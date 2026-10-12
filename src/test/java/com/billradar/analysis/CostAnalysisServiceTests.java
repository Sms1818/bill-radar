package com.billradar.analysis;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.billradar.billing.BillingCsvImporter;
import com.billradar.billing.BillingIngestionService;
import com.billradar.billing.BillingRecord;
import com.billradar.billing.BillingSource;
import com.billradar.billing.CurrencyConversionService;
import com.billradar.budget.BudgetConfig;
import com.billradar.budget.BudgetService;

import tools.jackson.databind.ObjectMapper;

class CostAnalysisServiceTests {

        private final CostAnalysisService costAnalysisService = new CostAnalysisService(85.0);
        private BudgetConfig sampleBudgetConfig;

        @BeforeEach
        void setUp() {
                BudgetService budgetService = new BudgetService(new ObjectMapper(), "sample-data/budgets.json");
                sampleBudgetConfig = budgetService.getBudgetConfig();
        }

        @Test
        void matchesExpectedJsonForSampleData() throws Exception {
                BillingCsvImporter importer = new BillingCsvImporter();
                List<BillingRecord> awsRecords = importer.importFile(Path.of("sample-data/aws_cur_style.csv"),
                                BillingSource.AWS);
                List<BillingRecord> azureRecords = importer.importFile(Path.of("sample-data/azure_cost_style.csv"),
                                BillingSource.AZURE);

                List<BillingRecord> allRecords = new ArrayList<>(awsRecords);
                allRecords.addAll(azureRecords);

                CurrencyConversionService converter = new CurrencyConversionService(sampleBudgetConfig.fxToUsd());
                BillingIngestionService ingestionService = new BillingIngestionService(converter);
                BillingIngestionService.IngestionResult ingestionResult = ingestionService.process(allRecords);

                CostAnalysisService.AnalysisResult result = costAnalysisService.analyze(
                                ingestionResult.records(),
                                sampleBudgetConfig);

                OverallCostSummary overall = result.overallSummary();
                assertNotNull(overall);
                assertEquals(new BigDecimal("11061.85"), overall.monthToDateSpend());
                assertEquals(new BigDecimal("633.86"), overall.untaggedSpend());
                assertEquals(7, overall.untaggedRecordCount());
                assertEquals(133, overall.totalRecordCount());

                // Check workload statuses matching expected.json:
                // breach: analytics, ml-training
                // warning: internal-tools
                // ok: checkout, search
                Map<String, WorkloadCostSummary> summaryMap = overall.workloads().stream()
                                .collect(java.util.stream.Collectors.toMap(WorkloadCostSummary::workload, w -> w));

                assertEquals(BudgetStatus.BREACH, summaryMap.get("analytics").status());
                assertEquals(BudgetStatus.BREACH, summaryMap.get("ml-training").status());
                assertEquals(BudgetStatus.WARNING, summaryMap.get("internal-tools").status());
                assertEquals(BudgetStatus.OK, summaryMap.get("checkout").status());
                assertEquals(BudgetStatus.OK, summaryMap.get("search").status());

                // Check generated alerts: 2 breaches (analytics, ml-training) + 1 warning
                // (internal-tools)
                List<BudgetAlert> alerts = result.alerts();
                assertEquals(3, alerts.size());

                List<String> breachAlertWorkloads = alerts.stream()
                                .filter(a -> a.status() == BudgetStatus.BREACH)
                                .map(BudgetAlert::workload)
                                .sorted()
                                .toList();
                assertEquals(List.of("analytics", "ml-training"), breachAlertWorkloads);

                List<String> warningAlertWorkloads = alerts.stream()
                                .filter(a -> a.status() == BudgetStatus.WARNING)
                                .map(BudgetAlert::workload)
                                .toList();
                assertEquals(List.of("internal-tools"), warningAlertWorkloads);
        }

        @Test
        void negativeCreditsReduceNetSpendCorrectly() {
                BudgetConfig config = new BudgetConfig(
                                YearMonth.of(2026, 10),
                                LocalDate.of(2026, 10, 10),
                                "EUR",
                                Map.of("EUR", BigDecimal.ONE),
                                Map.of("checkout", new BigDecimal("1000.00")));

                BillingRecord charge = new BillingRecord(
                                LocalDate.of(2026, 10, 1),
                                "AWS",
                                "EC2",
                                "eu-central-1",
                                new BigDecimal("200.00"),
                                "EUR",
                                "checkout");

                BillingRecord credit = new BillingRecord(
                                LocalDate.of(2026, 10, 2),
                                "AWS",
                                "EC2",
                                "eu-central-1",
                                new BigDecimal("-50.00"),
                                "EUR",
                                "checkout");

                CostAnalysisService.AnalysisResult result = costAnalysisService.analyze(List.of(charge, credit),
                                config);
                WorkloadCostSummary checkout = result.overallSummary().workloads().getFirst();

                assertEquals(new BigDecimal("150.00"), checkout.monthToDateSpend());
                assertEquals(new BigDecimal("850.00"), checkout.remainingBudget());
        }

        @Test
        void unbudgetedWorkloadsAreCapturedSafely() {
                BudgetConfig config = new BudgetConfig(
                                YearMonth.of(2026, 10),
                                LocalDate.of(2026, 10, 10),
                                "EUR",
                                Map.of("EUR", BigDecimal.ONE),
                                Map.of("checkout", new BigDecimal("1000.00")));

                BillingRecord unknownRecord = new BillingRecord(
                                LocalDate.of(2026, 10, 1),
                                "AWS",
                                "EC2",
                                "eu-central-1",
                                new BigDecimal("50.00"),
                                "EUR",
                                "experimental-ai");

                CostAnalysisService.AnalysisResult result = costAnalysisService.analyze(List.of(unknownRecord), config);
                assertTrue(result.overallSummary().workloads().stream()
                                .anyMatch(w -> "experimental-ai".equals(w.workload())));
        }
}
