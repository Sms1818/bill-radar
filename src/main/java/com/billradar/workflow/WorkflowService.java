package com.billradar.workflow;

import com.billradar.analysis.*;
import com.billradar.billing.*;
import com.billradar.budget.BudgetConfig;
import com.billradar.budget.BudgetService;
import com.billradar.persistence.BillingRecordEntity;
import com.billradar.persistence.BillingRecordRepository;
import com.billradar.persistence.WorkflowExecutionEntity;
import com.billradar.persistence.WorkflowExecutionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.*;

@Service
public class WorkflowService {

    private static final Logger log = LoggerFactory.getLogger(WorkflowService.class);

    private final BillingCsvImporter csvImporter;
    private final CurrencyConversionService currencyConverter;
    private final BudgetService budgetService;
    private final CostAnalysisService costAnalysisService;
    private final BillingRecordRepository billingRecordRepository;
    private final WorkflowExecutionRepository workflowExecutionRepository;

    private final String awsCsvPath;
    private final String azureCsvPath;

    public WorkflowService(
            BillingCsvImporter csvImporter,
            CurrencyConversionService currencyConverter,
            BudgetService budgetService,
            CostAnalysisService costAnalysisService,
            BillingRecordRepository billingRecordRepository,
            WorkflowExecutionRepository workflowExecutionRepository,
            @Value("${bill-radar.aws-csv-file:sample-data/aws_cur_style.csv}") String awsCsvPath,
            @Value("${bill-radar.azure-csv-file:sample-data/azure_cost_style.csv}") String azureCsvPath) {
        this.csvImporter = csvImporter;
        this.currencyConverter = currencyConverter;
        this.budgetService = budgetService;
        this.costAnalysisService = costAnalysisService;
        this.billingRecordRepository = billingRecordRepository;
        this.workflowExecutionRepository = workflowExecutionRepository;
        this.awsCsvPath = awsCsvPath;
        this.azureCsvPath = azureCsvPath;
    }

    @Transactional
    public IngestionRunSummary runIngestion() {
        String executionId = "EXEC-" + Instant.now().toEpochMilli() + "-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        Instant startTime = Instant.now();
        log.info("Starting ingestion workflow run {}", executionId);

        try {
            Path resolvedAwsPath = resolveFilePath(awsCsvPath);
            Path resolvedAzurePath = resolveFilePath(azureCsvPath);

            List<BillingRecord> rawAws = csvImporter.importFile(resolvedAwsPath, BillingSource.AWS);
            List<BillingRecord> rawAzure = csvImporter.importFile(resolvedAzurePath, BillingSource.AZURE);

            List<BillingRecord> combinedRaw = new ArrayList<>(rawAws.size() + rawAzure.size());
            combinedRaw.addAll(rawAws);
            combinedRaw.addAll(rawAzure);

            int totalProcessed = combinedRaw.size();
            int untaggedCount = 0;
            int creditCount = 0;
            int currencyConversions = 0;
            int intraBatchDuplicates = 0;

            Set<String> batchSeenFingerprints = new HashSet<>();
            List<RawWithFingerprint> batchUniqueRecords = new ArrayList<>();

            for (BillingRecord raw : combinedRaw) {
                String fp = FingerprintUtil.computeFingerprint(
                        raw.usageDate(),
                        raw.provider(),
                        raw.service(),
                        raw.region(),
                        raw.cost(),
                        raw.currency(),
                        raw.workload());

                if (!batchSeenFingerprints.add(fp)) {
                    intraBatchDuplicates++;
                    continue;
                }

                if (raw.workload() == null || raw.workload().isBlank()) {
                    untaggedCount++;
                }
                if (raw.cost() != null && raw.cost().signum() < 0) {
                    creditCount++;
                }
                batchUniqueRecords.add(new RawWithFingerprint(raw, fp));
            }

            // Query existing fingerprints in database for idempotent deduplication
            Set<String> existingDbFingerprints = billingRecordRepository.findAllFingerprints();

            List<BillingRecordEntity> newEntitiesToSave = new ArrayList<>();
            int alreadyInDbCount = 0;

            for (RawWithFingerprint item : batchUniqueRecords) {
                if (existingDbFingerprints.contains(item.fingerprint())) {
                    alreadyInDbCount++;
                    continue;
                }

                BigDecimal eurCost = currencyConverter.convertToEur(item.record().cost(), item.record().currency());
                if (!"EUR".equalsIgnoreCase(item.record().currency())) {
                    currencyConversions++;
                }

                BillingRecordEntity entity = new BillingRecordEntity(
                        item.record().usageDate(),
                        item.record().provider(),
                        item.record().service(),
                        item.record().region(),
                        eurCost,
                        "EUR",
                        item.record().cost(),
                        item.record().currency(),
                        item.record().workload(),
                        item.fingerprint(),
                        executionId);

                newEntitiesToSave.add(entity);
            }

            if (!newEntitiesToSave.isEmpty()) {
                billingRecordRepository.saveAll(newEntitiesToSave);
                log.info("Persisted {} new billing records for execution {}", newEntitiesToSave.size(), executionId);
            } else {
                log.info("No new records to persist (all {} records already present in database)", batchUniqueRecords.size());
            }

            int recordsAccepted = newEntitiesToSave.size();
            int totalSkipped = intraBatchDuplicates + alreadyInDbCount;

            // Perform cost analysis using all records in the database
            BudgetConfig budgetConfig = budgetService.getBudgetConfig();
            List<BillingRecordEntity> allEntities = billingRecordRepository.findAllByOrderByUsageDateAsc();
            List<BillingRecord> normalizedRecords = allEntities.stream()
                    .map(e -> new BillingRecord(
                            e.getUsageDate(),
                            e.getProvider(),
                            e.getService(),
                            e.getRegion(),
                            e.getCost(),
                            e.getCurrency(),
                            e.getWorkload()))
                    .toList();

            CostAnalysisService.AnalysisResult analysisResult = costAnalysisService.analyze(normalizedRecords, budgetConfig);

            Instant completionTime = Instant.now();
            BigDecimal totalSpend = analysisResult.overallSummary().monthToDateSpend();
            int alertCount = analysisResult.alerts().size();

            WorkflowExecutionEntity executionEntity = new WorkflowExecutionEntity(
                    executionId,
                    startTime,
                    completionTime,
                    "SUCCESS",
                    totalProcessed,
                    recordsAccepted,
                    totalSkipped,
                    untaggedCount,
                    intraBatchDuplicates,
                    creditCount,
                    currencyConversions,
                    totalSpend,
                    alertCount,
                    null);

            workflowExecutionRepository.save(executionEntity);

            DataQualityReport qualityReport = new DataQualityReport(
                    totalProcessed,
                    untaggedCount,
                    intraBatchDuplicates,
                    0,
                    creditCount,
                    currencyConversions);

            return new IngestionRunSummary(
                    executionId,
                    startTime,
                    completionTime,
                    "SUCCESS",
                    totalProcessed,
                    recordsAccepted,
                    totalSkipped,
                    untaggedCount,
                    intraBatchDuplicates,
                    creditCount,
                    currencyConversions,
                    totalSpend,
                    alertCount,
                    qualityReport,
                    analysisResult.overallSummary(),
                    analysisResult.alerts(),
                    null);

        } catch (Exception e) {
            log.error("Ingestion run {} failed", executionId, e);
            Instant completionTime = Instant.now();
            WorkflowExecutionEntity failedEntity = new WorkflowExecutionEntity(
                    executionId,
                    startTime,
                    completionTime,
                    "FAILED",
                    0,
                    0,
                    0,
                    0,
                    0,
                    0,
                    0,
                    BigDecimal.ZERO,
                    0,
                    e.getMessage());
            workflowExecutionRepository.save(failedEntity);
            throw new RuntimeException("Ingestion workflow failed: " + e.getMessage(), e);
        }
    }

    public List<WorkflowExecutionEntity> getExecutionHistory() {
        return workflowExecutionRepository.findAllByOrderByStartTimeDesc();
    }

    public Optional<WorkflowExecutionEntity> getLatestExecution() {
        return workflowExecutionRepository.findFirstByOrderByStartTimeDesc();
    }

    public OverallCostSummary getOverallCostSummary() {
        BudgetConfig budgetConfig = budgetService.getBudgetConfig();
        List<BillingRecord> records = getNormalizedBillingRecords();
        return costAnalysisService.analyze(records, budgetConfig).overallSummary();
    }

    public List<WorkloadCostSummary> getWorkloadCostSummaries() {
        return getOverallCostSummary().workloads();
    }

    public List<BudgetAlert> getAlerts() {
        BudgetConfig budgetConfig = budgetService.getBudgetConfig();
        List<BillingRecord> records = getNormalizedBillingRecords();
        return costAnalysisService.analyze(records, budgetConfig).alerts();
    }

    public DataQualityReport getDataQualityReport() {
        Optional<WorkflowExecutionEntity> latest = getLatestExecution();
        if (latest.isPresent()) {
            WorkflowExecutionEntity exec = latest.get();
            return new DataQualityReport(
                    exec.getRecordsProcessed(),
                    exec.getUntaggedRecords(),
                    exec.getDuplicateRecords(),
                    0,
                    exec.getCreditRecords(),
                    exec.getCurrencyConversions());
        }
        return new DataQualityReport(0, 0, 0, 0, 0, 0);
    }

    private List<BillingRecord> getNormalizedBillingRecords() {
        return billingRecordRepository.findAllByOrderByUsageDateAsc().stream()
                .map(e -> new BillingRecord(
                        e.getUsageDate(),
                        e.getProvider(),
                        e.getService(),
                        e.getRegion(),
                        e.getCost(),
                        e.getCurrency(),
                        e.getWorkload()))
                .toList();
    }

    private Path resolveFilePath(String configuredPath) throws IOException {
        Path directPath = Path.of(configuredPath);
        if (Files.exists(directPath)) {
            return directPath;
        }

        // Try classpath resource and copy to temporary file if necessary
        String resourcePath = configuredPath.startsWith("/") ? configuredPath : "/" + configuredPath;
        try (InputStream is = getClass().getResourceAsStream(resourcePath)) {
            if (is != null) {
                Path temp = Files.createTempFile("bill-radar-", ".csv");
                Files.copy(is, temp, StandardCopyOption.REPLACE_EXISTING);
                temp.toFile().deleteOnExit();
                return temp;
            }
        }

        throw new IllegalArgumentException("Billing data file not found at path: " + configuredPath);
    }

    private record RawWithFingerprint(BillingRecord record, String fingerprint) {
    }
}
