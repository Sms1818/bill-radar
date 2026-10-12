package com.billradar.persistence;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "workflow_executions", indexes = {
        @Index(name = "idx_workflow_execution_id", columnList = "execution_id", unique = true),
        @Index(name = "idx_workflow_start_time", columnList = "start_time")
})
public class WorkflowExecutionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "execution_id", nullable = false, unique = true, length = 64)
    private String executionId;

    @Column(name = "start_time", nullable = false)
    private Instant startTime;

    @Column(name = "completion_time")
    private Instant completionTime;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "records_processed", nullable = false)
    private int recordsProcessed;

    @Column(name = "records_accepted", nullable = false)
    private int recordsAccepted;

    @Column(name = "records_skipped", nullable = false)
    private int recordsSkipped;

    @Column(name = "untagged_records", nullable = false)
    private int untaggedRecords;

    @Column(name = "duplicate_records", nullable = false)
    private int duplicateRecords;

    @Column(name = "credit_records", nullable = false)
    private int creditRecords;

    @Column(name = "currency_conversions", nullable = false)
    private int currencyConversions;

    @Column(name = "total_spend", precision = 14, scale = 2)
    private BigDecimal totalSpend;

    @Column(name = "alerts_generated", nullable = false)
    private int alertsGenerated;

    @Column(name = "error_details", columnDefinition = "TEXT")
    private String errorDetails;

    public WorkflowExecutionEntity() {
    }

    public WorkflowExecutionEntity(
            String executionId,
            Instant startTime,
            Instant completionTime,
            String status,
            int recordsProcessed,
            int recordsAccepted,
            int recordsSkipped,
            int untaggedRecords,
            int duplicateRecords,
            int creditRecords,
            int currencyConversions,
            BigDecimal totalSpend,
            int alertsGenerated,
            String errorDetails) {
        this.executionId = executionId;
        this.startTime = startTime;
        this.completionTime = completionTime;
        this.status = status;
        this.recordsProcessed = recordsProcessed;
        this.recordsAccepted = recordsAccepted;
        this.recordsSkipped = recordsSkipped;
        this.untaggedRecords = untaggedRecords;
        this.duplicateRecords = duplicateRecords;
        this.creditRecords = creditRecords;
        this.currencyConversions = currencyConversions;
        this.totalSpend = totalSpend;
        this.alertsGenerated = alertsGenerated;
        this.errorDetails = errorDetails;
    }

    public Long getId() {
        return id;
    }

    public String getExecutionId() {
        return executionId;
    }

    public void setExecutionId(String executionId) {
        this.executionId = executionId;
    }

    public Instant getStartTime() {
        return startTime;
    }

    public void setStartTime(Instant startTime) {
        this.startTime = startTime;
    }

    public Instant getCompletionTime() {
        return completionTime;
    }

    public void setCompletionTime(Instant completionTime) {
        this.completionTime = completionTime;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public int getRecordsProcessed() {
        return recordsProcessed;
    }

    public void setRecordsProcessed(int recordsProcessed) {
        this.recordsProcessed = recordsProcessed;
    }

    public int getRecordsAccepted() {
        return recordsAccepted;
    }

    public void setRecordsAccepted(int recordsAccepted) {
        this.recordsAccepted = recordsAccepted;
    }

    public int getRecordsSkipped() {
        return recordsSkipped;
    }

    public void setRecordsSkipped(int recordsSkipped) {
        this.recordsSkipped = recordsSkipped;
    }

    public int getUntaggedRecords() {
        return untaggedRecords;
    }

    public void setUntaggedRecords(int untaggedRecords) {
        this.untaggedRecords = untaggedRecords;
    }

    public int getDuplicateRecords() {
        return duplicateRecords;
    }

    public void setDuplicateRecords(int duplicateRecords) {
        this.duplicateRecords = duplicateRecords;
    }

    public int getCreditRecords() {
        return creditRecords;
    }

    public void setCreditRecords(int creditRecords) {
        this.creditRecords = creditRecords;
    }

    public int getCurrencyConversions() {
        return currencyConversions;
    }

    public void setCurrencyConversions(int currencyConversions) {
        this.currencyConversions = currencyConversions;
    }

    public BigDecimal getTotalSpend() {
        return totalSpend;
    }

    public void setTotalSpend(BigDecimal totalSpend) {
        this.totalSpend = totalSpend;
    }

    public int getAlertsGenerated() {
        return alertsGenerated;
    }

    public void setAlertsGenerated(int alertsGenerated) {
        this.alertsGenerated = alertsGenerated;
    }

    public String getErrorDetails() {
        return errorDetails;
    }

    public void setErrorDetails(String errorDetails) {
        this.errorDetails = errorDetails;
    }
}
