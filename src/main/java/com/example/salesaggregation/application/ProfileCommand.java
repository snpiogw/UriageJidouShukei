package com.example.salesaggregation.application;

import com.example.salesaggregation.domain.ColumnMapping;
import com.example.salesaggregation.domain.TaxMode;

import java.math.BigDecimal;
import java.time.LocalTime;

public record ProfileCommand(
        String profileName,
        String spreadsheetId,
        String sourceSheetName,
        String resultSheetName,
        String errorSheetName,
        TaxMode taxMode,
        BigDecimal taxRate,
        boolean autoEnabled,
        LocalTime executionTime,
        String timeZone,
        ColumnMapping columnMapping) {
}
