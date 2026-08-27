package com.example.salesaggregation.domain;

public record RowError(int rowNumber, String field, String code, String guidance) {}
