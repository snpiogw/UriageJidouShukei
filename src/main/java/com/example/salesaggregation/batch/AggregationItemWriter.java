package com.example.salesaggregation.batch;

import com.example.salesaggregation.domain.ValidatedSalesRow;
import com.example.salesaggregation.infrastructure.persistence.AggregationWorkStore;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;

import java.util.UUID;

public class AggregationItemWriter implements ItemWriter<ValidatedSalesRow> {
    private final UUID executionId;
    private final AggregationWorkStore workStore;

    public AggregationItemWriter(UUID executionId, AggregationWorkStore workStore) {
        this.executionId = executionId;
        this.workStore = workStore;
    }

    @Override
    public void write(Chunk<? extends ValidatedSalesRow> chunk) {
        workStore.writeChunk(executionId, chunk.getItems());
    }
}
