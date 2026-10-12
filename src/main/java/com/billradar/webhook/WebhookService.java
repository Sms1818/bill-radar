package com.billradar.webhook;

import com.billradar.analysis.BudgetAlert;
import com.billradar.analysis.BudgetStatus;
import com.billradar.analysis.OverallCostSummary;
import com.billradar.workflow.WorkflowService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Service
public class WebhookService {

    private final WorkflowService workflowService;
    private final String webhookUrl;

    public WebhookService(
            WorkflowService workflowService,
            @Value("${bill-radar.webhook.url:https://example.com/webhooks/budget-alerts}") String webhookUrl) {
        this.workflowService = workflowService;
        this.webhookUrl = webhookUrl;
    }

    public WebhookPreviewPayload generatePreviewPayload() {
        OverallCostSummary summary = workflowService.getOverallCostSummary();
        List<BudgetAlert> alerts = workflowService.getAlerts();

        long breachCount = alerts.stream()
                .filter(a -> a.status() == BudgetStatus.BREACH)
                .count();

        long warningCount = alerts.stream()
                .filter(a -> a.status() == BudgetStatus.WARNING)
                .count();

        String summaryText = String.format(
                "%d budget breach%s and %d approaching-budget warning%s detected for billing period %s",
                breachCount, breachCount == 1 ? "" : "es",
                warningCount, warningCount == 1 ? "" : "s",
                summary.billingMonth());

        return new WebhookPreviewPayload(
                "BUDGET_ALERT",
                "production",
                Instant.now(),
                summary.billingMonth(),
                summary.asOfDate(),
                summary.overallStatus().name(),
                summary.totalBudget(),
                summary.projectedMonthEndSpend(),
                summaryText,
                alerts.size(),
                (int) breachCount,
                (int) warningCount,
                alerts,
                webhookUrl,
                true);
    }
}
