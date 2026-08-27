package com.example.salesaggregation.domain;

import java.math.BigDecimal;
import java.util.List;

public record ValidatedSalesRow(SalesRow row, BigDecimal amount, List<RowError> errors) {
    public ValidatedSalesRow {
        errors = List.copyOf(errors);
    }

    public boolean valid() {
        return row != null && errors.isEmpty();
    }

    public static ValidatedSalesRow invalid(List<RowError> errors) {
        return new ValidatedSalesRow(null, null, errors);
    }
}
