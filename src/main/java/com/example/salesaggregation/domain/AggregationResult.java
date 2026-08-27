package com.example.salesaggregation.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record AggregationResult(
        UUID executionId,
        Instant completedAt,
        TaxMode taxMode,
        BigDecimal taxRate,
        long settingsVersion,
        long sourceCount,
        long validCount,
        long invalidCount,
        Map<String, BigDecimal> productTotals,
        Map<String, BigDecimal> staffTotals,
        Map<String, BigDecimal> monthlyTotals,
        BigDecimal grandTotal,
        List<RowError> errors) {}
