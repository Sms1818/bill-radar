package com.billradar;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

import com.billradar.billing.BillingCsvImporter;
import com.billradar.billing.BillingIngestionService;
import com.billradar.billing.BillingRecord;
import com.billradar.billing.BillingSource;
import com.billradar.billing.CurrencyConversionService;

class BillingCsvImporterTests {

    private final BillingCsvImporter importer = new BillingCsvImporter();

    @Test
    void importsAwsCsv() throws Exception {
        List<BillingRecord> records = importer.importFile(
                Path.of("sample-data/aws_cur_style.csv"),
                BillingSource.AWS);

        assertFalse(records.isEmpty());
        assertEquals("AWS", records.getFirst().provider());
        assertNotNull(records.getFirst().usageDate());
        assertNotNull(records.getFirst().cost());
    }

    @Test
    void importsAzureCsv() throws Exception {
        List<BillingRecord> records = importer.importFile(
                Path.of("sample-data/azure_cost_style.csv"),
                BillingSource.AZURE);

        assertFalse(records.isEmpty());
        assertEquals("AZURE", records.getFirst().provider());
        assertNotNull(records.getFirst().usageDate());
        assertNotNull(records.getFirst().cost());
        assertNotNull(records.getFirst().workload());
    }

    @Test
    void importsBothProvidersAndNormalizesAllRecordsToEur() throws Exception {
        List<BillingRecord> awsRecords = importer.importFile(
                Path.of("sample-data/aws_cur_style.csv"),
                BillingSource.AWS);

        List<BillingRecord> azureRecords = importer.importFile(
                Path.of("sample-data/azure_cost_style.csv"),
                BillingSource.AZURE);

        List<BillingRecord> allRecords = new java.util.ArrayList<>();
        allRecords.addAll(awsRecords);
        allRecords.addAll(azureRecords);

        CurrencyConversionService converter = new CurrencyConversionService(
                java.util.Map.of(
                        "EUR",
                        new java.math.BigDecimal("1.12")));

        BillingIngestionService ingestionService = new BillingIngestionService(converter);

        BillingIngestionService.IngestionResult result = ingestionService.process(allRecords);

        assertEquals(134, result.report().rowsProcessed());
        assertEquals(1, result.report().duplicateRowsSkipped());
        assertEquals(1, result.report().creditRows());
        assertEquals(7, result.report().untaggedRows());
        assertEquals(133, result.records().size());

        assertTrue(result.records().stream()
                .allMatch(record -> "EUR".equals(record.currency())));
    }

}
