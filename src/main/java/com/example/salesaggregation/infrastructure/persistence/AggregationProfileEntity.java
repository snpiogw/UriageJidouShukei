package com.example.salesaggregation.infrastructure.persistence;

import com.example.salesaggregation.domain.ColumnMapping;
import com.example.salesaggregation.domain.ExecutionProfileSnapshot;
import com.example.salesaggregation.domain.TaxMode;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalTime;

@Entity
@Table(name = "aggregation_profile")
public class AggregationProfileEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "aggregation_profile_seq")
    @SequenceGenerator(name = "aggregation_profile_seq", sequenceName = "aggregation_profile_id_seq", allocationSize = 1)
    private Long id;

    @Column(name = "profile_name", nullable = false, length = 100)
    private String profileName;
    @Column(name = "spreadsheet_id", nullable = false, length = 255)
    private String spreadsheetId;
    @Column(name = "source_sheet_name", nullable = false, length = 100)
    private String sourceSheetName;
    @Column(name = "result_sheet_name", nullable = false, length = 100)
    private String resultSheetName;
    @Column(name = "error_sheet_name", nullable = false, length = 100)
    private String errorSheetName;

    @Enumerated(EnumType.STRING)
    @Column(name = "tax_mode", nullable = false, length = 20)
    private TaxMode taxMode;
    @Column(name = "tax_rate", nullable = false, precision = 9, scale = 4)
    private BigDecimal taxRate;
    @Column(nullable = false)
    private boolean active;
    @Column(name = "auto_enabled", nullable = false)
    private boolean autoEnabled;
    @Column(name = "execution_time", nullable = false)
    private LocalTime executionTime;
    @Column(name = "time_zone", nullable = false, length = 50)
    private String timeZone;

    @Column(name = "date_column", nullable = false, length = 100)
    private String dateColumn;
    @Column(name = "staff_column", nullable = false, length = 100)
    private String staffColumn;
    @Column(name = "product_column", nullable = false, length = 100)
    private String productColumn;
    @Column(name = "quantity_column", nullable = false, length = 100)
    private String quantityColumn;
    @Column(name = "unit_price_column", nullable = false, length = 100)
    private String unitPriceColumn;

    @Version
    private long version;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
    @Column(name = "updated_by", nullable = false, length = 100)
    private String updatedBy;

    protected AggregationProfileEntity() {}

    public AggregationProfileEntity(String profileName, String spreadsheetId, String sourceSheetName,
                                    String resultSheetName, String errorSheetName, TaxMode taxMode,
                                    BigDecimal taxRate, boolean autoEnabled, LocalTime executionTime,
                                    String timeZone, ColumnMapping mapping, String actor) {
        this(profileName, spreadsheetId, sourceSheetName, resultSheetName, errorSheetName, taxMode,
                taxRate, true, autoEnabled, executionTime, timeZone, mapping, actor);
    }

    public AggregationProfileEntity(String profileName, String spreadsheetId, String sourceSheetName,
                                    String resultSheetName, String errorSheetName, TaxMode taxMode,
                                    BigDecimal taxRate, boolean active, boolean autoEnabled, LocalTime executionTime,
                                    String timeZone, ColumnMapping mapping, String actor) {
        this.createdAt = Instant.now();
        updateValues(profileName, spreadsheetId, sourceSheetName, resultSheetName, errorSheetName,
                taxMode, taxRate, active, autoEnabled, executionTime, timeZone, mapping, actor);
    }

    public void update(String profileName, String spreadsheetId, String sourceSheetName,
                       String resultSheetName, String errorSheetName, TaxMode taxMode,
                       BigDecimal taxRate, boolean active, boolean autoEnabled, LocalTime executionTime,
                       String timeZone, ColumnMapping mapping, String actor) {
        updateValues(profileName, spreadsheetId, sourceSheetName, resultSheetName, errorSheetName,
                taxMode, taxRate, active, autoEnabled, executionTime, timeZone, mapping, actor);
    }

    public void fillLegacySpreadsheetId(String spreadsheetId, String actor) {
        if (this.spreadsheetId != null && !this.spreadsheetId.isBlank()) return;
        this.spreadsheetId = spreadsheetId.trim();
        this.updatedAt = Instant.now();
        this.updatedBy = actor;
    }

    private void updateValues(String profileName, String spreadsheetId, String sourceSheetName,
                              String resultSheetName, String errorSheetName, TaxMode taxMode,
                              BigDecimal taxRate, boolean active, boolean autoEnabled, LocalTime executionTime,
                              String timeZone, ColumnMapping mapping, String actor) {
        this.profileName = profileName.trim();
        this.spreadsheetId = spreadsheetId.trim();
        this.sourceSheetName = sourceSheetName.trim();
        this.resultSheetName = resultSheetName.trim();
        this.errorSheetName = errorSheetName.trim();
        this.taxMode = taxMode;
        this.taxRate = taxRate;
        this.active = active;
        this.autoEnabled = autoEnabled;
        this.executionTime = executionTime;
        this.timeZone = timeZone;
        this.dateColumn = mapping.dateColumn();
        this.staffColumn = mapping.staffColumn();
        this.productColumn = mapping.productColumn();
        this.quantityColumn = mapping.quantityColumn();
        this.unitPriceColumn = mapping.unitPriceColumn();
        this.updatedAt = Instant.now();
        this.updatedBy = actor;
    }

    public ExecutionProfileSnapshot snapshot() {
        return new ExecutionProfileSnapshot(id, profileName, spreadsheetId, sourceSheetName, resultSheetName,
                errorSheetName, taxMode, taxRate, timeZone, version, columnMapping());
    }

    public ColumnMapping columnMapping() {
        return new ColumnMapping(dateColumn, staffColumn, productColumn, quantityColumn, unitPriceColumn);
    }

    public Long getId() { return id; }
    public String getProfileName() { return profileName; }
    public String getSpreadsheetId() { return spreadsheetId; }
    public String getSourceSheetName() { return sourceSheetName; }
    public String getResultSheetName() { return resultSheetName; }
    public String getErrorSheetName() { return errorSheetName; }
    public TaxMode getTaxMode() { return taxMode; }
    public BigDecimal getTaxRate() { return taxRate; }
    public boolean isActive() { return active; }
    public boolean isAutoEnabled() { return autoEnabled; }
    public LocalTime getExecutionTime() { return executionTime; }
    public String getTimeZone() { return timeZone; }
    public long getVersion() { return version; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public String getUpdatedBy() { return updatedBy; }
}
