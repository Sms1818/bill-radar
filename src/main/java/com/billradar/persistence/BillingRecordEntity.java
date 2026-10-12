package com.billradar.persistence;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "billing_records", indexes = {
        @Index(name = "idx_billing_fingerprint", columnList = "record_fingerprint", unique = true),
        @Index(name = "idx_billing_workload", columnList = "workload"),
        @Index(name = "idx_billing_usage_date", columnList = "usage_date")
})
public class BillingRecordEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "usage_date", nullable = false)
    private LocalDate usageDate;

    @Column(name = "provider", nullable = false, length = 50)
    private String provider;

    @Column(name = "service", nullable = false, length = 100)
    private String service;

    @Column(name = "region", length = 50)
    private String region;

    @Column(name = "cost", nullable = false, precision = 14, scale = 2)
    private BigDecimal cost;

    @Column(name = "currency", nullable = false, length = 10)
    private String currency;

    @Column(name = "original_cost", nullable = false, precision = 14, scale = 2)
    private BigDecimal originalCost;

    @Column(name = "original_currency", nullable = false, length = 10)
    private String originalCurrency;

    @Column(name = "workload", length = 100)
    private String workload;

    @Column(name = "record_fingerprint", nullable = false, unique = true, length = 64)
    private String recordFingerprint;

    @Column(name = "execution_id", length = 64)
    private String executionId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    public BillingRecordEntity() {
    }

    public BillingRecordEntity(
            LocalDate usageDate,
            String provider,
            String service,
            String region,
            BigDecimal cost,
            String currency,
            BigDecimal originalCost,
            String originalCurrency,
            String workload,
            String recordFingerprint,
            String executionId) {
        this.usageDate = usageDate;
        this.provider = provider;
        this.service = service;
        this.region = region;
        this.cost = cost;
        this.currency = currency;
        this.originalCost = originalCost;
        this.originalCurrency = originalCurrency;
        this.workload = workload;
        this.recordFingerprint = recordFingerprint;
        this.executionId = executionId;
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public LocalDate getUsageDate() {
        return usageDate;
    }

    public void setUsageDate(LocalDate usageDate) {
        this.usageDate = usageDate;
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public String getService() {
        return service;
    }

    public void setService(String service) {
        this.service = service;
    }

    public String getRegion() {
        return region;
    }

    public void setRegion(String region) {
        this.region = region;
    }

    public BigDecimal getCost() {
        return cost;
    }

    public void setCost(BigDecimal cost) {
        this.cost = cost;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public BigDecimal getOriginalCost() {
        return originalCost;
    }

    public void setOriginalCost(BigDecimal originalCost) {
        this.originalCost = originalCost;
    }

    public String getOriginalCurrency() {
        return originalCurrency;
    }

    public void setOriginalCurrency(String originalCurrency) {
        this.originalCurrency = originalCurrency;
    }

    public String getWorkload() {
        return workload;
    }

    public void setWorkload(String workload) {
        this.workload = workload;
    }

    public String getRecordFingerprint() {
        return recordFingerprint;
    }

    public void setRecordFingerprint(String recordFingerprint) {
        this.recordFingerprint = recordFingerprint;
    }

    public String getExecutionId() {
        return executionId;
    }

    public void setExecutionId(String executionId) {
        this.executionId = executionId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
