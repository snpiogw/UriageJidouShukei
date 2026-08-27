package com.example.salesaggregation.infrastructure.google;

import com.example.salesaggregation.domain.AggregationResult;
import com.example.salesaggregation.domain.RawSalesRow;
import com.example.salesaggregation.domain.RowError;

import java.io.IOException;
import java.util.List;

public interface SalesSheetGateway {
    void validateHeader() throws IOException;
    int sourceRowCount() throws IOException;
    List<RawSalesRow> readRows(int startRow, int pageSize) throws IOException;
    void writeResult(AggregationResult result) throws IOException;
    void writeErrorsOnly(java.util.UUID executionId, List<RowError> errors) throws IOException;
}
