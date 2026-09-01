package com.example.salesaggregation.domain;

import java.math.BigDecimal;

public record ExecutionProfileSnapshot(
        long profileId,
        String profileName,
        String spreadsheetId,
        String sourceSheetName,
        String resultSheetName,
        String errorSheetName,
        TaxMode taxMode,
        BigDecimal taxRate,
        String timeZone,
        long version,
        ColumnMapping columnMapping) {
}
