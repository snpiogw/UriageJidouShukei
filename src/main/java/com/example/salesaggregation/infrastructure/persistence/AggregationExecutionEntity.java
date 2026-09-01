package com.example.salesaggregation.infrastructure.persistence;

import com.example.salesaggregation.domain.ExecutionStatus;
import com.example.salesaggregation.domain.ColumnMapping;
import com.example.salesaggregation.domain.ExecutionProfileSnapshot;
import com.example.salesaggregation.domain.TaxMode;
import com.example.salesaggregation.domain.TriggerType;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.UUID;

@Entity
@Table(name = "aggregation_execution")
public class AggregationExecutionEntity {
    @Id
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "trigger_type", nullable = false, length = 20)
    private TriggerType triggerType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private ExecutionStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "tax_mode", nullable = false, length = 20)
    private TaxMode taxMode;

    @Column(name = "tax_rate", nullable = false, precision = 9, scale = 4)
    private BigDecimal taxRate;

    @Column(name = "settings_version", nullable = false)
    private long settingsVersion;

    @Column(name = "profile_id", nullable = false)
    private long profileId;
    @Column(name = "profile_name_snapshot", length = 100)
    private String profileNameSnapshot;
    @Column(name = "spreadsheet_id_snapshot", length = 255)
    private String spreadsheetIdSnapshot;
    @Column(name = "source_sheet_name_snapshot", length = 100)
    private String sourceSheetNameSnapshot;
    @Column(name = "result_sheet_name_snapshot", length = 100)
    private String resultSheetNameSnapshot;
    @Column(name = "error_sheet_name_snapshot", length = 100)
    private String errorSheetNameSnapshot;
    @Column(name = "time_zone_snapshot", length = 50)
    private String timeZoneSnapshot;
    @Column(name = "date_column_snapshot", length = 100)
    private String dateColumnSnapshot;
    @Column(name = "staff_column_snapshot", length = 100)
    private String staffColumnSnapshot;
    @Column(name = "product_column_snapshot", length = 100)
    private String productColumnSnapshot;
    @Column(name = "quantity_column_snapshot", length = 100)
    private String quantityColumnSnapshot;
    @Column(name = "unit_price_column_snapshot", length = 100)
    private String unitPriceColumnSnapshot;

    @Column(name = "requested_at", nullable = false)
    private Instant requestedAt;
    @Column(name = "started_at")
    private Instant startedAt;
    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "source_count", nullable = false)
    private long sourceCount;
    @Column(name = "valid_count", nullable = false)
    private long validCount;
    @Column(name = "invalid_count", nullable = false)
    private long invalidCount;

    @Column(name = "error_code", length = 100)
    private String errorCode;
    @Column(name = "summary", length = 500)
    private String summary;

    protected AggregationExecutionEntity() {}

    public AggregationExecutionEntity(UUID id, TriggerType triggerType, TaxMode taxMode,
                                      BigDecimal taxRate, long settingsVersion) {
        this.id = id;
        this.triggerType = triggerType;
        this.taxMode = taxMode;
        this.taxRate = taxRate;
        this.settingsVersion = settingsVersion;
        this.profileId = 1L;
        this.profileNameSnapshot = "既存設定";
        this.spreadsheetIdSnapshot = "";
        this.sourceSheetNameSnapshot = "売上データ";
        this.resultSheetNameSnapshot = "集計結果";
        this.errorSheetNameSnapshot = "エラーログ";
        this.timeZoneSnapshot = "Asia/Tokyo";
        this.dateColumnSnapshot = "日付";
        this.staffColumnSnapshot = "担当者";
        this.productColumnSnapshot = "商品名";
        this.quantityColumnSnapshot = "数量";
        this.unitPriceColumnSnapshot = "単価";
        this.status = ExecutionStatus.QUEUED;
        this.requestedAt = Instant.now();
    }

    public AggregationExecutionEntity(UUID id, TriggerType triggerType, ExecutionProfileSnapshot profile) {
        this(id, triggerType, profile.taxMode(), profile.taxRate(), profile.version());
        applySnapshot(profile);
    }

    public void fillLegacySnapshotIfMissing(ExecutionProfileSnapshot profile) {
        if (spreadsheetIdSnapshot != null && !spreadsheetIdSnapshot.isBlank()) return;
        applySnapshot(profile);
    }

    private void applySnapshot(ExecutionProfileSnapshot profile) {
        this.profileId = profile.profileId();
        this.profileNameSnapshot = profile.profileName();
        this.spreadsheetIdSnapshot = profile.spreadsheetId();
        this.sourceSheetNameSnapshot = profile.sourceSheetName();
        this.resultSheetNameSnapshot = profile.resultSheetName();
        this.errorSheetNameSnapshot = profile.errorSheetName();
        this.timeZoneSnapshot = profile.timeZone();
        this.dateColumnSnapshot = profile.columnMapping().dateColumn();
        this.staffColumnSnapshot = profile.columnMapping().staffColumn();
        this.productColumnSnapshot = profile.columnMapping().productColumn();
        this.quantityColumnSnapshot = profile.columnMapping().quantityColumn();
        this.unitPriceColumnSnapshot = profile.columnMapping().unitPriceColumn();
    }

    public ExecutionProfileSnapshot profileSnapshot() {
        return new ExecutionProfileSnapshot(profileId, profileNameSnapshot, spreadsheetIdSnapshot,
                sourceSheetNameSnapshot, resultSheetNameSnapshot, errorSheetNameSnapshot, taxMode, taxRate,
                timeZoneSnapshot, settingsVersion, new ColumnMapping(dateColumnSnapshot, staffColumnSnapshot,
                productColumnSnapshot, quantityColumnSnapshot, unitPriceColumnSnapshot));
    }

    public void markRunning() {
        status = ExecutionStatus.RUNNING;
        startedAt = Instant.now();
    }

    public void queueForRestart() {
        if (status != ExecutionStatus.FAILED) {
            throw new IllegalStateException("失敗した集計だけを再開できます");
        }
        status = ExecutionStatus.QUEUED;
        startedAt = null;
        completedAt = null;
        errorCode = null;
        summary = "失敗地点からの再開を受け付けました";
    }

    public void complete(ExecutionStatus finalStatus, long source, long valid, long invalid, String summary) {
        this.status = finalStatus;
        this.sourceCount = source;
        this.validCount = valid;
        this.invalidCount = invalid;
        this.summary = summary;
        this.completedAt = Instant.now();
    }

    public void fail(ExecutionStatus finalStatus, String errorCode, String summary) {
        this.status = finalStatus;
        this.errorCode = errorCode;
        this.summary = summary;
        this.completedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public TriggerType getTriggerType() { return triggerType; }
    public ExecutionStatus getStatus() { return status; }
    public TaxMode getTaxMode() { return taxMode; }
    public BigDecimal getTaxRate() { return taxRate; }
    public long getSettingsVersion() { return settingsVersion; }
    public long getProfileId() { return profileId; }
    public String getProfileNameSnapshot() { return profileNameSnapshot; }
    public Instant getRequestedAt() { return requestedAt; }
    public ZonedDateTime getRequestedAtJst() { return requestedAt.atZone(ZoneId.of("Asia/Tokyo")); }
    public Instant getStartedAt() { return startedAt; }
    public Instant getCompletedAt() { return completedAt; }
    public ZonedDateTime getCompletedAtJst() { return completedAt == null ? null : completedAt.atZone(ZoneId.of("Asia/Tokyo")); }
    public long getSourceCount() { return sourceCount; }
    public long getValidCount() { return validCount; }
    public long getInvalidCount() { return invalidCount; }
    public String getErrorCode() { return errorCode; }
    public String getSummary() { return summary; }
}
