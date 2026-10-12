package com.billradar.analysis;

import java.math.BigDecimal;
import java.util.Map;

public record WorkloadCostSummary(
        String workload,
        BigDecimal monthToDateSpend,
        BigDecimal budget,
        BigDecimal budgetUtilizationPercent,
        BigDecimal remainingBudget,
        BigDecimal dailyAverageSpend,
        BigDecimal projectedMonthEndSpend,
        BigDecimal projectedBudgetOverrunOrHeadroom,
        BigDecimal projectedOverrunAmount,
        BigDecimal projectedUtilizationPercent,
        BudgetStatus status,
        int recordCount,
        Map<String, BigDecimal> providerSpend) {
}
