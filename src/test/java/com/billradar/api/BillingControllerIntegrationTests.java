package com.billradar.api;

import com.billradar.persistence.BillingRecordRepository;
import com.billradar.persistence.WorkflowExecutionRepository;
import com.billradar.workflow.WorkflowService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class BillingControllerIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private WorkflowService workflowService;

    @Autowired
    private BillingRecordRepository billingRecordRepository;

    @Autowired
    private WorkflowExecutionRepository workflowExecutionRepository;

    @BeforeEach
    void resetAndSeedFixtureData() {
        billingRecordRepository.deleteAll();
        workflowExecutionRepository.deleteAll();
        workflowService.runIngestion();
    }

    @Test
    void healthEndpointReturnsUp() throws Exception {
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.database").value("CONNECTED"))
                .andExpect(jsonPath("$.application").value("bill-radar"));
    }

    @Test
    void triggerIngestionReturnsCreatedSummary() throws Exception {
        mockMvc.perform(post("/api/ingest"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.executionId").isNotEmpty())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.recordsProcessed").value(134))
                .andExpect(jsonPath("$.costSummary").exists());
    }

    @Test
    void getCostSummaryReturnsExpectedTotals() throws Exception {
        mockMvc.perform(get("/api/costs/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.monthToDateSpend").value(11061.85))
                .andExpect(jsonPath("$.untaggedSpend").value(633.86))
                .andExpect(jsonPath("$.untaggedRecordCount").value(7))
                .andExpect(jsonPath("$.totalRecordCount").value(133))
                .andExpect(jsonPath("$.currency").value("EUR"))
                .andExpect(jsonPath("$.billingMonth").value("2026-10"))
                .andExpect(jsonPath("$.asOfDate").value("2026-10-12"))
                .andExpect(jsonPath("$.overallStatus").value("BREACH"))
                .andExpect(jsonPath("$.workloads", hasSize(5)));
    }

    @Test
    void getWorkloadsReturnsWorkloadDetails() throws Exception {
        mockMvc.perform(get("/api/costs/workloads"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(5)))
                .andExpect(jsonPath("$[?(@.workload == 'analytics')].status").value("BREACH"))
                .andExpect(jsonPath("$[?(@.workload == 'ml-training')].status").value("BREACH"))
                .andExpect(jsonPath("$[?(@.workload == 'internal-tools')].status").value("WARNING"))
                .andExpect(jsonPath("$[?(@.workload == 'checkout')].status").value("OK"))
                .andExpect(jsonPath("$[?(@.workload == 'search')].status").value("OK"));
    }

    @Test
    void getAlertsReturnsThreeAlerts() throws Exception {
        mockMvc.perform(get("/api/alerts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[?(@.workload == 'analytics')].severity").value("CRITICAL"))
                .andExpect(jsonPath("$[?(@.workload == 'ml-training')].severity").value("CRITICAL"))
                .andExpect(jsonPath("$[?(@.workload == 'internal-tools')].severity").value("WARNING"));
    }

    @Test
    void getDataQualityReturnsMetrics() throws Exception {
        mockMvc.perform(get("/api/data-quality"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rowsProcessed").value(134))
                .andExpect(jsonPath("$.untaggedRows").value(7))
                .andExpect(jsonPath("$.duplicateRowsSkipped").value(1))
                .andExpect(jsonPath("$.creditRows").value(1))
                .andExpect(jsonPath("$.currencyConversions").value(128));
    }

    @Test
    void getWorkflowHistoryReturnsExecutions() throws Exception {
        mockMvc.perform(get("/api/workflow/history"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", not(empty())))
                .andExpect(jsonPath("$[0].executionId").isNotEmpty())
                .andExpect(jsonPath("$[0].status").value("SUCCESS"));
    }

    @Test
    void previewWebhookReturnsSimulatedPayload() throws Exception {
        mockMvc.perform(get("/api/webhooks/preview"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.event").value("BUDGET_ALERT"))
                .andExpect(jsonPath("$.deliverySimulated").value(true))
                .andExpect(jsonPath("$.breachCount").value(2))
                .andExpect(jsonPath("$.warningCount").value(1))
                .andExpect(jsonPath("$.alerts", hasSize(3)));
    }

    @Test
    void servesStaticDashboardIndexHtml() throws Exception {
        mockMvc.perform(get("/index.html"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Bill Radar")));
    }
}
