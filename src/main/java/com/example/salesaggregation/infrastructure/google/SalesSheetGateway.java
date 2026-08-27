package com.example.salesaggregation.infrastructure.google;

import com.example.salesaggregation.domain.AggregationResult;
import com.example.salesaggregation.domain.ExecutionProfileSnapshot;
import com.example.salesaggregation.domain.RawSalesRow;
import com.example.salesaggregation.domain.RowError;

import java.io.IOException;
import java.util.List;

public interface SalesSheetGateway {
    ResolvedColumnMapping validateHeader(ExecutionProfileSnapshot profile) throws IOException;
    int sourceRowCount(ExecutionProfileSnapshot profile) throws IOException;
    List<RawSalesRow> readRows(ExecutionProfileSnapshot profile, ResolvedColumnMapping mapping,
                               int startRow, int pageSize) throws IOException;
    void writeResult(ExecutionProfileSnapshot profile, AggregationResult result) throws IOException;
    void writeErrorsOnly(ExecutionProfileSnapshot profile, java.util.UUID executionId,
                         List<RowError> errors) throws IOException;
}
