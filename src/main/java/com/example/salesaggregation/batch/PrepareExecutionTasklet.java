package com.example.salesaggregation.batch;

import com.example.salesaggregation.infrastructure.google.SalesSheetGateway;
import com.example.salesaggregation.infrastructure.persistence.*;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Component
public class PrepareExecutionTasklet implements Tasklet {
    private final AggregationExecutionRepository executions;
    private final AggregationWorkStore workStore;
    private final SalesSheetGateway sheets;

    public PrepareExecutionTasklet(AggregationExecutionRepository executions, AggregationWorkStore workStore,
                                   SalesSheetGateway sheets) {
        this.executions = executions;
        this.workStore = workStore;
        this.sheets = sheets;
    }

    @Override
    @Transactional
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) throws Exception {
        UUID id = executionId(chunkContext);
        AggregationExecutionEntity execution = executions.findById(id).orElseThrow();
        workStore.clear(id);
        sheets.validateHeader();
        execution.markRunning();
        executions.save(execution);
        return RepeatStatus.FINISHED;
    }

    static UUID executionId(ChunkContext context) {
        return UUID.fromString(context.getStepContext().getJobParameters().get("executionId").toString());
    }
}
