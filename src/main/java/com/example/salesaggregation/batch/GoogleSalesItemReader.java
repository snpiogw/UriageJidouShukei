package com.example.salesaggregation.batch;

import com.example.salesaggregation.config.AppProperties;
import com.example.salesaggregation.domain.RawSalesRow;
import com.example.salesaggregation.infrastructure.google.SalesSheetGateway;
import org.springframework.batch.item.ExecutionContext;
import org.springframework.batch.item.ItemStreamException;
import org.springframework.batch.item.support.AbstractItemStreamItemReader;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

public class GoogleSalesItemReader extends AbstractItemStreamItemReader<RawSalesRow> {
    private static final String CHECKPOINT_ROW_KEY = "googleSheets.checkpointRow";
    private final SalesSheetGateway gateway;
    private final int fetchSize;
    private final int maxRows;
    private final Deque<RawSalesRow> buffer = new ArrayDeque<>();
    private int nextPageStart = 2;
    private int checkpointRow = 2;
    private int sourceRowCount = -1;
    private boolean finished;

    public GoogleSalesItemReader(SalesSheetGateway gateway, AppProperties properties) {
        this.gateway = gateway;
        this.fetchSize = properties.sheets().fetchSize();
        this.maxRows = properties.sheets().maxRows();
        setName("googleSalesItemReader");
    }

    @Override
    public RawSalesRow read() throws Exception {
        while (buffer.isEmpty() && !finished) loadNextPage();
        RawSalesRow item = buffer.pollFirst();
        if (item != null) checkpointRow = item.rowNumber() + 1;
        return item;
    }

    private void loadNextPage() throws IOException {
        if (sourceRowCount < 0) sourceRowCount = gateway.sourceRowCount();
        if (nextPageStart > sourceRowCount) {
            finished = true;
            return;
        }
        if (nextPageStart > maxRows + 1) {
            int size = Math.min(fetchSize, sourceRowCount - nextPageStart + 1);
            List<RawSalesRow> overflow = gateway.readRows(nextPageStart, size);
            if (!overflow.isEmpty()) {
                throw new IllegalStateException("入力データが最大行数" + maxRows + "件を超えています");
            }
            nextPageStart += size;
            return;
        }
        int size = Math.min(fetchSize, Math.min(maxRows + 2 - nextPageStart,
                sourceRowCount - nextPageStart + 1));
        List<RawSalesRow> rows = gateway.readRows(nextPageStart, size);
        nextPageStart += size;
        // An empty page does not prove end-of-data: users may leave a large blank area in the sheet.
        // Continue up to the configured maximum so later rows are not silently skipped.
        buffer.addAll(rows);
    }

    @Override
    public void open(ExecutionContext executionContext) throws ItemStreamException {
        checkpointRow = executionContext.getInt(CHECKPOINT_ROW_KEY, 2);
        nextPageStart = checkpointRow;
        buffer.clear();
        sourceRowCount = -1;
        finished = false;
    }

    @Override
    public void update(ExecutionContext executionContext) throws ItemStreamException {
        // Spring Batch calls update after a successful chunk. Persist the first uncommitted sheet row,
        // rather than a page boundary, so a restart never adds an already committed item twice.
        executionContext.putInt(CHECKPOINT_ROW_KEY, checkpointRow);
    }
}
