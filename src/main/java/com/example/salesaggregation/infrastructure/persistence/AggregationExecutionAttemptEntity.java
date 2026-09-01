package com.example.salesaggregation.infrastructure.persistence;

import com.example.salesaggregation.domain.ExecutionStatus;
import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "aggregation_execution_attempt",
        uniqueConstraints = @UniqueConstraint(columnNames = {"execution_id", "attempt_number"}))
public class AggregationExecutionAttemptEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "execution_id", nullable = false)
    private java.util.UUID executionId;

    @Column(name = "attempt_number", nullable = false)
    private int attemptNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private ExecutionStatus status;

    @Column(name = "requested_at", nullable = false)
    private Instant requestedAt;
    @Column(name = "started_at")
    private Instant startedAt;
    @Column(name = "completed_at")
    private Instant completedAt;
    @Column(name = "error_code", length = 100)
    private String errorCode;
    @Column(length = 500)
    private String summary;

    protected AggregationExecutionAttemptEntity() {}

    public AggregationExecutionAttemptEntity(java.util.UUID executionId, int attemptNumber, String summary) {
        this.executionId = executionId;
        this.attemptNumber = attemptNumber;
        this.status = ExecutionStatus.QUEUED;
        this.requestedAt = Instant.now();
        this.summary = summary;
    }

    public void markRunning() {
        status = ExecutionStatus.RUNNING;
        startedAt = Instant.now();
        completedAt = null;
        errorCode = null;
    }

    public void complete(ExecutionStatus finalStatus, String summary) {
        status = finalStatus;
        this.summary = summary;
        this.completedAt = Instant.now();
    }

    public void fail(ExecutionStatus finalStatus, String errorCode, String summary) {
        status = finalStatus;
        this.errorCode = errorCode;
        this.summary = summary;
        this.completedAt = Instant.now();
    }

    public Long getId() { return id; }
    public java.util.UUID getExecutionId() { return executionId; }
    public int getAttemptNumber() { return attemptNumber; }
    public ExecutionStatus getStatus() { return status; }
    public Instant getRequestedAt() { return requestedAt; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getCompletedAt() { return completedAt; }
    public String getErrorCode() { return errorCode; }
    public String getSummary() { return summary; }
}
