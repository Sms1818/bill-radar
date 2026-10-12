# Bill Radar

> **Cloud Cost Visibility & Budget Risk MVP**
> *Synthetic-Data Proof of Concept & MVP*

Bill Radar is an automated cloud-cost visibility and budget-risk engine designed for multi-cloud environments. It imports synthetic billing exports modeled after AWS Cost & Usage Reports (CUR) and Azure Cost Management exports, normalizes their disparate schemas and currencies into a unified EUR data model, forecasts month-end spend via deterministic run-rate calculations, identifies budget overruns and warnings, and presents interactive insights through a real-time dashboard and REST API.

---

## 1. Architecture & Product Overview

```
                        +----------------------------+
                        |   sample-data/budgets.json |
                        | (As-of date, limits, FX)   |
                        +--------------+-------------+
                                       |
+--------------------------+           v
| AWS CUR Export (CSV)     +--> [ BillingCsvImporter ]
+--------------------------+           |
| Azure Cost Export (CSV)  +-->        v
+--------------------------+   [ CurrencyConversionService ] (USD -> EUR @ 1.12)
                                       |
                                       v
                               [ WorkflowService ] <==> [ PostgreSQL 17 ]
                              (Fingerprint Dedup)        (billing_records,
                                       |                  workflow_executions)
                                       v
                            [ CostAnalysisService ]
                         (Run-rate forecast & alerts)
                                       |
                   +-------------------+-------------------+
                   |                                       |
                   v                                       v
         [ REST API Controllers ]                [ WebhookService ]
         (/api/costs, /api/alerts, ...)     (Simulated Slack / Alert JSON)
                   |
                   v
      [ Frontend Dashboard UI ]
      (HTML5 / CSS3 / Vanilla JS)
```

### Core Architecture Highlights
- **Normalized Ingestion Pipeline**: Ingests raw AWS and Azure exports, strips provider-specific idiosyncrasies, extracts workload tags (including embedded JSON tags in Azure), and maps all records to a unified schema.
- **Idempotent Ingestion & Deduplication**: Employs SHA-256 fingerprinting across `(provider, usageDate, service, region, cost, currency, workload)` to ensure that re-ingesting datasets does not create duplicate records or double-count spend.
- **Financial Precision**: All calculations utilize `BigDecimal` with explicit scale and `RoundingMode.HALF_UP` to prevent floating-point inaccuracies.
- **Deterministic Forecasting**: Projects month-end spend based on the configured billing month and reference `as_of` date (`2026-10-12`).
- **Observability & History**: Full audit trail of workflow executions and data-quality metrics persisted directly into PostgreSQL.

---

## 2. Prerequisites

- **macOS** (Apple Silicon or Intel)
- **Java 21** or later (`java -version`)
- **Docker & Docker Compose** (for PostgreSQL 17)
- **Maven Wrapper** (`./mvnw` bundled in repository)
- **curl** or modern web browser (Safari, Chrome, Firefox)

---

## 3. Setup and Run Instructions (macOS)

### Step 1: Start PostgreSQL with Docker Compose
From the root of the project directory:

```bash
docker compose up -d postgres
```

Verify that PostgreSQL 17 is healthy and running on port `5433`:
```bash
docker compose ps
```

*Database connection defaults (configured in `compose.yaml` and `application.properties`):*
- Host: `localhost`
- Port: `5433`
- Database: `bill_radar`
- Username: `bill_radar`
- Password: `bill_radar_dev`

If you want to rehearse the **first-ingestion** demo from a clean local database, remove the local demo database volume before starting the app. This deletes only this project's local PostgreSQL demo data:

```bash
docker compose down -v
docker compose up -d postgres
```

Skip `down -v` if you want to keep the existing demo data; the next run will correctly demonstrate idempotent re-ingestion instead.

### Step 2: Build and Run Application
Run the Spring Boot application using the bundled Maven wrapper:

```bash
./mvnw spring-boot:run
```

The application will start on port `8080`.
The dashboard opens without silently running an import. Use **Run ingestion** to execute the bundled AWS-style and Azure-style CSV pipeline; a run-results dialog shows each processing stage and the saved/skipped record counts. Existing database records are safely skipped on repeat runs to prevent double-counting.

---

## 4. Dashboard Demonstration Steps

1. Open your web browser and navigate to:
   ```
   http://localhost:8080/
   ```
2. **Run the ingestion pipeline first**:
   - Click **Run ingestion**. The result dialog walks through reading the two source files, detecting duplicates, reconciling against the ledger, and recalculating budgets and forecasts.
   - On a clean database, expect `134` source rows processed, `133` new records saved, and `1` duplicate source row skipped.
   - If the database already contains this dataset, expect `0` new records and `133` existing records skipped, plus the one duplicate source row. That is the idempotency check, not a failed import.
3. **Review High-Level KPIs**:
   - Review month-to-date spend, projected month-end spend, total EUR budget, and overall status. These figures use the checked-in synthetic fixtures and configured EUR/USD rate of `1.12`.
4. **Inspect Active Budget Alerts**:
   - Review the `analytics` and `ml-training` breach alerts and the `internal-tools` warning.
5. **Examine Workload Spend**:
   - Compare spend vs budget across `checkout`, `search`, `analytics`, `ml-training`, and `internal-tools`.
6. **Inspect Data Quality**:
   - Rows processed: `134` (AWS-style: 97, Azure-style: 37)
   - Accepted records: `133`
   - Duplicate source rows: `1`
   - Untagged records: `7` (~7.2%)
   - Negative credits: `1`
   - USD-to-EUR conversions on the initial run: `128` (rate: `1.12`)
7. **Preview Webhook Payload**:
   - Click **Webhook payload** to inspect the JSON prepared for an alert destination. No external webhook is sent.

---

## 5. REST API Documentation & Examples

All endpoints return JSON responses. Stack traces are never leaked in error scenarios.

### Health Check
```bash
curl -s http://localhost:8080/api/health
```
```json
{
  "status": "UP",
  "database": "CONNECTED",
  "application": "bill-radar",
  "timestamp": "2026-10-12T09:47:50.129532Z"
}
```

### Trigger Ingestion
```bash
curl -s -X POST http://localhost:8080/api/ingest
```
```json
{
  "executionId": "EXEC-1791798314817-9EB283",
  "status": "SUCCESS",
  "recordsProcessed": 134,
  "recordsAccepted": 0,
  "recordsSkipped": 134,
  "untaggedRecords": 7,
  "duplicateRecords": 1,
  "creditRecords": 1,
  "currencyConversions": 128,
  "totalSpend": 11471.54,
  "alertsGenerated": 3
}
```

### Overall Cost Summary
```bash
curl -s http://localhost:8080/api/costs/summary
```
```json
{
  "billingMonth": "2026-10",
  "asOfDate": "2026-10-12",
  "elapsedDays": 12,
  "daysInMonth": 31,
  "currency": "EUR",
  "monthToDateSpend": 11471.54,
  "totalBudget": 31712.96,
  "budgetUtilizationPercent": 36.17,
  "remainingBudget": 20241.42,
  "dailyAverageSpend": 955.96,
  "projectedMonthEndSpend": 29634.81,
  "projectedBudgetOverrunOrHeadroom": 2078.15,
  "projectedUtilizationPercent": 93.45,
  "untaggedSpend": 657.34,
  "untaggedRecordCount": 7,
  "totalRecordCount": 133,
  "overallStatus": "BREACH"
}
```

### Workload Breakdown & Forecasts
```bash
curl -s http://localhost:8080/api/costs/workloads
```
```json
[
  {
    "workload": "analytics",
    "monthToDateSpend": 1975.32,
    "budget": 4629.63,
    "budgetUtilizationPercent": 42.67,
    "remainingBudget": 2654.31,
    "dailyAverageSpend": 164.61,
    "projectedMonthEndSpend": 5102.91,
    "projectedBudgetOverrunOrHeadroom": -473.28,
    "projectedOverrunAmount": 473.28,
    "projectedUtilizationPercent": 110.22,
    "status": "BREACH",
    "recordCount": 24,
    "providerSpend": { "AWS": 1975.32 }
  },
  {
    "workload": "internal-tools",
    "monthToDateSpend": 739.09,
    "budget": 2083.33,
    "budgetUtilizationPercent": 35.48,
    "remainingBudget": 1344.24,
    "dailyAverageSpend": 61.59,
    "projectedMonthEndSpend": 1909.32,
    "projectedBudgetOverrunOrHeadroom": 174.01,
    "projectedOverrunAmount": 0.00,
    "projectedUtilizationPercent": 91.65,
    "status": "WARNING",
    "recordCount": 23,
    "providerSpend": { "AWS": 739.09 }
  }
]
```

### Active Budget Alerts
```bash
curl -s http://localhost:8080/api/alerts
```

### Data Quality Statistics
```bash
curl -s http://localhost:8080/api/data-quality
```
```json
{
  "rowsProcessed": 134,
  "untaggedRows": 7,
  "duplicateRowsSkipped": 1,
  "invalidRows": 0,
  "creditRows": 1,
  "currencyConversions": 128
}
```

### Workflow Execution History
```bash
curl -s http://localhost:8080/api/workflow/history
```

### Webhook Preview Payload
```bash
curl -s http://localhost:8080/api/webhooks/preview
```

---

## 6. Financial & Analytical Assumptions

### A. Synthetic Data Disclaimer
All billing records, cloud resource names, and exchange rates used in this project are **synthetic test fixtures**. This project does not connect to live AWS or Azure cloud accounts, nor does it query external banking APIs.

### B. Currency Normalization
- All internal budgets, stored costs, and forecast totals use **EUR (€)**.
- Exchange rates are configured in `sample-data/budgets.json`.
- The configured fixture defines `EUR: 1.12` (EUR to USD). Therefore, USD charges are converted to EUR by dividing by `1.12`:
  $$\text{cost}_{\text{EUR}} = \frac{\text{cost}_{\text{USD}}}{1.12}$$
- EUR amounts remain untouched.
- Unsupported currencies (e.g., GBP, JPY) or negative exchange rates are rejected explicitly.

### C. Run-Rate Forecasting Model
Forecasting uses a transparent, deterministic run-rate projection formula:
$$\text{projectedMonthEndSpend} = \frac{\text{monthToDateSpend}}{\text{elapsedDays}} \times \text{daysInMonth}$$

- Billing Month: `2026-10` (October has `31` days).
- Reference Date (`as_of`): `2026-10-12` (`12` elapsed days).
- Deterministic behavior: If `elapsedDays == 0`, projected spend defaults safely to month-to-date spend without division by zero.
- All monetary arithmetic uses `BigDecimal` with rounding mode `HALF_UP` to two decimal places.

### D. Alert Thresholds
Workload budget statuses and alerts are computed deterministically:
1. **Actual Breach (`ACTUAL_OVERRUN`, CRITICAL)**:
   Triggered if current month-to-date spend exceeds allocated budget:
   $$\text{monthToDateSpend} > \text{budget}$$
2. **Projected Breach (`PROJECTED_OVERRUN`, CRITICAL)**:
   Triggered if projected month-end spend exceeds allocated budget:
   $$\text{projectedMonthEndSpend} > \text{budget} \quad (\text{projected utilization} \ge 100\%)$$
3. **Approaching Budget Warning (`APPROACHING_BUDGET`, WARNING)**:
   Triggered if projected utilization is between 85% and 100%:
   $$85.0\% \le \text{projectedUtilization} < 100.0\%$$
4. **OK**:
   Workload projected spend is under 85.0% of allocated budget.

---

## 7. Data Quality & Deduplication Strategy

- **Exact Duplicate Handling**: The sample Azure export contains an exact duplicate row on Day 9 (`ml-training` Virtual Machines). This row is detected via natural record fingerprinting and discarded during ingestion.
- **Negative-Cost Credits**: AWS CUR export contains an Athena credit row (`-$150.00`). Credits are preserved, normalized to `-€138.89`, reduce the net month-to-date spend, and are explicitly tracked in the data-quality audit report.
- **Untagged Records**: 7 AWS rows lack workload tags. Untagged rows are preserved, recorded in data-quality metrics, and added to the overall total spend.
- **Database Idempotency**: Billing records stored in table `billing_records` carry a unique constraint on `record_fingerprint` (SHA-256 hash). If ingestion is triggered repeatedly, existing records are detected and skipped, preventing double-counting of spend.

---

## 8. Running Automated Tests

Run the full automated test suite using Maven:

```bash
./mvnw test
```

### Test Coverage Highlights
- `BillingCsvImporterTests`: Ingestion and schema parsing of AWS CUR and Azure Cost CSV exports.
- `AzureTagExtractionTests`: Robust JSON tag parsing and tolerance to empty or malformed tags.
- `CurrencyConversionServiceTests`: USD to EUR conversion, EUR preservation, negative credits, and currency rejection.
- `BudgetServiceTests`: JSON budget configuration parsing, rate validation, and constraint checking.
- `CostAnalysisServiceTests`: Run-rate forecast arithmetic, budget utilization percentages, alert generation, and credit handling.
- `ExpectedJsonAcceptanceTests`: Acceptance oracle test asserting exact alignment with `expected.json` (breaches: `analytics`, `ml-training`; warning: `internal-tools`; ok: `checkout`, `search`).
- `WorkflowServiceIntegrationTests`: Database persistence with PostgreSQL, intra-batch deduplication, idempotent re-ingestion, and execution history.
- `BillingControllerIntegrationTests`: MockMvc integration tests for all 8 REST endpoints and static dashboard index.html.
- `FailureHandlingTests`: Validates rejection of invalid configurations and ensures no stack traces leak in API error responses.

---

## 9. Known Limitations & Future Improvements

1. **Static Exchange Rates**: FX rates are configured via fixture JSON. A production enhancement would integrate ECB or Open Exchange Rates with daily historical fx caching.
2. **Advanced Anomaly Forecasting**: The current forecast utilizes a simple, transparent run-rate model. Future iterations could incorporate moving averages (e.g., 7-day weighted mean) or seasonal ARIMA models.
3. **Real Webhook Delivery**: Webhook preview generation is supported; integrating active webhook dispatch with exponential backoff retries and HMAC signatures would be an appropriate next step.
4. **Authentication**: The current MVP is designed for internal demonstration without authentication; production environments would require OAuth2 / OIDC token protection.


### Dashboard navigation

The dashboard uses focused navigation views (Overview, Budget watch, Workloads, Data quality, and Run history) instead of requiring the user to scroll through one long report. Clicking **Run ingestion** opens a run report showing source rows processed, new records saved, duplicate source rows, previously stored records skipped, currency-conversion count, credits, untagged records, and budget alerts generated.
