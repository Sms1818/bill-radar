package com.billradar.analysis;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

public record OverallCostSummary(
        String billingMonth,
        LocalDate asOfDate,
        int elapsedDays,
        int daysInMonth,
        String currency,
        BigDecimal monthToDateSpend,
        BigDecimal totalBudget,
        BigDecimal budgetUtilizationPercent,
        BigDecimal remainingBudget,
        BigDecimal dailyAverageSpend,
        BigDecimal projectedMonthEndSpend,
        BigDecimal projectedBudgetOverrunOrHeadroom,
        BigDecimal projectedUtilizationPercent,
        BigDecimal untaggedSpend,
        int untaggedRecordCount,
        int totalRecordCount,
        BudgetStatus overallStatus,
        List<WorkloadCostSummary> workloads) {
}
