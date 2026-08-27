package com.example.salesaggregation.domain;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Component
public class TaxCalculator {
    public BigDecimal calculate(SalesRow row, TaxMode mode, BigDecimal taxRatePercent) {
        BigDecimal base = row.unitPrice().multiply(BigDecimal.valueOf(row.quantity()));
        if (mode == TaxMode.EXCLUSIVE) {
            return base.setScale(0, RoundingMode.UNNECESSARY);
        }
        BigDecimal multiplier = BigDecimal.ONE.add(taxRatePercent.movePointLeft(2));
        return base.multiply(multiplier).setScale(0, RoundingMode.DOWN);
    }
}
