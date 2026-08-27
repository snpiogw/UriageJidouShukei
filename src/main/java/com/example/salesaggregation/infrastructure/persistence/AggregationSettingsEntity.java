package com.example.salesaggregation.infrastructure.persistence;

import com.example.salesaggregation.domain.TaxMode;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalTime;

@Entity
@Table(name = "aggregation_settings")
public class AggregationSettingsEntity {
    @Id
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "tax_mode", nullable = false, length = 20)
    private TaxMode taxMode;

    @Column(name = "tax_rate", nullable = false, precision = 9, scale = 4)
    private BigDecimal taxRate;

    @Column(name = "auto_enabled", nullable = false)
    private boolean autoEnabled;

    @Column(name = "execution_time", nullable = false)
    private LocalTime executionTime;

    @Column(name = "time_zone", nullable = false, length = 50)
    private String timeZone;

    @Version
    private long version;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "updated_by", nullable = false, length = 100)
    private String updatedBy;

    protected AggregationSettingsEntity() {}

    public AggregationSettingsEntity(Long id, TaxMode taxMode, BigDecimal taxRate, boolean autoEnabled,
                                     LocalTime executionTime, String timeZone, String updatedBy) {
        this.id = id;
        this.taxMode = taxMode;
        this.taxRate = taxRate;
        this.autoEnabled = autoEnabled;
        this.executionTime = executionTime;
        this.timeZone = timeZone;
        this.updatedBy = updatedBy;
        this.updatedAt = Instant.now();
    }

    public void update(TaxMode taxMode, BigDecimal taxRate, boolean autoEnabled,
                       LocalTime executionTime, String updatedBy) {
        this.taxMode = taxMode;
        this.taxRate = taxRate;
        this.autoEnabled = autoEnabled;
        this.executionTime = executionTime;
        this.updatedBy = updatedBy;
        this.updatedAt = Instant.now();
    }

    public Long getId() { return id; }
    public TaxMode getTaxMode() { return taxMode; }
    public BigDecimal getTaxRate() { return taxRate; }
    public boolean isAutoEnabled() { return autoEnabled; }
    public LocalTime getExecutionTime() { return executionTime; }
    public String getTimeZone() { return timeZone; }
    public long getVersion() { return version; }
    public Instant getUpdatedAt() { return updatedAt; }
    public String getUpdatedBy() { return updatedBy; }
}
