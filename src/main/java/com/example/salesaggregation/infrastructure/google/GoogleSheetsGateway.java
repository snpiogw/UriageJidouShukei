package com.example.salesaggregation.infrastructure.google;

import com.example.salesaggregation.config.AppProperties;
import com.example.salesaggregation.domain.AggregationResult;
import com.example.salesaggregation.domain.RawSalesRow;
import com.example.salesaggregation.domain.RowError;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.services.sheets.v4.Sheets;
import com.google.api.services.sheets.v4.model.*;
import org.springframework.stereotype.Component;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Component
@ConditionalOnExpression("'${app.sheets.spreadsheet-id:}' != ''")
public class GoogleSheetsGateway implements SalesSheetGateway {
    private static final List<String> EXPECTED_HEADERS = List.of("日付", "担当者", "商品名", "数量", "単価");
    private static final int OUTPUT_ROWS_TO_CLEAR = 20_000;
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final Sheets sheets;
    private final AppProperties properties;

    public GoogleSheetsGateway(Sheets sheets, AppProperties properties) {
        this.sheets = sheets;
        this.properties = properties;
    }

    @Override
    public void validateHeader() throws IOException {
        requireSpreadsheetId();
        List<List<Object>> values = executeWithRetry(() -> sheets.spreadsheets().values()
                .get(properties.sheets().spreadsheetId(), quote(properties.sheets().sourceSheet()) + "!A1:E1")
                .setValueRenderOption("FORMATTED_VALUE")
                .execute().getValues());
        if (values == null || values.isEmpty()) {
            throw new IOException("売上データの見出しがありません");
        }
        List<String> actual = new ArrayList<>();
        for (int i = 0; i < EXPECTED_HEADERS.size(); i++) {
            actual.add(i < values.getFirst().size() ? values.getFirst().get(i).toString().trim() : "");
        }
        if (!actual.equals(EXPECTED_HEADERS)) {
            throw new IOException("見出しは「日付、担当者、商品名、数量、単価」にしてください");
        }
    }

    @Override
    public int sourceRowCount() throws IOException {
        requireSpreadsheetId();
        Spreadsheet spreadsheet = executeWithRetry(() -> sheets.spreadsheets()
                .get(properties.sheets().spreadsheetId())
                .setFields("sheets.properties(title,gridProperties(rowCount))")
                .execute());
        return spreadsheet.getSheets().stream()
                .map(Sheet::getProperties)
                .filter(sheet -> properties.sheets().sourceSheet().equals(sheet.getTitle()))
                .map(SheetProperties::getGridProperties)
                .map(GridProperties::getRowCount)
                .findFirst()
                .orElseThrow(() -> new IOException("入力シート「" + properties.sheets().sourceSheet() + "」がありません"));
    }

    @Override
    public List<RawSalesRow> readRows(int startRow, int pageSize) throws IOException {
        requireSpreadsheetId();
        int endRow = startRow + pageSize - 1;
        String range = quote(properties.sheets().sourceSheet()) + "!A" + startRow + ":E" + endRow;
        List<List<Object>> values = executeWithRetry(() -> sheets.spreadsheets().values().get(
                        properties.sheets().spreadsheetId(), range)
                .setValueRenderOption("UNFORMATTED_VALUE")
                .setDateTimeRenderOption("SERIAL_NUMBER")
                .execute().getValues());
        if (values == null) return List.of();
        List<RawSalesRow> rows = new ArrayList<>(values.size());
        for (int i = 0; i < values.size(); i++) {
            List<Object> row = values.get(i);
            if (row == null || row.stream().allMatch(v -> v == null || v.toString().isBlank())) continue;
            rows.add(new RawSalesRow(startRow + i, row));
        }
        return rows;
    }

    @Override
    public void writeResult(AggregationResult result) throws IOException {
        Map<String, Integer> ids = ensureOutputSheets();
        List<List<Object>> resultRows = resultMatrix(result);
        List<List<Object>> errorRows = errorMatrix(result.executionId(), result.errors());
        List<Request> requests = new ArrayList<>();
        requests.add(clearRequest(ids.get(properties.sheets().resultSheet()), OUTPUT_ROWS_TO_CLEAR, 11));
        requests.add(clearRequest(ids.get(properties.sheets().errorSheet()), OUTPUT_ROWS_TO_CLEAR, 6));
        requests.add(updateRequest(ids.get(properties.sheets().resultSheet()), resultRows));
        requests.add(updateRequest(ids.get(properties.sheets().errorSheet()), errorRows));
        batchUpdate(requests);
    }

    @Override
    public void writeErrorsOnly(UUID executionId, List<RowError> errors) throws IOException {
        Map<String, Integer> ids = ensureOutputSheets();
        List<Request> requests = List.of(
                clearRequest(ids.get(properties.sheets().errorSheet()), OUTPUT_ROWS_TO_CLEAR, 6),
                updateRequest(ids.get(properties.sheets().errorSheet()), errorMatrix(executionId, errors)));
        batchUpdate(requests);
    }

    private void batchUpdate(List<Request> requests) throws IOException {
        executeWithRetry(() -> sheets.spreadsheets().batchUpdate(
                properties.sheets().spreadsheetId(),
                new BatchUpdateSpreadsheetRequest().setRequests(requests)).execute());
    }

    private Map<String, Integer> ensureOutputSheets() throws IOException {
        Set<String> required = Set.of(properties.sheets().resultSheet(), properties.sheets().errorSheet());
        Spreadsheet spreadsheet = executeWithRetry(() -> sheets.spreadsheets()
                .get(properties.sheets().spreadsheetId())
                .setFields("sheets.properties(sheetId,title,gridProperties(rowCount,columnCount))")
                .execute());
        Map<String, Integer> ids = sheetIds(spreadsheet);
        Map<String, Integer> existingIds = ids;
        List<Request> adds = required.stream()
                .filter(name -> !existingIds.containsKey(name))
                .map(name -> new Request().setAddSheet(new AddSheetRequest()
                        .setProperties(new SheetProperties().setTitle(name)
                                .setGridProperties(new GridProperties().setRowCount(OUTPUT_ROWS_TO_CLEAR).setColumnCount(12)))))
                .toList();
        if (!adds.isEmpty()) {
            batchUpdate(adds);
            spreadsheet = executeWithRetry(() -> sheets.spreadsheets()
                    .get(properties.sheets().spreadsheetId())
                    .setFields("sheets.properties(sheetId,title,gridProperties(rowCount,columnCount))")
                    .execute());
            ids = sheetIds(spreadsheet);
        }
        List<Request> resizes = new ArrayList<>();
        for (Sheet sheet : spreadsheet.getSheets()) {
            if (!required.contains(sheet.getProperties().getTitle())) continue;
            GridProperties grid = sheet.getProperties().getGridProperties();
            if (grid.getRowCount() < OUTPUT_ROWS_TO_CLEAR || grid.getColumnCount() < 12) {
                resizes.add(new Request().setUpdateSheetProperties(new UpdateSheetPropertiesRequest()
                        .setProperties(new SheetProperties().setSheetId(sheet.getProperties().getSheetId())
                                .setGridProperties(new GridProperties()
                                        .setRowCount(Math.max(grid.getRowCount(), OUTPUT_ROWS_TO_CLEAR))
                                        .setColumnCount(Math.max(grid.getColumnCount(), 12))))
                        .setFields("gridProperties(rowCount,columnCount)")));
            }
        }
        if (!resizes.isEmpty()) batchUpdate(resizes);
        return ids;
    }

    private Map<String, Integer> sheetIds(Spreadsheet spreadsheet) {
        Map<String, Integer> ids = new HashMap<>();
        for (Sheet sheet : spreadsheet.getSheets()) {
            ids.put(sheet.getProperties().getTitle(), sheet.getProperties().getSheetId());
        }
        return ids;
    }

    private Request clearRequest(int sheetId, int rows, int columns) {
        return new Request().setRepeatCell(new RepeatCellRequest()
                .setRange(new GridRange().setSheetId(sheetId).setStartRowIndex(0).setEndRowIndex(rows)
                        .setStartColumnIndex(0).setEndColumnIndex(columns))
                .setCell(new CellData())
                .setFields("userEnteredValue,userEnteredFormat"));
    }

    private Request updateRequest(int sheetId, List<List<Object>> matrix) {
        List<RowData> rows = matrix.stream()
                .map(row -> new RowData().setValues(row.stream().map(this::cell).toList()))
                .toList();
        return new Request().setUpdateCells(new UpdateCellsRequest()
                .setStart(new GridCoordinate().setSheetId(sheetId).setRowIndex(0).setColumnIndex(0))
                .setRows(rows)
                .setFields("userEnteredValue,userEnteredFormat.numberFormat"));
    }

    private CellData cell(Object value) {
        ExtendedValue extended = new ExtendedValue();
        CellData cell = new CellData();
        if (value instanceof BigDecimal number) {
            extended.setNumberValue(number.doubleValue());
            cell.setUserEnteredFormat(new CellFormat()
                    .setNumberFormat(new NumberFormat().setType("CURRENCY").setPattern("¥#,##0")));
        } else if (value instanceof Number number) {
            extended.setNumberValue(number.doubleValue());
        } else if (value instanceof Boolean bool) {
            extended.setBoolValue(bool);
        } else {
            extended.setStringValue(value == null ? "" : value.toString());
        }
        return cell.setUserEnteredValue(extended);
    }

    private List<List<Object>> resultMatrix(AggregationResult result) {
        List<List<Object>> rows = new ArrayList<>();
        rows.add(List.of("実行ID", result.executionId().toString()));
        rows.add(List.of("完了日時", DATE_TIME.format(result.completedAt().atZone(ZoneId.of("Asia/Tokyo")))));
        rows.add(List.of("税区分", result.taxMode().label()));
        rows.add(List.of("税率", result.taxRate().toPlainString() + "%"));
        rows.add(List.of("設定バージョン", result.settingsVersion()));
        rows.add(List.of("対象行数", result.sourceCount()));
        rows.add(List.of("正常行数", result.validCount()));
        rows.add(List.of("除外行数", result.invalidCount()));
        rows.add(List.of());
        rows.add(List.of("商品名", amountLabel(result), "", "担当者", amountLabel(result), "", "年月", amountLabel(result), "", "区分", "金額"));

        int max = Math.max(Math.max(result.productTotals().size(), result.staffTotals().size()), result.monthlyTotals().size());
        List<Map.Entry<String, BigDecimal>> products = new ArrayList<>(result.productTotals().entrySet());
        List<Map.Entry<String, BigDecimal>> staff = new ArrayList<>(result.staffTotals().entrySet());
        List<Map.Entry<String, BigDecimal>> months = new ArrayList<>(result.monthlyTotals().entrySet());
        for (int i = 0; i < Math.max(max, 1); i++) {
            List<Object> row = new ArrayList<>(Collections.nCopies(11, ""));
            if (i < products.size()) { row.set(0, products.get(i).getKey()); row.set(1, products.get(i).getValue()); }
            if (i < staff.size()) { row.set(3, staff.get(i).getKey()); row.set(4, staff.get(i).getValue()); }
            if (i < months.size()) { row.set(6, months.get(i).getKey()); row.set(7, months.get(i).getValue()); }
            if (i == 0) { row.set(9, "総売上"); row.set(10, result.grandTotal()); }
            rows.add(row);
        }
        return rows;
    }

    private String amountLabel(AggregationResult result) {
        return "売上額（" + result.taxMode().label() + "）";
    }

    private List<List<Object>> errorMatrix(UUID executionId, List<RowError> errors) {
        List<List<Object>> rows = new ArrayList<>();
        rows.add(List.of("実行ID", "実行日時", "行番号", "項目", "エラーコード", "修正方法"));
        String now = DATE_TIME.format(java.time.ZonedDateTime.now(ZoneId.of("Asia/Tokyo")));
        for (RowError error : errors) {
            rows.add(List.of(executionId.toString(), now, error.rowNumber(), error.field(), error.code(), error.guidance()));
        }
        return rows;
    }

    private String quote(String sheetName) {
        return "'" + sheetName.replace("'", "''") + "'";
    }

    private void requireSpreadsheetId() {
        if (properties.sheets().spreadsheetId() == null || properties.sheets().spreadsheetId().isBlank()) {
            throw new IllegalStateException("GOOGLE_SPREADSHEET_IDが設定されていません");
        }
    }

    private <T> T executeWithRetry(IOSupplier<T> operation) throws IOException {
        IOException last = null;
        int attempts = properties.batch().transientRetryCount() + 1;
        for (int attempt = 1; attempt <= attempts; attempt++) {
            try {
                return operation.get();
            } catch (GoogleJsonResponseException ex) {
                if (!retryable(ex.getStatusCode()) || attempt == attempts) throw ex;
                last = ex;
            } catch (IOException ex) {
                if (attempt == attempts) throw ex;
                last = ex;
            }
            try {
                Thread.sleep(Math.min(4000L, 250L << (attempt - 1)));
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new IOException("Google Sheets APIの再試行が中断されました", ex);
            }
        }
        throw last == null ? new IOException("Google Sheets API request failed") : last;
    }

    private boolean retryable(int statusCode) {
        return statusCode == 429 || statusCode >= 500;
    }

    @FunctionalInterface
    private interface IOSupplier<T> { T get() throws IOException; }
}
