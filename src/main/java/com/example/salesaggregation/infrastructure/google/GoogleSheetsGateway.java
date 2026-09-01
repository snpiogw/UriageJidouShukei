package com.example.salesaggregation.infrastructure.google;

import com.example.salesaggregation.config.AppProperties;
import com.example.salesaggregation.domain.AggregationResult;
import com.example.salesaggregation.domain.ExecutionProfileSnapshot;
import com.example.salesaggregation.domain.RawSalesRow;
import com.example.salesaggregation.domain.RowError;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.services.sheets.v4.Sheets;
import com.google.api.services.sheets.v4.model.*;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.BeansException;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Component
public class GoogleSheetsGateway implements SalesSheetGateway {
    private static final int OUTPUT_ROWS_TO_CLEAR = 20_000;
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final ObjectProvider<Sheets> sheetsProvider;
    private final AppProperties properties;
    private final HeaderMappingResolver headerMappingResolver;

    public GoogleSheetsGateway(ObjectProvider<Sheets> sheetsProvider, AppProperties properties,
                               HeaderMappingResolver headerMappingResolver) {
        this.sheetsProvider = sheetsProvider;
        this.properties = properties;
        this.headerMappingResolver = headerMappingResolver;
    }

    @Override
    public ResolvedColumnMapping validateHeader(ExecutionProfileSnapshot profile) throws IOException {
        requireSpreadsheetId(profile);
        List<List<Object>> values = executeWithRetry(() -> sheets().spreadsheets().values()
                .get(profile.spreadsheetId(), quote(profile.sourceSheetName()) + "!1:1")
                .setValueRenderOption("FORMATTED_VALUE")
                .execute().getValues());
        if (values == null || values.isEmpty()) {
            throw new IOException("売上データの見出しがありません");
        }
        return headerMappingResolver.resolve(values.getFirst(), profile.columnMapping());
    }

    @Override
    public int sourceRowCount(ExecutionProfileSnapshot profile) throws IOException {
        requireSpreadsheetId(profile);
        Spreadsheet spreadsheet = executeWithRetry(() -> sheets().spreadsheets()
                .get(profile.spreadsheetId())
                .setFields("sheets.properties(title,gridProperties(rowCount))")
                .execute());
        return spreadsheet.getSheets().stream()
                .map(Sheet::getProperties)
                .filter(sheet -> profile.sourceSheetName().equals(sheet.getTitle()))
                .map(SheetProperties::getGridProperties)
                .map(GridProperties::getRowCount)
                .findFirst()
                .orElseThrow(() -> new IOException("入力シート「" + profile.sourceSheetName() + "」がありません"));
    }

    @Override
    public List<RawSalesRow> readRows(ExecutionProfileSnapshot profile, ResolvedColumnMapping mapping,
                                      int startRow, int pageSize) throws IOException {
        requireSpreadsheetId(profile);
        int endRow = startRow + pageSize - 1;
        String range = quote(profile.sourceSheetName()) + "!A" + startRow + ":"
                + columnName(mapping.lastIndex()) + endRow;
        List<List<Object>> values = executeWithRetry(() -> sheets().spreadsheets().values().get(
                        profile.spreadsheetId(), range)
                .setValueRenderOption("UNFORMATTED_VALUE")
                .setDateTimeRenderOption("SERIAL_NUMBER")
                .execute().getValues());
        if (values == null) return List.of();
        List<RawSalesRow> rows = new ArrayList<>(values.size());
        for (int i = 0; i < values.size(); i++) {
            List<Object> row = values.get(i);
            if (row == null) continue;
            RawSalesRow canonical = mapping.canonicalRow(startRow + i, row);
            if (canonical.cells().stream().allMatch(v -> v == null || v.toString().isBlank())) continue;
            rows.add(canonical);
        }
        return rows;
    }

    @Override
    public void writeResult(ExecutionProfileSnapshot profile, AggregationResult result) throws IOException {
        Map<String, Integer> ids = ensureOutputSheets(profile);
        List<List<Object>> resultRows = resultMatrix(profile, result);
        List<List<Object>> errorRows = errorMatrix(profile, result.executionId(), result.errors());
        List<Request> requests = new ArrayList<>();
        requests.add(clearRequest(ids.get(profile.resultSheetName()), OUTPUT_ROWS_TO_CLEAR, 11));
        requests.add(clearRequest(ids.get(profile.errorSheetName()), OUTPUT_ROWS_TO_CLEAR, 6));
        requests.add(updateRequest(ids.get(profile.resultSheetName()), resultRows));
        requests.add(updateRequest(ids.get(profile.errorSheetName()), errorRows));
        requests.addAll(resultFormattingRequests(ids.get(profile.resultSheetName())));
        requests.addAll(errorFormattingRequests(ids.get(profile.errorSheetName())));
        batchUpdate(profile, requests);
    }

    @Override
    public void writeErrorsOnly(ExecutionProfileSnapshot profile, UUID executionId, List<RowError> errors) throws IOException {
        Map<String, Integer> ids = ensureOutputSheets(profile);
        List<Request> requests = List.of(
                clearRequest(ids.get(profile.errorSheetName()), OUTPUT_ROWS_TO_CLEAR, 6),
                updateRequest(ids.get(profile.errorSheetName()), errorMatrix(profile, executionId, errors)),
                errorHeaderRequest(ids.get(profile.errorSheetName())),
                freezeRowsRequest(ids.get(profile.errorSheetName()), 1),
                autoResizeRequest(ids.get(profile.errorSheetName()), 6));
        batchUpdate(profile, requests);
    }

    private List<Request> resultFormattingRequests(int sheetId) {
        return List.of(
                formatRequest(sheetId, 0, 9, 0, 1, new Color().setRed(.91f).setGreen(.95f).setBlue(.93f),
                        new Color().setRed(.09f).setGreen(.31f).setBlue(.23f), false),
                formatRequest(sheetId, 10, 11, 0, 11, new Color().setRed(.09f).setGreen(.42f).setBlue(.30f),
                        new Color().setRed(1f).setGreen(1f).setBlue(1f), true),
                freezeRowsRequest(sheetId, 11),
                autoResizeRequest(sheetId, 11));
    }

    private List<Request> errorFormattingRequests(int sheetId) {
        return List.of(errorHeaderRequest(sheetId), freezeRowsRequest(sheetId, 1), autoResizeRequest(sheetId, 6));
    }

    private Request errorHeaderRequest(int sheetId) {
        return formatRequest(sheetId, 0, 1, 0, 6,
                new Color().setRed(.65f).setGreen(.22f).setBlue(.22f),
                new Color().setRed(1f).setGreen(1f).setBlue(1f), true);
    }

    private Request formatRequest(int sheetId, int startRow, int endRow, int startColumn, int endColumn,
                                  Color background, Color foreground, boolean centered) {
        CellFormat format = new CellFormat()
                .setBackgroundColor(background)
                .setTextFormat(new TextFormat().setBold(true).setForegroundColor(foreground));
        if (centered) format.setHorizontalAlignment("CENTER");
        return new Request().setRepeatCell(new RepeatCellRequest()
                .setRange(new GridRange().setSheetId(sheetId).setStartRowIndex(startRow).setEndRowIndex(endRow)
                        .setStartColumnIndex(startColumn).setEndColumnIndex(endColumn))
                .setCell(new CellData().setUserEnteredFormat(format))
                .setFields("userEnteredFormat(backgroundColor,textFormat,horizontalAlignment)"));
    }

    private Request freezeRowsRequest(int sheetId, int rows) {
        return new Request().setUpdateSheetProperties(new UpdateSheetPropertiesRequest()
                .setProperties(new SheetProperties().setSheetId(sheetId)
                        .setGridProperties(new GridProperties().setFrozenRowCount(rows)))
                .setFields("gridProperties.frozenRowCount"));
    }

    private Request autoResizeRequest(int sheetId, int columns) {
        return new Request().setAutoResizeDimensions(new AutoResizeDimensionsRequest()
                .setDimensions(new DimensionRange().setSheetId(sheetId).setDimension("COLUMNS")
                        .setStartIndex(0).setEndIndex(columns)));
    }

    private void batchUpdate(ExecutionProfileSnapshot profile, List<Request> requests) throws IOException {
        executeWithRetry(() -> sheets().spreadsheets().batchUpdate(
                profile.spreadsheetId(),
                new BatchUpdateSpreadsheetRequest().setRequests(requests)).execute());
    }

    private Map<String, Integer> ensureOutputSheets(ExecutionProfileSnapshot profile) throws IOException {
        Set<String> required = Set.of(profile.resultSheetName(), profile.errorSheetName());
        Spreadsheet spreadsheet = executeWithRetry(() -> sheets().spreadsheets()
                .get(profile.spreadsheetId())
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
            batchUpdate(profile, adds);
            spreadsheet = executeWithRetry(() -> sheets().spreadsheets()
                    .get(profile.spreadsheetId())
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
        if (!resizes.isEmpty()) batchUpdate(profile, resizes);
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

    private List<List<Object>> resultMatrix(ExecutionProfileSnapshot profile, AggregationResult result) {
        List<List<Object>> rows = new ArrayList<>();
        rows.add(List.of("実行ID", result.executionId().toString()));
        rows.add(List.of("完了日時", DATE_TIME.format(result.completedAt().atZone(ZoneId.of(profile.timeZone())))));
        rows.add(List.of("集計設定", profile.profileName()));
        rows.add(List.of("税区分", result.taxMode().label()));
        BigDecimal displayRate = result.taxRate().stripTrailingZeros();
        rows.add(List.of("税率", (displayRate.scale() < 0 ? displayRate.setScale(0) : displayRate).toPlainString() + "%"));
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

    private List<List<Object>> errorMatrix(ExecutionProfileSnapshot profile, UUID executionId, List<RowError> errors) {
        List<List<Object>> rows = new ArrayList<>();
        rows.add(List.of("実行ID", "実行日時", "行番号", "項目", "エラーコード", "修正方法"));
        String now = DATE_TIME.format(java.time.ZonedDateTime.now(ZoneId.of(profile.timeZone())));
        for (RowError error : errors) {
            rows.add(List.of(executionId.toString(), now, error.rowNumber(), error.field(), error.code(), error.guidance()));
        }
        return rows;
    }

    private String quote(String sheetName) {
        return "'" + sheetName.replace("'", "''") + "'";
    }

    private void requireSpreadsheetId(ExecutionProfileSnapshot profile) {
        if (profile.spreadsheetId() == null || profile.spreadsheetId().isBlank()) {
            throw new IllegalStateException("集計設定のSpreadsheet IDが設定されていません");
        }
    }

    private String columnName(int zeroBasedIndex) {
        int value = zeroBasedIndex + 1;
        StringBuilder name = new StringBuilder();
        while (value > 0) {
            value--;
            name.append((char) ('A' + value % 26));
            value /= 26;
        }
        return name.reverse().toString();
    }

    private Sheets sheets() throws IOException {
        try {
            return sheetsProvider.getObject();
        } catch (BeansException ex) {
            throw new IOException("Googleサービスアカウント認証を初期化できません", ex);
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
