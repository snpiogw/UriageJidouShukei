package com.example.salesaggregation.batch;

import com.example.salesaggregation.infrastructure.persistence.PostgresAdvisoryLockService;
import com.example.salesaggregation.infrastructure.persistence.AggregationExecutionRepository;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class AcquireExecutionLockTasklet implements Tasklet {
    private final PostgresAdvisoryLockService locks;
    private final AggregationExecutionRepository executions;

    public AcquireExecutionLockTasklet(PostgresAdvisoryLockService locks, AggregationExecutionRepository executions) {
        this.locks = locks;
        this.executions = executions;
    }

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
        UUID executionId = PrepareExecutionTasklet.executionId(chunkContext);
        long profileId = executions.findById(executionId).orElseThrow().getProfileId();
        if (!locks.tryLock(executionId, profileId)) {
            throw new ConcurrentExecutionException();
        }
        return RepeatStatus.FINISHED;
    }

    static final class ConcurrentExecutionException extends RuntimeException {}
}
