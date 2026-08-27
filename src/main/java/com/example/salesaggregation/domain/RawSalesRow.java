package com.example.salesaggregation.domain;

import java.util.List;

public record RawSalesRow(int rowNumber, List<Object> cells) {
    public RawSalesRow {
        cells = List.copyOf(cells);
    }

    public Object cell(int index) {
        return index < cells.size() ? cells.get(index) : null;
    }
}
