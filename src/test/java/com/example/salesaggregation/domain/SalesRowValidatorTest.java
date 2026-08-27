package com.example.salesaggregation.domain;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SalesRowValidatorTest {
    private final SalesRowValidator validator = new SalesRowValidator();

    @Test
    void validatesIsoDateAndTrimsNames() {
        RawSalesRow raw = new RawSalesRow(2, List.of("2026-08-26", " 田中 ", " 商品A ", 2.0, 1200.0));
        SalesRowValidator.ValidationOutcome outcome = validator.validate(raw);
        assertThat(outcome.errors()).isEmpty();
        assertThat(outcome.row().salesDate()).isEqualTo(LocalDate.of(2026, 8, 26));
        assertThat(outcome.row().staffName()).isEqualTo("田中");
        assertThat(outcome.row().quantity()).isEqualTo(2);
    }

    @Test
    void validatesGoogleSerialDate() {
        RawSalesRow raw = new RawSalesRow(2, List.of(46260.0, "田中", "商品A", 1.0, 100.0));
        assertThat(validator.validate(raw).errors()).isEmpty();
    }

    @Test
    void reportsAllInvalidFieldsWithoutExposingValues() {
        RawSalesRow raw = new RawSalesRow(9, List.of("08/26/2026", "", "商品A", -1.0, "1,200"));
        SalesRowValidator.ValidationOutcome outcome = validator.validate(raw);
        assertThat(outcome.row()).isNull();
        assertThat(outcome.errors()).extracting(RowError::field)
                .containsExactly("日付", "担当者", "数量", "単価");
        assertThat(outcome.errors()).allMatch(error -> error.rowNumber() == 9);
    }

    @Test
    void rejectsDateSerialWithTimeComponent() {
        RawSalesRow raw = new RawSalesRow(2, List.of(46260.5, "田中", "商品A", 1.0, 100.0));
        assertThat(validator.validate(raw).errors()).extracting(RowError::code).contains("INVALID_DATE");
    }
}
