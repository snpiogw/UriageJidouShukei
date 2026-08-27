package com.example.salesaggregation.application;

import com.example.salesaggregation.domain.ExecutionStatus;
import com.example.salesaggregation.domain.TriggerType;
import com.example.salesaggregation.infrastructure.persistence.*;
import org.springframework.batch.core.*;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

@Service
public class AggregationLaunchService {
    private final SettingsService settingsService;
    private final AggregationExecutionRepository executions;
    private final JobLauncher launcher;
    private final Job job;

    public AggregationLaunchService(SettingsService settingsService,
                                    AggregationExecutionRepository executions,
                                    @Qualifier("asyncJobLauncher") JobLauncher launcher,
                                    Job salesAggregationJob) {
        this.settingsService = settingsService;
        this.executions = executions;
        this.launcher = launcher;
        this.job = salesAggregationJob;
    }

    public UUID launch(TriggerType triggerType) {
        AggregationSettingsEntity settings = settingsService.current();
        UUID id = UUID.randomUUID();
        executions.saveAndFlush(new AggregationExecutionEntity(id, triggerType, settings.getTaxMode(),
                settings.getTaxRate(), settings.getVersion()));
        try {
            JobParameters params = new JobParametersBuilder()
                    .addString("executionId", id.toString(), true)
                    .addString("requestedAt", Instant.now().toString(), false)
                    .toJobParameters();
            launcher.run(job, params);
            return id;
        } catch (Exception ex) {
            AggregationExecutionEntity execution = executions.findById(id).orElseThrow();
            execution.fail(ExecutionStatus.FAILED, "LAUNCH_FAILED", "バッチ処理を開始できませんでした");
            executions.save(execution);
            throw new IllegalStateException("バッチ処理を開始できませんでした", ex);
        }
    }

    public UUID restart(UUID id) {
        AggregationExecutionEntity execution = executions.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("実行履歴が見つかりません"));
        if (execution.getStatus() != ExecutionStatus.FAILED) {
            throw new IllegalStateException("失敗した集計だけを再開できます");
        }

        execution.queueForRestart();
        executions.saveAndFlush(execution);
        try {
            JobParameters params = new JobParametersBuilder()
                    .addString("executionId", id.toString(), true)
                    .addString("requestedAt", Instant.now().toString(), false)
                    .toJobParameters();
            launcher.run(job, params);
            return id;
        } catch (Exception ex) {
            AggregationExecutionEntity failed = executions.findById(id).orElseThrow();
            failed.fail(ExecutionStatus.FAILED, "RESTART_FAILED", "バッチ処理を再開できませんでした");
            executions.save(failed);
            throw new IllegalStateException("バッチ処理を再開できませんでした", ex);
        }
    }
}
