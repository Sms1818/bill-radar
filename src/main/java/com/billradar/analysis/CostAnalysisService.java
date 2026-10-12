package com.billradar.analysis;

import com.billradar.billing.BillingRecord;
import com.billradar.budget.BudgetConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;

@Service
public class CostAnalysisService {

    private final BigDecimal warningThresholdPercent;

    @org.springframework.beans.factory.annotation.Autowired
    public CostAnalysisService(
            @Value("${bill-radar.alert.warning-threshold-percent:85.0}") double warningThresholdPercent) {
        this.warningThresholdPercent = BigDecimal.valueOf(warningThresholdPercent).setScale(2, RoundingMode.HALF_UP);
    }

    public CostAnalysisService(BigDecimal warningThresholdPercent) {
        this.warningThresholdPercent = warningThresholdPercent.setScale(2, RoundingMode.HALF_UP);
    }

    public AnalysisResult analyze(List<BillingRecord> records, BudgetConfig budgetConfig) {
        Objects.requireNonNull(records, "records cannot be null");
        Objects.requireNonNull(budgetConfig, "budgetConfig cannot be null");

        LocalDate asOfDate = budgetConfig.asOf();
        YearMonth billingMonth = budgetConfig.month();
        int daysInMonth = billingMonth.lengthOfMonth();
        int elapsedDays = Math.max(0, asOfDate.getDayOfMonth());

        // Group records by normalized workload
        Map<String, List<BillingRecord>> recordsByWorkload = new HashMap<>();
        List<BillingRecord> untaggedRecords = new ArrayList<>();
        BigDecimal totalSpend = BigDecimal.ZERO;
        BigDecimal untaggedSpend = BigDecimal.ZERO;

        for (BillingRecord record : records) {
            BigDecimal cost = record.cost() != null ? record.cost() : BigDecimal.ZERO;
            totalSpend = totalSpend.add(cost);

            if (record.workload() == null || record.workload().isBlank()) {
                untaggedRecords.add(record);
                untaggedSpend = untaggedSpend.add(cost);
            } else {
                String normalizedWorkload = record.workload().trim().toLowerCase();
                recordsByWorkload.computeIfAbsent(normalizedWorkload, k -> new ArrayList<>()).add(record);
            }
        }

        totalSpend = totalSpend.setScale(2, RoundingMode.HALF_UP);
        untaggedSpend = untaggedSpend.setScale(2, RoundingMode.HALF_UP);

        // Collect all distinct workloads (both configured and discovered from records)
        Set<String> allWorkloads = new LinkedHashSet<>(budgetConfig.budgets().keySet());
        allWorkloads.addAll(recordsByWorkload.keySet());

        List<WorkloadCostSummary> workloadSummaries = new ArrayList<>();
        List<BudgetAlert> alerts = new ArrayList<>();
        BigDecimal totalBudget = BigDecimal.ZERO;

        for (String workload : allWorkloads) {
            List<BillingRecord> workloadRecords = recordsByWorkload.getOrDefault(workload, Collections.emptyList());
            BigDecimal budget = budgetConfig.getBudgetForWorkload(workload).setScale(2, RoundingMode.HALF_UP);
            totalBudget = totalBudget.add(budget);

            BigDecimal mtdSpend = BigDecimal.ZERO;
            Map<String, BigDecimal> providerSpend = new HashMap<>();

            for (BillingRecord r : workloadRecords) {
                BigDecimal rCost = r.cost() != null ? r.cost() : BigDecimal.ZERO;
                mtdSpend = mtdSpend.add(rCost);
                String provider = r.provider() != null ? r.provider() : "UNKNOWN";
                providerSpend.merge(provider, rCost, BigDecimal::add);
            }
            mtdSpend = mtdSpend.setScale(2, RoundingMode.HALF_UP);
            providerSpend.replaceAll((k, v) -> v.setScale(2, RoundingMode.HALF_UP));

            // Forecast calculations using deterministic run-rate
            BigDecimal dailyAvg;
            BigDecimal projectedMonthEnd;
            if (elapsedDays > 0) {
                dailyAvg = mtdSpend.divide(BigDecimal.valueOf(elapsedDays), 2, RoundingMode.HALF_UP);
                projectedMonthEnd = mtdSpend.multiply(BigDecimal.valueOf(daysInMonth))
                        .divide(BigDecimal.valueOf(elapsedDays), 2, RoundingMode.HALF_UP);
            } else {
                dailyAvg = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
                projectedMonthEnd = mtdSpend;
            }

            BigDecimal remainingBudget = budget.subtract(mtdSpend).setScale(2, RoundingMode.HALF_UP);
            BigDecimal overrunOrHeadroom = budget.subtract(projectedMonthEnd).setScale(2, RoundingMode.HALF_UP);
            BigDecimal projectedOverrunAmount = projectedMonthEnd.compareTo(budget) > 0
                    ? projectedMonthEnd.subtract(budget).setScale(2, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

            BigDecimal budgetUtilPercent;
            BigDecimal projectedUtilPercent;
            if (budget.compareTo(BigDecimal.ZERO) > 0) {
                budgetUtilPercent = mtdSpend.multiply(BigDecimal.valueOf(100))
                        .divide(budget, 2, RoundingMode.HALF_UP);
                projectedUtilPercent = projectedMonthEnd.multiply(BigDecimal.valueOf(100))
                        .divide(budget, 2, RoundingMode.HALF_UP);
            } else {
                budgetUtilPercent = mtdSpend.compareTo(BigDecimal.ZERO) > 0
                        ? BigDecimal.valueOf(100.00).setScale(2, RoundingMode.HALF_UP)
                        : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
                projectedUtilPercent = projectedMonthEnd.compareTo(BigDecimal.ZERO) > 0
                        ? BigDecimal.valueOf(100.00).setScale(2, RoundingMode.HALF_UP)
                        : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
            }

            // Determine status & generate alerts
            BudgetStatus status;
            if (budget.compareTo(BigDecimal.ZERO) > 0 && mtdSpend.compareTo(budget) > 0) {
                status = BudgetStatus.BREACH;
                String msg = String.format(
                        "Workload '%s' month-to-date spend (€%s) has breached its allocated budget (€%s) by €%s (%s%% utilization).",
                        workload, mtdSpend, budget, mtdSpend.subtract(budget).setScale(2, RoundingMode.HALF_UP), budgetUtilPercent);
                alerts.add(new BudgetAlert(
                        generateAlertId(workload, "ACTUAL"),
                        workload,
                        AlertSeverity.CRITICAL,
                        AlertType.ACTUAL_OVERRUN,
                        BudgetStatus.BREACH,
                        mtdSpend,
                        budget,
                        projectedMonthEnd,
                        projectedOverrunAmount,
                        budgetUtilPercent,
                        projectedUtilPercent,
                        msg,
                        Instant.now()));
            } else if (budget.compareTo(BigDecimal.ZERO) > 0 && projectedMonthEnd.compareTo(budget) > 0) {
                status = BudgetStatus.BREACH;
                String msg = String.format(
                        "Workload '%s' projected month-end spend (€%s) breaches budget (€%s) by €%s (%s%% projected utilization).",
                        workload, projectedMonthEnd, budget, projectedOverrunAmount, projectedUtilPercent);
                alerts.add(new BudgetAlert(
                        generateAlertId(workload, "PROJECTED"),
                        workload,
                        AlertSeverity.CRITICAL,
                        AlertType.PROJECTED_OVERRUN,
                        BudgetStatus.BREACH,
                        mtdSpend,
                        budget,
                        projectedMonthEnd,
                        projectedOverrunAmount,
                        budgetUtilPercent,
                        projectedUtilPercent,
                        msg,
                        Instant.now()));
            } else if (budget.compareTo(BigDecimal.ZERO) > 0 && projectedUtilPercent.compareTo(warningThresholdPercent) >= 0) {
                status = BudgetStatus.WARNING;
                String msg = String.format(
                        "Workload '%s' projected month-end spend (€%s) is approaching budget (€%s) at %s%% projected utilization (threshold: %s%%).",
                        workload, projectedMonthEnd, budget, projectedUtilPercent, warningThresholdPercent);
                alerts.add(new BudgetAlert(
                        generateAlertId(workload, "WARNING"),
                        workload,
                        AlertSeverity.WARNING,
                        AlertType.APPROACHING_BUDGET,
                        BudgetStatus.WARNING,
                        mtdSpend,
                        budget,
                        projectedMonthEnd,
                        projectedOverrunAmount,
                        budgetUtilPercent,
                        projectedUtilPercent,
                        msg,
                        Instant.now()));
            } else {
                status = BudgetStatus.OK;
            }

            workloadSummaries.add(new WorkloadCostSummary(
                    workload,
                    mtdSpend,
                    budget,
                    budgetUtilPercent,
                    remainingBudget,
                    dailyAvg,
                    projectedMonthEnd,
                    overrunOrHeadroom,
                    projectedOverrunAmount,
                    projectedUtilPercent,
                    status,
                    workloadRecords.size(),
                    providerSpend));
        }

        // Overall summary calculations
        totalBudget = totalBudget.setScale(2, RoundingMode.HALF_UP);
        BigDecimal totalDailyAvg = elapsedDays > 0
                ? totalSpend.divide(BigDecimal.valueOf(elapsedDays), 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        BigDecimal totalProjected = elapsedDays > 0
                ? totalSpend.multiply(BigDecimal.valueOf(daysInMonth)).divide(BigDecimal.valueOf(elapsedDays), 2, RoundingMode.HALF_UP)
                : totalSpend;

        BigDecimal totalRemaining = totalBudget.subtract(totalSpend).setScale(2, RoundingMode.HALF_UP);
        BigDecimal totalOverrunOrHeadroom = totalBudget.subtract(totalProjected).setScale(2, RoundingMode.HALF_UP);

        BigDecimal totalBudgetUtilPercent = totalBudget.compareTo(BigDecimal.ZERO) > 0
                ? totalSpend.multiply(BigDecimal.valueOf(100)).divide(totalBudget, 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

        BigDecimal totalProjectedUtilPercent = totalBudget.compareTo(BigDecimal.ZERO) > 0
                ? totalProjected.multiply(BigDecimal.valueOf(100)).divide(totalBudget, 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

        BudgetStatus overallStatus = BudgetStatus.OK;
        if (workloadSummaries.stream().anyMatch(w -> w.status() == BudgetStatus.BREACH)) {
            overallStatus = BudgetStatus.BREACH;
        } else if (workloadSummaries.stream().anyMatch(w -> w.status() == BudgetStatus.WARNING)) {
            overallStatus = BudgetStatus.WARNING;
        }

        OverallCostSummary overallSummary = new OverallCostSummary(
                billingMonth.toString(),
                asOfDate,
                elapsedDays,
                daysInMonth,
                budgetConfig.currency(),
                totalSpend,
                totalBudget,
                totalBudgetUtilPercent,
                totalRemaining,
                totalDailyAvg,
                totalProjected,
                totalOverrunOrHeadroom,
                totalProjectedUtilPercent,
                untaggedSpend,
                untaggedRecords.size(),
                records.size(),
                overallStatus,
                Collections.unmodifiableList(workloadSummaries));

        return new AnalysisResult(overallSummary, Collections.unmodifiableList(alerts));
    }

    private String generateAlertId(String workload, String type) {
        return "ALERT-" + workload.toUpperCase().replaceAll("[^A-Z0-9]", "-") + "-" + type;
    }

    public record AnalysisResult(
            OverallCostSummary overallSummary,
            List<BudgetAlert> alerts) {
    }
}
