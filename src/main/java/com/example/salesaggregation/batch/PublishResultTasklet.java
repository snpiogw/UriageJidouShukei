package com.example.salesaggregation.batch;

import com.example.salesaggregation.domain.*;
import com.example.salesaggregation.infrastructure.google.SalesSheetGateway;
import com.example.salesaggregation.infrastructure.persistence.*;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
public class PublishResultTasklet implements Tasklet {
    private final AggregationExecutionRepository executions;
    private final AggregationWorkStore workStore;
    private final SalesSheetGateway sheets;

    public PublishResultTasklet(AggregationExecutionRepository executions, AggregationWorkStore workStore,
                                SalesSheetGateway sheets) {
        this.executions = executions;
        this.workStore = workStore;
        this.sheets = sheets;
    }

    @Override
    @Transactional
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) throws Exception {
        UUID id = PrepareExecutionTasklet.executionId(chunkContext);
        AggregationExecutionEntity execution = executions.findById(id).orElseThrow();
        List<RowError> errors = workStore.errors(id);
        if (execution.getValidCount() == 0) {
            sheets.writeErrorsOnly(execution.profileSnapshot(), id, errors);
            execution.complete(ExecutionStatus.NO_VALID_DATA, execution.getSourceCount(), 0,
                    execution.getInvalidCount(), "有効な売上データがないため集計結果を更新していません");
            executions.save(execution);
            return RepeatStatus.FINISHED;
        }

        Map<String, BigDecimal> products = workStore.productTotals(id);
        Map<String, BigDecimal> staff = workStore.staffTotals(id);
        Map<String, BigDecimal> months = workStore.monthlyTotals(id);
        BigDecimal grandTotal = products.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        AggregationResult result = new AggregationResult(id, Instant.now(), execution.getTaxMode(),
                execution.getTaxRate(), execution.getSettingsVersion(), execution.getSourceCount(),
                execution.getValidCount(), execution.getInvalidCount(), products, staff, months, grandTotal, errors);
        sheets.writeResult(execution.profileSnapshot(), result);
        ExecutionStatus status = execution.getInvalidCount() == 0
                ? ExecutionStatus.SUCCESS : ExecutionStatus.SUCCESS_WITH_WARNINGS;
        execution.complete(status, execution.getSourceCount(), execution.getValidCount(),
                execution.getInvalidCount(), status == ExecutionStatus.SUCCESS ? "正常に更新しました" : "一部の行を除外して更新しました");
        executions.save(execution);
        return RepeatStatus.FINISHED;
    }
}
