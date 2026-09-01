package com.example.salesaggregation.infrastructure.google;

import com.example.salesaggregation.domain.RawSalesRow;

import java.util.List;

public record ResolvedColumnMapping(int dateIndex, int staffIndex, int productIndex,
                                    int quantityIndex, int unitPriceIndex) {
    public int lastIndex() {
        return Math.max(Math.max(dateIndex, staffIndex), Math.max(productIndex, Math.max(quantityIndex, unitPriceIndex)));
    }

    public RawSalesRow canonicalRow(int rowNumber, List<Object> cells) {
        return new RawSalesRow(rowNumber, List.of(value(cells, dateIndex), value(cells, staffIndex),
                value(cells, productIndex), value(cells, quantityIndex), value(cells, unitPriceIndex)));
    }

    private Object value(List<Object> cells, int index) {
        return index < cells.size() && cells.get(index) != null ? cells.get(index) : "";
    }
}
