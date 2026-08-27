package com.example.salesaggregation.infrastructure.persistence;

import com.example.salesaggregation.domain.ExecutionStatus;
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
        this.status = ExecutionStatus.QUEUED;
        this.requestedAt = Instant.now();
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
