package com.billradar.webhook;

import com.billradar.analysis.BudgetAlert;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record WebhookPreviewPayload(
        String event,
        String environment,
        Instant generatedAt,
        String billingMonth,
        LocalDate asOfDate,
        String overallStatus,
        BigDecimal totalBudgetEur,
        BigDecimal totalProjectedSpendEur,
        String summary,
        int alertCount,
        int breachCount,
        int warningCount,
        List<BudgetAlert> alerts,
        String webhookTargetUrl,
        boolean deliverySimulated) {
}
