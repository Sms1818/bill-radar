package com.billradar.api;

import com.billradar.analysis.BudgetAlert;
import com.billradar.analysis.OverallCostSummary;
import com.billradar.analysis.WorkloadCostSummary;
import com.billradar.billing.DataQualityReport;
import com.billradar.persistence.WorkflowExecutionEntity;
import com.billradar.webhook.WebhookPreviewPayload;
import com.billradar.webhook.WebhookService;
import com.billradar.workflow.IngestionRunSummary;
import com.billradar.workflow.WorkflowService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
public class BillingController {

    private final WorkflowService workflowService;
    private final WebhookService webhookService;

    public BillingController(WorkflowService workflowService, WebhookService webhookService) {
        this.workflowService = workflowService;
        this.webhookService = webhookService;
    }

    /**
     * Trigger ingestion of bundled billing export files.
     */
    @PostMapping(path = {"/ingestion/run", "/ingest"})
    public ResponseEntity<IngestionRunSummary> triggerIngestion() {
        IngestionRunSummary summary = workflowService.runIngestion();
        return ResponseEntity.status(HttpStatus.CREATED).body(summary);
    }

    /**
     * Retrieve overall cost summary, including month-to-date spend, forecast, and status.
     */
    @GetMapping("/costs/summary")
    public ResponseEntity<OverallCostSummary> getCostSummary() {
        return ResponseEntity.ok(workflowService.getOverallCostSummary());
    }

    /**
     * Retrieve workload-level spend, budget, and forecast analysis.
     */
    @GetMapping("/costs/workloads")
    public ResponseEntity<List<WorkloadCostSummary>> getWorkloadSummaries() {
        return ResponseEntity.ok(workflowService.getWorkloadCostSummaries());
    }

    /**
     * Retrieve active budget alerts.
     */
    @GetMapping("/alerts")
    public ResponseEntity<List<BudgetAlert>> getBudgetAlerts() {
        return ResponseEntity.ok(workflowService.getAlerts());
    }

    /**
     * Retrieve data quality metrics and row ingestion statistics.
     */
    @GetMapping("/data-quality")
    public ResponseEntity<DataQualityReport> getDataQualityReport() {
        return ResponseEntity.ok(workflowService.getDataQualityReport());
    }

    /**
     * Retrieve execution history of billing workflow runs.
     */
    @GetMapping(path = {"/workflow/history", "/workflows"})
    public ResponseEntity<List<WorkflowExecutionEntity>> getWorkflowHistory() {
        return ResponseEntity.ok(workflowService.getExecutionHistory());
    }

    /**
     * Preview simulated JSON payload for budget alert webhooks.
     */
    @GetMapping("/webhooks/preview")
    public ResponseEntity<WebhookPreviewPayload> previewWebhookPayload() {
        return ResponseEntity.ok(webhookService.generatePreviewPayload());
    }
}
