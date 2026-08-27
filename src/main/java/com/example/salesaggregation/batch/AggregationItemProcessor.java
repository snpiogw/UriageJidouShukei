package com.example.salesaggregation.batch;

import com.example.salesaggregation.domain.*;
import org.springframework.batch.item.ItemProcessor;

import java.math.BigDecimal;

public class AggregationItemProcessor implements ItemProcessor<RawSalesRow, ValidatedSalesRow> {
    private final SalesRowValidator validator;
    private final TaxCalculator calculator;
    private final TaxMode taxMode;
    private final BigDecimal taxRate;

    public AggregationItemProcessor(SalesRowValidator validator, TaxCalculator calculator,
                                    TaxMode taxMode, BigDecimal taxRate) {
        this.validator = validator;
        this.calculator = calculator;
        this.taxMode = taxMode;
        this.taxRate = taxRate;
    }

    @Override
    public ValidatedSalesRow process(RawSalesRow item) {
        SalesRowValidator.ValidationOutcome outcome = validator.validate(item);
        if (!outcome.errors().isEmpty()) return ValidatedSalesRow.invalid(outcome.errors());
        return new ValidatedSalesRow(outcome.row(), calculator.calculate(outcome.row(), taxMode, taxRate), java.util.List.of());
    }
}
