package com.example.salesaggregation.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app")
public record AppProperties(@Valid Sheets sheets, @Valid Security security, @Valid Batch batch) {
    public record Sheets(
            String spreadsheetId,
            @NotBlank String sourceSheet,
            @NotBlank String resultSheet,
            @NotBlank String errorSheet,
            @Min(1) @Max(1_000_000) int maxRows,
            @Min(1) @Max(10_000) int fetchSize) {}

    public record Security(@NotBlank String adminUsername, @NotBlank String adminPasswordHash) {}

    public record Batch(
            @Min(1) @Max(10_000) int chunkSize,
            @Min(0) @Max(10) int transientRetryCount) {}
}
