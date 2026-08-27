package com.example.salesaggregation.batch;

import com.example.salesaggregation.config.AppProperties;
import com.example.salesaggregation.domain.AggregationResult;
import com.example.salesaggregation.domain.RawSalesRow;
import com.example.salesaggregation.domain.RowError;
import com.example.salesaggregation.infrastructure.google.SalesSheetGateway;
import org.junit.jupiter.api.Test;
import org.springframework.batch.item.ExecutionContext;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class GoogleSalesItemReaderTest {
    private final SalesSheetGateway gateway = new InMemorySheetGateway();
    private final AppProperties properties = new AppProperties(
            new AppProperties.Sheets("sheet", "売上データ", "集計結果", "エラーログ", 100, 4),
            new AppProperties.Security("admin", "hash"),
            new AppProperties.Batch(2, 3));

    @Test
    void restartsAtFirstUncommittedRowInsteadOfPageBoundary() throws Exception {
        ExecutionContext checkpoint = new ExecutionContext();
        GoogleSalesItemReader firstAttempt = new GoogleSalesItemReader(gateway, properties);
        firstAttempt.open(checkpoint);

        assertThat(firstAttempt.read().rowNumber()).isEqualTo(2);
        assertThat(firstAttempt.read().rowNumber()).isEqualTo(3);
        firstAttempt.update(checkpoint);

        GoogleSalesItemReader restarted = new GoogleSalesItemReader(gateway, properties);
        restarted.open(checkpoint);

        assertThat(restarted.read().rowNumber()).isEqualTo(4);
    }

    @Test
    void continuesAfterAnEmptyPage() throws Exception {
        GoogleSalesItemReader reader = new GoogleSalesItemReader(new InMemorySheetGateway(
                List.of(InMemorySheetGateway.row(2), InMemorySheetGateway.row(7))), properties);
        reader.open(new ExecutionContext());

        assertThat(reader.read().rowNumber()).isEqualTo(2);
        assertThat(reader.read().rowNumber()).isEqualTo(7);
    }

    @Test
    void stopsAtThePhysicalEndOfTheSheetGrid() throws Exception {
        AppProperties largeLimit = new AppProperties(
                new AppProperties.Sheets("sheet", "売上データ", "集計結果", "エラーログ", 10_000, 500),
                properties.security(), properties.batch());
        InMemorySheetGateway smallGrid = new InMemorySheetGateway(
                List.of(InMemorySheetGateway.row(2)), 1_000);
        GoogleSalesItemReader reader = new GoogleSalesItemReader(smallGrid, largeLimit);
        reader.open(new ExecutionContext());

        assertThat(reader.read().rowNumber()).isEqualTo(2);
        assertThat(reader.read()).isNull();
        assertThat(smallGrid.largestRequestedRow).isEqualTo(1_000);
    }

    private static final class InMemorySheetGateway implements SalesSheetGateway {
        private final List<RawSalesRow> rows;
        private final int rowCount;
        private int largestRequestedRow;

        private InMemorySheetGateway() {
            this(List.of(row(2), row(3), row(4), row(5), row(6)), 6);
        }

        private InMemorySheetGateway(List<RawSalesRow> rows) {
            this(rows, rows.stream().mapToInt(RawSalesRow::rowNumber).max().orElse(1));
        }

        private InMemorySheetGateway(List<RawSalesRow> rows, int rowCount) {
            this.rows = rows;
            this.rowCount = rowCount;
        }

        @Override
        public void validateHeader() {}

        @Override
        public int sourceRowCount() {
            return rowCount;
        }

        @Override
        public List<RawSalesRow> readRows(int startRow, int pageSize) {
            int endExclusive = startRow + pageSize;
            largestRequestedRow = Math.max(largestRequestedRow, endExclusive - 1);
            if (largestRequestedRow > rowCount) {
                throw new IllegalArgumentException("read exceeded physical grid");
            }
            return rows.stream()
                    .filter(row -> row.rowNumber() >= startRow && row.rowNumber() < endExclusive)
                    .toList();
        }

        @Override
        public void writeResult(AggregationResult result) {}

        @Override
        public void writeErrorsOnly(UUID executionId, List<RowError> errors) {}

        private static RawSalesRow row(int number) {
            return new RawSalesRow(number, List.of("2026-08-26", "担当", "商品", 1, 100));
        }
    }
}
