package com.billradar.workflow;

import com.billradar.analysis.BudgetAlert;
import com.billradar.analysis.OverallCostSummary;
import com.billradar.billing.DataQualityReport;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record IngestionRunSummary(
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
        DataQualityReport dataQualityReport,
        OverallCostSummary costSummary,
        List<BudgetAlert> alerts,
        String errorDetails) {
}
