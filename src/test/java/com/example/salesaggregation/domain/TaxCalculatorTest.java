package com.example.salesaggregation.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class TaxCalculatorTest {
    private final TaxCalculator calculator = new TaxCalculator();

    @Test
    void calculatesExclusiveAmount() {
        SalesRow row = row(3, "1200");
        assertThat(calculator.calculate(row, TaxMode.EXCLUSIVE, new BigDecimal("10")))
                .isEqualByComparingTo("3600");
    }

    @Test
    void calculatesInclusiveAmountAndRoundsDownPerRow() {
        SalesRow row = row(1, "101");
        assertThat(calculator.calculate(row, TaxMode.INCLUSIVE, new BigDecimal("8")))
                .isEqualByComparingTo("109");
    }

    @Test
    void acceptsZeroAmountAsValidCalculation() {
        SalesRow row = row(0, "999");
        assertThat(calculator.calculate(row, TaxMode.INCLUSIVE, new BigDecimal("10")))
                .isEqualByComparingTo("0");
    }

    private SalesRow row(long quantity, String price) {
        return new SalesRow(2, LocalDate.of(2026, 8, 26), "田中", "商品A", quantity, new BigDecimal(price));
    }
}
