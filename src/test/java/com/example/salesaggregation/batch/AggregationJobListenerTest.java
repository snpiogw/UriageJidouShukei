package com.example.salesaggregation.batch;

import com.example.salesaggregation.domain.ExecutionStatus;
import com.example.salesaggregation.domain.TaxMode;
import com.example.salesaggregation.domain.TriggerType;
import com.example.salesaggregation.infrastructure.persistence.AggregationExecutionEntity;
import com.example.salesaggregation.infrastructure.persistence.AggregationExecutionRepository;
import com.example.salesaggregation.infrastructure.persistence.PostgresAdvisoryLockService;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class AggregationJobListenerTest {

    @Test
    void recordsConcurrentExecutionAsSkippedAfterTheFailedStepTransactionEnds() {
        UUID id = UUID.randomUUID();
        AggregationExecutionEntity execution = new AggregationExecutionEntity(
                id, TriggerType.MANUAL, TaxMode.INCLUSIVE, new BigDecimal("10"), 0);
        AggregationExecutionRepository executions = mock(AggregationExecutionRepository.class);
        PostgresAdvisoryLockService locks = mock(PostgresAdvisoryLockService.class);
        when(executions.findById(id)).thenReturn(Optional.of(execution));

        JobParameters parameters = new JobParametersBuilder()
                .addString("executionId", id.toString(), true)
                .toJobParameters();
        JobExecution jobExecution = new JobExecution(1L, parameters);
        jobExecution.setStatus(BatchStatus.FAILED);
        jobExecution.addFailureException(new IllegalStateException("step failed",
                new AcquireExecutionLockTasklet.ConcurrentExecutionException()));

        new AggregationJobListener(executions, locks).afterJob(jobExecution);

        assertThat(execution.getStatus()).isEqualTo(ExecutionStatus.SKIPPED_CONCURRENT);
        assertThat(execution.getErrorCode()).isEqualTo("CONCURRENT_EXECUTION");
        assertThat(execution.getSummary()).isEqualTo("別の集計が実行中のため、この実行は開始されませんでした");
        verify(executions).save(execution);
        verify(locks).unlock(id);
    }

    @Test
    void recordsAnUnderstandableReasonForInvalidSheetHeaders() {
        UUID id = UUID.randomUUID();
        AggregationExecutionEntity execution = new AggregationExecutionEntity(
                id, TriggerType.SCHEDULED, TaxMode.INCLUSIVE, new BigDecimal("10"), 0);
        AggregationExecutionRepository executions = mock(AggregationExecutionRepository.class);
        PostgresAdvisoryLockService locks = mock(PostgresAdvisoryLockService.class);
        when(executions.findById(id)).thenReturn(Optional.of(execution));

        JobExecution jobExecution = new JobExecution(2L, new JobParametersBuilder()
                .addString("executionId", id.toString(), true).toJobParameters());
        jobExecution.setStatus(BatchStatus.FAILED);
        jobExecution.addFailureException(new IllegalStateException("step failed",
                new java.io.IOException("見出しは「日付、担当者、商品名、数量、単価」にしてください")));

        new AggregationJobListener(executions, locks).afterJob(jobExecution);

        assertThat(execution.getStatus()).isEqualTo(ExecutionStatus.FAILED);
        assertThat(execution.getErrorCode()).isEqualTo("INVALID_SHEET_HEADER");
        assertThat(execution.getSummary()).isEqualTo("売上データシートの見出しが想定と一致しません");
    }
}
