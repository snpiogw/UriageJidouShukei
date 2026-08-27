package com.example.salesaggregation.application;

import com.example.salesaggregation.batch.AggregationItemProcessor;
import com.example.salesaggregation.domain.*;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AggregationItemProcessorTest {
    private final AggregationItemProcessor processor = new AggregationItemProcessor(
            new SalesRowValidator(), new TaxCalculator(), TaxMode.INCLUSIVE, new BigDecimal("10"));

    @Test
    void returnsCalculatedValidRow() throws Exception {
        ValidatedSalesRow result = processor.process(
                new RawSalesRow(2, List.of("2026-08-26", "田中", "商品A", 2.0, 101.0)));
        assertThat(result.valid()).isTrue();
        assertThat(result.amount()).isEqualByComparingTo("222");
    }

    @Test
    void keepsValidationErrorsForInvalidRow() throws Exception {
        ValidatedSalesRow result = processor.process(
                new RawSalesRow(5, List.of("bad", "田中", "商品A", 2.0, 101.0)));
        assertThat(result.valid()).isFalse();
        assertThat(result.errors()).extracting(RowError::field).containsExactly("日付");
    }
}
