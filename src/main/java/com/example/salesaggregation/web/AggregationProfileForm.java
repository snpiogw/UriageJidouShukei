package com.example.salesaggregation.web;

import com.example.salesaggregation.application.ProfileCommand;
import com.example.salesaggregation.domain.ColumnMapping;
import com.example.salesaggregation.domain.TaxMode;
import jakarta.validation.constraints.*;
import org.springframework.format.annotation.DateTimeFormat;

import java.math.BigDecimal;
import java.time.LocalTime;

public class AggregationProfileForm {
    @NotBlank(message = "設定名を入力してください") @Size(max = 100, message = "設定名は100文字以内で入力してください")
    private String profileName;
    @NotBlank(message = "Spreadsheet IDを入力してください") @Size(max = 255, message = "Spreadsheet IDは255文字以内で入力してください")
    private String spreadsheetId;
    @NotBlank(message = "入力シート名を入力してください") @Size(max = 100)
    private String sourceSheetName;
    @NotBlank(message = "集計結果シート名を入力してください") @Size(max = 100)
    private String resultSheetName;
    @NotBlank(message = "エラーログシート名を入力してください") @Size(max = 100)
    private String errorSheetName;
    @NotNull(message = "税区分を選択してください")
    private TaxMode taxMode;
    @NotNull(message = "税率を入力してください")
    @DecimalMin(value = "0.0000", message = "税率は0以上にしてください")
    @DecimalMax(value = "100.0000", message = "税率は100以下にしてください")
    @Digits(integer = 3, fraction = 4, message = "税率は小数4桁以内にしてください")
    private BigDecimal taxRate;
    private boolean autoEnabled;
    @NotNull(message = "実行時刻を入力してください") @DateTimeFormat(pattern = "HH:mm")
    private LocalTime executionTime;
    @NotBlank(message = "タイムゾーンを入力してください") @Size(max = 50)
    private String timeZone;
    @NotBlank(message = "日付の列名を入力してください") @Size(max = 100)
    private String dateColumn;
    @NotBlank(message = "担当者の列名を入力してください") @Size(max = 100)
    private String staffColumn;
    @NotBlank(message = "商品名の列名を入力してください") @Size(max = 100)
    private String productColumn;
    @NotBlank(message = "数量の列名を入力してください") @Size(max = 100)
    private String quantityColumn;
    @NotBlank(message = "単価の列名を入力してください") @Size(max = 100)
    private String unitPriceColumn;
    private long version;

    public AggregationProfileForm() {
        sourceSheetName = "売上データ";
        resultSheetName = "集計結果";
        errorSheetName = "エラーログ";
        taxMode = TaxMode.INCLUSIVE;
        taxRate = new BigDecimal("10");
        executionTime = LocalTime.of(21, 0);
        timeZone = "Asia/Tokyo";
        dateColumn = "日付";
        staffColumn = "担当者";
        productColumn = "商品名";
        quantityColumn = "数量";
        unitPriceColumn = "単価";
    }

    public ProfileCommand toCommand() {
        return new ProfileCommand(profileName, spreadsheetId, sourceSheetName, resultSheetName, errorSheetName,
                taxMode, taxRate, autoEnabled, executionTime, timeZone,
                new ColumnMapping(dateColumn, staffColumn, productColumn, quantityColumn, unitPriceColumn));
    }

    public String getProfileName() { return profileName; }
    public void setProfileName(String profileName) { this.profileName = profileName; }
    public String getSpreadsheetId() { return spreadsheetId; }
    public void setSpreadsheetId(String spreadsheetId) { this.spreadsheetId = spreadsheetId; }
    public String getSourceSheetName() { return sourceSheetName; }
    public void setSourceSheetName(String sourceSheetName) { this.sourceSheetName = sourceSheetName; }
    public String getResultSheetName() { return resultSheetName; }
    public void setResultSheetName(String resultSheetName) { this.resultSheetName = resultSheetName; }
    public String getErrorSheetName() { return errorSheetName; }
    public void setErrorSheetName(String errorSheetName) { this.errorSheetName = errorSheetName; }
    public TaxMode getTaxMode() { return taxMode; }
    public void setTaxMode(TaxMode taxMode) { this.taxMode = taxMode; }
    public BigDecimal getTaxRate() { return taxRate; }
    public void setTaxRate(BigDecimal taxRate) { this.taxRate = taxRate; }
    public boolean isAutoEnabled() { return autoEnabled; }
    public void setAutoEnabled(boolean autoEnabled) { this.autoEnabled = autoEnabled; }
    public LocalTime getExecutionTime() { return executionTime; }
    public void setExecutionTime(LocalTime executionTime) { this.executionTime = executionTime; }
    public String getTimeZone() { return timeZone; }
    public void setTimeZone(String timeZone) { this.timeZone = timeZone; }
    public String getDateColumn() { return dateColumn; }
    public void setDateColumn(String dateColumn) { this.dateColumn = dateColumn; }
    public String getStaffColumn() { return staffColumn; }
    public void setStaffColumn(String staffColumn) { this.staffColumn = staffColumn; }
    public String getProductColumn() { return productColumn; }
    public void setProductColumn(String productColumn) { this.productColumn = productColumn; }
    public String getQuantityColumn() { return quantityColumn; }
    public void setQuantityColumn(String quantityColumn) { this.quantityColumn = quantityColumn; }
    public String getUnitPriceColumn() { return unitPriceColumn; }
    public void setUnitPriceColumn(String unitPriceColumn) { this.unitPriceColumn = unitPriceColumn; }
    public long getVersion() { return version; }
    public void setVersion(long version) { this.version = version; }
}
