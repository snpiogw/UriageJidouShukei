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
    private final AggregationProfileService profileService;
    private final AggregationExecutionRepository executions;
    private final ExecutionAttemptService attempts;
    private final JobLauncher launcher;
    private final Job job;

    public AggregationLaunchService(AggregationProfileService profileService,
                                    AggregationExecutionRepository executions,
                                    ExecutionAttemptService attempts,
                                    @Qualifier("asyncJobLauncher") JobLauncher launcher,
                                    Job salesAggregationJob) {
        this.profileService = profileService;
        this.executions = executions;
        this.attempts = attempts;
        this.launcher = launcher;
        this.job = salesAggregationJob;
    }

    public UUID launch(long profileId, TriggerType triggerType) {
        AggregationProfileEntity profile = profileService.get(profileId);
        if (!profile.isActive()) {
            throw new IllegalStateException("無効化された集計設定は実行できません");
        }
        if (profile.getSpreadsheetId().isBlank()) {
            throw new IllegalStateException("Spreadsheet IDが未設定です。集計設定を編集してください");
        }
        UUID id = UUID.randomUUID();
        executions.saveAndFlush(new AggregationExecutionEntity(id, triggerType, profile.snapshot()));
        attempts.queueInitial(id);
        try {
            JobParameters params = new JobParametersBuilder()
                    .addString("executionId", id.toString(), true)
                    .addLong("profileId", profileId, false)
                    .addString("requestedAt", Instant.now().toString(), false)
                    .toJobParameters();
            launcher.run(job, params);
            return id;
        } catch (Exception ex) {
            AggregationExecutionEntity execution = executions.findById(id).orElseThrow();
            execution.fail(ExecutionStatus.FAILED, "LAUNCH_FAILED", "バッチ処理を開始できませんでした");
            executions.save(execution);
            attempts.fail(id, ExecutionStatus.FAILED, "LAUNCH_FAILED", "バッチ処理を開始できませんでした");
            throw new IllegalStateException("バッチ処理を開始できませんでした", ex);
        }
    }

    public UUID restart(UUID id) {
        AggregationExecutionEntity execution = executions.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("実行履歴が見つかりません"));
        if (execution.getStatus() != ExecutionStatus.FAILED) {
            throw new IllegalStateException("失敗した集計だけを再開できます");
        }
        AggregationProfileEntity profile = profileService.get(execution.getProfileId());
        if (!profile.isActive()) {
            throw new IllegalStateException("無効化された集計設定は再開できません");
        }

        execution.queueForRestart();
        executions.saveAndFlush(execution);
        attempts.queueRestart(id);
        try {
            JobParameters params = new JobParametersBuilder()
                    .addString("executionId", id.toString(), true)
                    .addLong("profileId", execution.getProfileId(), false)
                    .addString("requestedAt", Instant.now().toString(), false)
                    .toJobParameters();
            launcher.run(job, params);
            return id;
        } catch (Exception ex) {
            AggregationExecutionEntity failed = executions.findById(id).orElseThrow();
            failed.fail(ExecutionStatus.FAILED, "RESTART_FAILED", "バッチ処理を再開できませんでした");
            executions.save(failed);
            attempts.fail(id, ExecutionStatus.FAILED, "RESTART_FAILED", "バッチ処理を再開できませんでした");
            throw new IllegalStateException("バッチ処理を再開できませんでした", ex);
        }
    }
}
