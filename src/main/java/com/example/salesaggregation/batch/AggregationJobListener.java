package com.example.salesaggregation.batch;

import com.example.salesaggregation.domain.ExecutionStatus;
import com.example.salesaggregation.infrastructure.persistence.AggregationExecutionEntity;
import com.example.salesaggregation.infrastructure.persistence.AggregationExecutionRepository;
import com.example.salesaggregation.infrastructure.persistence.PostgresAdvisoryLockService;
import com.example.salesaggregation.application.ExecutionAttemptService;
import org.springframework.batch.core.*;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.UUID;

@Component
public class AggregationJobListener implements JobExecutionListener {
    private final AggregationExecutionRepository executions;
    private final PostgresAdvisoryLockService locks;
    private final ExecutionAttemptService attempts;

    public AggregationJobListener(AggregationExecutionRepository executions, PostgresAdvisoryLockService locks,
                                  ExecutionAttemptService attempts) {
        this.executions = executions;
        this.locks = locks;
        this.attempts = attempts;
    }

    @Override
    public void beforeJob(JobExecution jobExecution) {
        String rawId = jobExecution.getJobParameters().getString("executionId");
        if (rawId != null) attempts.markRunning(UUID.fromString(rawId));
    }

    @Override
    public void afterJob(JobExecution jobExecution) {
        String rawId = jobExecution.getJobParameters().getString("executionId");
        if (rawId == null) return;
        UUID id = UUID.fromString(rawId);
        try {
            AggregationExecutionEntity execution = executions.findById(id).orElse(null);
            if (execution != null && jobExecution.getStatus() == BatchStatus.FAILED
                    && !execution.getStatus().isTerminal()) {
                FailureDetails failure = describeFailure(jobExecution);
                execution.fail(failure.status(), failure.code(), failure.summary());
                executions.save(execution);
                attempts.fail(id, failure.status(), failure.code(), failure.summary());
            }
        } finally {
            locks.unlock(id);
        }
    }

    private FailureDetails describeFailure(JobExecution execution) {
        for (Throwable failure : execution.getAllFailureExceptions()) {
            if (hasCause(failure, AcquireExecutionLockTasklet.ConcurrentExecutionException.class)) {
                return new FailureDetails(ExecutionStatus.SKIPPED_CONCURRENT, "CONCURRENT_EXECUTION",
                        "別の集計が実行中のため、この実行は開始されませんでした");
            }
            if (containsMessage(failure, "GOOGLE_SPREADSHEET_ID") || containsMessage(failure, "Spreadsheet ID")) {
                return failed("SPREADSHEET_NOT_CONFIGURED", "GoogleスプレッドシートIDが設定されていません");
            }
            if (containsMessage(failure, "見出し")) {
                return failed("INVALID_SHEET_HEADER", "売上データシートの見出しが想定と一致しません");
            }
            if (containsMessage(failure, "最大行数")) {
                return failed("ROW_LIMIT_EXCEEDED", "売上データが設定された最大行数を超えています");
            }
            if (hasCause(failure, IOException.class)
                    || hasCauseNamed(failure, "GoogleJsonResponseException")) {
                return failed("GOOGLE_SHEETS_UNAVAILABLE", "Google Sheetsからの読み込みまたは書き込みに失敗しました");
            }
            if (hasCause(failure, DataAccessException.class)) {
                return failed("DATABASE_ERROR", "データベースへの読み込みまたは書き込みに失敗しました");
            }
        }
        return failed("BATCH_FAILED", "集計処理中に予期しないエラーが発生しました");
    }

    private FailureDetails failed(String code, String summary) {
        return new FailureDetails(ExecutionStatus.FAILED, code, summary);
    }

    private boolean containsMessage(Throwable failure, String text) {
        Throwable current = failure;
        while (current != null) {
            if (current.getMessage() != null && current.getMessage().contains(text)) return true;
            current = current.getCause();
        }
        return false;
    }

    private boolean hasCause(Throwable failure, Class<? extends Throwable> type) {
        Throwable current = failure;
        while (current != null) {
            if (type.isInstance(current)) return true;
            current = current.getCause();
        }
        return false;
    }

    private boolean hasCauseNamed(Throwable failure, String simpleName) {
        Throwable current = failure;
        while (current != null) {
            if (current.getClass().getSimpleName().equals(simpleName)) return true;
            current = current.getCause();
        }
        return false;
    }

    private record FailureDetails(ExecutionStatus status, String code, String summary) {}
}
