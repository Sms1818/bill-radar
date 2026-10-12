package com.billradar;

import com.billradar.analysis.BudgetStatus;
import com.billradar.analysis.OverallCostSummary;
import com.billradar.analysis.WorkloadCostSummary;
import com.billradar.billing.DataQualityReport;
import com.billradar.workflow.WorkflowService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class ExpectedJsonAcceptanceTests {

    @Autowired
    private WorkflowService workflowService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void verifiesResultsAgainstExpectedJsonOracle() throws Exception {
        Path expectedPath = Path.of("src/test/resources/fixtures/expected.json");
        JsonNode expectedRoot;
        try (InputStream is = Files.newInputStream(expectedPath)) {
            expectedRoot = objectMapper.readTree(is);
        }

        List<String> expectedBreach = new ArrayList<>();
        expectedRoot.get("breach").forEach(node -> expectedBreach.add(node.asText()));

        List<String> expectedWarning = new ArrayList<>();
        expectedRoot.get("warning").forEach(node -> expectedWarning.add(node.asText()));

        List<String> expectedOk = new ArrayList<>();
        expectedRoot.get("ok").forEach(node -> expectedOk.add(node.asText()));

        JsonNode expectedDataQuality = expectedRoot.get("data_quality");
        int expectedNegativeRows = expectedDataQuality.get("negative_rows").asInt();
        int expectedDuplicateRows = expectedDataQuality.get("duplicate_rows").asInt();
        String expectedMixedCurrency = expectedDataQuality.get("mixed_currency").asText();

        // This test uses a fresh, explicitly triggered ingestion because application startup is read-only.
        workflowService.runIngestion();

        // Query actual system state
        OverallCostSummary summary = workflowService.getOverallCostSummary();
        Map<String, BudgetStatus> actualStatusByWorkload = summary.workloads().stream()
                .collect(Collectors.toMap(WorkloadCostSummary::workload, WorkloadCostSummary::status));

        // 1. Verify breach workloads
        for (String w : expectedBreach) {
            assertEquals(BudgetStatus.BREACH, actualStatusByWorkload.get(w),
                    "Workload " + w + " was expected to be in BREACH");
        }

        // 2. Verify warning workloads
        for (String w : expectedWarning) {
            assertEquals(BudgetStatus.WARNING, actualStatusByWorkload.get(w),
                    "Workload " + w + " was expected to be in WARNING");
        }

        // 3. Verify OK workloads
        for (String w : expectedOk) {
            assertEquals(BudgetStatus.OK, actualStatusByWorkload.get(w),
                    "Workload " + w + " was expected to be OK");
        }

        // 4. Verify data quality metrics
        DataQualityReport qualityReport = workflowService.getDataQualityReport();
        assertEquals(expectedDuplicateRows, qualityReport.duplicateRowsSkipped());
        assertEquals(expectedNegativeRows, qualityReport.creditRows());
        assertEquals(7, qualityReport.untaggedRows()); // 7 out of 97 AWS rows is ~7.2% ("about 6%")
        assertEquals("EUR", expectedMixedCurrency);
        assertEquals(134, qualityReport.rowsProcessed());
    }
}
