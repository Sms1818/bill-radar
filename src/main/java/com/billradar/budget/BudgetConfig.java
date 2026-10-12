package com.billradar.budget;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Collections;
import java.util.Map;

/**
 * Immutable configuration holding budget limits, reference date, and exchange rates.
 */
public record BudgetConfig(
        YearMonth month,
        LocalDate asOf,
        String currency,
        Map<String, BigDecimal> fxToUsd,
        Map<String, BigDecimal> budgets) {

    public BudgetConfig {
        fxToUsd = fxToUsd != null ? Collections.unmodifiableMap(fxToUsd) : Collections.emptyMap();
        budgets = budgets != null ? Collections.unmodifiableMap(budgets) : Collections.emptyMap();
    }

    public BigDecimal getBudgetForWorkload(String workload) {
        if (workload == null) {
            return BigDecimal.ZERO;
        }
        return budgets.getOrDefault(workload.toLowerCase(), BigDecimal.ZERO);
    }
}
