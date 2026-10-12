package com.billradar.workflow;

import com.billradar.analysis.BudgetAlert;
import com.billradar.analysis.BudgetStatus;
import com.billradar.analysis.OverallCostSummary;
import com.billradar.persistence.BillingRecordRepository;
import com.billradar.persistence.WorkflowExecutionEntity;
import com.billradar.persistence.WorkflowExecutionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class WorkflowServiceIntegrationTests {

    @Autowired
    private WorkflowService workflowService;

    @Autowired
    private BillingRecordRepository billingRecordRepository;

    @Autowired
    private WorkflowExecutionRepository workflowExecutionRepository;

    @BeforeEach
    void cleanUp() {
        billingRecordRepository.deleteAll();
        workflowExecutionRepository.deleteAll();
    }

    @Test
    void ingestionIsIdempotentAndDoesNotDoubleCountSpend() {
        // Run 1: Initial ingestion
        IngestionRunSummary run1 = workflowService.runIngestion();

        assertEquals("SUCCESS", run1.status());
        assertEquals(134, run1.recordsProcessed());
        assertEquals(133, run1.recordsAccepted());
        assertEquals(1, run1.recordsSkipped());
        assertEquals(1, run1.duplicateRecords());
        assertEquals(7, run1.untaggedRecords());
        assertEquals(1, run1.creditRecords());
        assertEquals(new BigDecimal("11061.85"), run1.totalSpend());
        assertEquals(133, billingRecordRepository.count());

        // Run 2: Re-ingestion of the same dataset
        IngestionRunSummary run2 = workflowService.runIngestion();

        assertEquals("SUCCESS", run2.status());
        assertEquals(134, run2.recordsProcessed());
        assertEquals(0, run2.recordsAccepted(), "Repeated ingestion must not accept duplicate records");
        assertEquals(134, run2.recordsSkipped(), "All records in second run must be skipped as duplicates");
        assertEquals(133, billingRecordRepository.count(), "Database record count must remain 133");
        assertEquals(new BigDecimal("11061.85"), run2.totalSpend(), "Total spend must not double-count");

        // Verify execution history
        List<WorkflowExecutionEntity> history = workflowService.getExecutionHistory();
        assertEquals(2, history.size());
        assertEquals("SUCCESS", history.get(0).getStatus());
        assertEquals("SUCCESS", history.get(1).getStatus());

        // Verify alerts and summaries
        OverallCostSummary summary = workflowService.getOverallCostSummary();
        assertEquals(BudgetStatus.BREACH, summary.overallStatus());
        assertEquals(new BigDecimal("11061.85"), summary.monthToDateSpend());

        List<BudgetAlert> alerts = workflowService.getAlerts();
        assertEquals(3, alerts.size());
    }
}
