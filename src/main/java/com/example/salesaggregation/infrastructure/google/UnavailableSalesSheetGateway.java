package com.example.salesaggregation.infrastructure.google;

import com.example.salesaggregation.domain.AggregationResult;
import com.example.salesaggregation.domain.RawSalesRow;
import com.example.salesaggregation.domain.RowError;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

@Component
@ConditionalOnExpression("'${app.sheets.spreadsheet-id:}' == ''")
public class UnavailableSalesSheetGateway implements SalesSheetGateway {
    private IOException unavailable() {
        return new IOException("GOOGLE_SPREADSHEET_IDとGoogleサービスアカウント認証を設定してください");
    }

    @Override public void validateHeader() throws IOException { throw unavailable(); }
    @Override public int sourceRowCount() throws IOException { throw unavailable(); }
    @Override public List<RawSalesRow> readRows(int startRow, int pageSize) throws IOException { throw unavailable(); }
    @Override public void writeResult(AggregationResult result) throws IOException { throw unavailable(); }
    @Override public void writeErrorsOnly(UUID executionId, List<RowError> errors) throws IOException { throw unavailable(); }
}
