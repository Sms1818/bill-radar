package com.billradar.analysis;

import java.math.BigDecimal;
import java.time.Instant;

public record BudgetAlert(
        String alertId,
        String workload,
        AlertSeverity severity,
        AlertType type,
        BudgetStatus status,
        BigDecimal currentSpend,
        BigDecimal budget,
        BigDecimal projectedSpend,
        BigDecimal projectedOverrunAmount,
        BigDecimal utilizationPercent,
        BigDecimal projectedUtilizationPercent,
        String message,
        Instant timestamp) {
}
