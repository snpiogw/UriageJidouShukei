package com.example.salesaggregation.domain;

import java.math.BigDecimal;
import java.time.LocalDate;

public record SalesRow(
        int rowNumber,
        LocalDate salesDate,
        String staffName,
        String productName,
        long quantity,
        BigDecimal unitPrice) {}
