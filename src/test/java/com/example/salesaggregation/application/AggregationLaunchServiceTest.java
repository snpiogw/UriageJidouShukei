package com.example.salesaggregation.application;

import com.example.salesaggregation.domain.ExecutionStatus;
import com.example.salesaggregation.domain.ColumnMapping;
import com.example.salesaggregation.domain.ExecutionProfileSnapshot;
import com.example.salesaggregation.domain.TaxMode;
import com.example.salesaggregation.domain.TriggerType;
import com.example.salesaggregation.infrastructure.persistence.AggregationExecutionEntity;
import com.example.salesaggregation.infrastructure.persistence.AggregationExecutionRepository;
import com.example.salesaggregation.infrastructure.persistence.AggregationProfileEntity;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.launch.JobLauncher;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AggregationLaunchServiceTest {

    @Test
    void launchesTheSelectedProfileWithOnlyExecutionIdAsIdentifyingParameter() throws Exception {
        AggregationProfileService profiles = mock(AggregationProfileService.class);
        AggregationProfileEntity profile = mock(AggregationProfileEntity.class);
        AggregationExecutionRepository executions = mock(AggregationExecutionRepository.class);
        JobLauncher launcher = mock(JobLauncher.class);
        Job job = mock(Job.class);
        ExecutionProfileSnapshot snapshot = new ExecutionProfileSnapshot(22, "店舗B", "sheet-b", "入力",
                "結果", "エラー", TaxMode.INCLUSIVE, BigDecimal.TEN, "Asia/Tokyo", 3, ColumnMapping.DEFAULT);
        when(profiles.get(22)).thenReturn(profile);
        when(profile.getSpreadsheetId()).thenReturn("sheet-b");
        when(profile.snapshot()).thenReturn(snapshot);
        when(launcher.run(eq(job), any(JobParameters.class))).thenAnswer(invocation ->
                new JobExecution(1L, invocation.getArgument(1)));

        UUID id = new AggregationLaunchService(profiles, executions, launcher, job)
                .launch(22, TriggerType.MANUAL);

        ArgumentCaptor<JobParameters> parameters = ArgumentCaptor.forClass(JobParameters.class);
        verify(launcher).run(eq(job), parameters.capture());
        assertThat(parameters.getValue().getString("executionId")).isEqualTo(id.toString());
        assertThat(parameters.getValue().getParameter("executionId").isIdentifying()).isTrue();
        assertThat(parameters.getValue().getLong("profileId")).isEqualTo(22L);
        assertThat(parameters.getValue().getParameter("profileId").isIdentifying()).isFalse();
        assertThat(parameters.getValue().getParameter("requestedAt").isIdentifying()).isFalse();
        verify(executions).saveAndFlush(argThat(execution -> execution.getProfileId() == 22));
    }

    @Test
    void restartsFailedExecutionWithTheSameIdentifyingExecutionId() throws Exception {
        UUID id = UUID.randomUUID();
        AggregationExecutionEntity execution = new AggregationExecutionEntity(
                id, TriggerType.MANUAL, TaxMode.INCLUSIVE, new BigDecimal("10"), 0);
        execution.fail(ExecutionStatus.FAILED, "BATCH_FAILED", "failed");

        AggregationProfileService profiles = mock(AggregationProfileService.class);
        AggregationExecutionRepository executions = mock(AggregationExecutionRepository.class);
        JobLauncher launcher = mock(JobLauncher.class);
        Job job = mock(Job.class);
        when(executions.findById(id)).thenReturn(Optional.of(execution));
        when(launcher.run(eq(job), any(JobParameters.class))).thenAnswer(invocation ->
                new JobExecution(2L, invocation.getArgument(1)));

        AggregationLaunchService service = new AggregationLaunchService(profiles, executions, launcher, job);
        service.restart(id);

        ArgumentCaptor<JobParameters> parameters = ArgumentCaptor.forClass(JobParameters.class);
        verify(launcher).run(eq(job), parameters.capture());
        assertThat(parameters.getValue().getString("executionId")).isEqualTo(id.toString());
        assertThat(parameters.getValue().getParameter("executionId").isIdentifying()).isTrue();
        assertThat(parameters.getValue().getParameter("requestedAt").isIdentifying()).isFalse();
        assertThat(parameters.getValue().getLong("profileId")).isEqualTo(1L);
        assertThat(parameters.getValue().getParameter("profileId").isIdentifying()).isFalse();
        assertThat(execution.getStatus()).isEqualTo(ExecutionStatus.QUEUED);
        assertThat(execution.getCompletedAt()).isNull();
        assertThat(execution.getErrorCode()).isNull();
        verify(executions).saveAndFlush(execution);
    }
}
