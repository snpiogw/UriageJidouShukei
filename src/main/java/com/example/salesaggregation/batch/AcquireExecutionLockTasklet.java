package com.example.salesaggregation.batch;

import com.example.salesaggregation.infrastructure.persistence.PostgresAdvisoryLockService;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class AcquireExecutionLockTasklet implements Tasklet {
    private final PostgresAdvisoryLockService locks;

    public AcquireExecutionLockTasklet(PostgresAdvisoryLockService locks) {
        this.locks = locks;
    }

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
        UUID executionId = PrepareExecutionTasklet.executionId(chunkContext);
        if (!locks.tryLock(executionId)) {
            throw new ConcurrentExecutionException();
        }
        return RepeatStatus.FINISHED;
    }

    static final class ConcurrentExecutionException extends RuntimeException {}
}
