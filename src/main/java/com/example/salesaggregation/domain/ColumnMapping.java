package com.example.salesaggregation.domain;

import java.util.List;

public record ColumnMapping(
        String dateColumn,
        String staffColumn,
        String productColumn,
        String quantityColumn,
        String unitPriceColumn) {

    public static final ColumnMapping DEFAULT = new ColumnMapping("日付", "担当者", "商品名", "数量", "単価");

    public ColumnMapping {
        dateColumn = normalized(dateColumn);
        staffColumn = normalized(staffColumn);
        productColumn = normalized(productColumn);
        quantityColumn = normalized(quantityColumn);
        unitPriceColumn = normalized(unitPriceColumn);
    }

    public List<String> headers() {
        return List.of(dateColumn, staffColumn, productColumn, quantityColumn, unitPriceColumn);
    }

    private static String normalized(String value) {
        return value == null ? "" : value.trim();
    }
}
