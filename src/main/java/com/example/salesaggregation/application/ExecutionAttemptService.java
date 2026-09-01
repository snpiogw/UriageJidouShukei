package com.example.salesaggregation.application;

import com.example.salesaggregation.domain.ExecutionStatus;
import com.example.salesaggregation.infrastructure.persistence.AggregationExecutionAttemptEntity;
import com.example.salesaggregation.infrastructure.persistence.AggregationExecutionAttemptRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class ExecutionAttemptService {
    private final AggregationExecutionAttemptRepository attempts;

    public ExecutionAttemptService(AggregationExecutionAttemptRepository attempts) {
        this.attempts = attempts;
    }

    @Transactional
    public AggregationExecutionAttemptEntity queueInitial(UUID executionId) {
        return attempts.save(new AggregationExecutionAttemptEntity(executionId, 1, "集計処理を受け付けました"));
    }

    @Transactional
    public AggregationExecutionAttemptEntity queueRestart(UUID executionId) {
        int next = attempts.findFirstByExecutionIdOrderByAttemptNumberDesc(executionId)
                .map(item -> item.getAttemptNumber() + 1).orElse(1);
        return attempts.save(new AggregationExecutionAttemptEntity(executionId, next,
                "失敗地点からの再開を受け付けました"));
    }

    @Transactional
    public void markRunning(UUID executionId) {
        AggregationExecutionAttemptEntity attempt = latest(executionId);
        attempt.markRunning();
        attempts.save(attempt);
    }

    @Transactional
    public void complete(UUID executionId, ExecutionStatus status, String summary) {
        AggregationExecutionAttemptEntity attempt = latest(executionId);
        attempt.complete(status, summary);
        attempts.save(attempt);
    }

    @Transactional
    public void fail(UUID executionId, ExecutionStatus status, String code, String summary) {
        AggregationExecutionAttemptEntity attempt = latest(executionId);
        attempt.fail(status, code, summary);
        attempts.save(attempt);
    }

    @Transactional(readOnly = true)
    public List<AggregationExecutionAttemptEntity> list(UUID executionId) {
        return attempts.findByExecutionIdOrderByAttemptNumberDesc(executionId);
    }

    private AggregationExecutionAttemptEntity latest(UUID executionId) {
        return attempts.findFirstByExecutionIdOrderByAttemptNumberDesc(executionId)
                .orElseThrow(() -> new IllegalStateException("実行試行履歴が見つかりません"));
    }
}
