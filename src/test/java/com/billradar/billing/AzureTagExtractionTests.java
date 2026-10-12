package com.billradar.billing;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AzureTagExtractionTests {

    private final BillingCsvImporter importer = new BillingCsvImporter();

    @Test
    void extractsWorkloadFromValidJsonTag(@TempDir Path tempDir) throws IOException {
        String csv = """
                Date,ServiceName,ResourceLocation,CostInBillingCurrency,BillingCurrency,Tags
                2026-10-01,Virtual Machines,westeurope,100.00,USD,"{""workload"": ""search""}"
                """;
        Path file = tempDir.resolve("azure_valid.csv");
        Files.writeString(file, csv);

        List<BillingRecord> records = importer.importFile(file, BillingSource.AZURE);
        assertEquals(1, records.size());
        assertEquals("search", records.getFirst().workload());
    }

    @Test
    void handlesMissingOrEmptyTagsGracefully(@TempDir Path tempDir) throws IOException {
        String csv = """
                Date,ServiceName,ResourceLocation,CostInBillingCurrency,BillingCurrency,Tags
                2026-10-01,Virtual Machines,westeurope,50.00,USD,""
                2026-10-02,Storage,westeurope,20.00,USD,"   "
                2026-10-03,Network,westeurope,10.00,USD,
                """;
        Path file = tempDir.resolve("azure_empty_tags.csv");
        Files.writeString(file, csv);

        List<BillingRecord> records = importer.importFile(file, BillingSource.AZURE);
        assertEquals(3, records.size());
        assertNull(records.get(0).workload());
        assertNull(records.get(1).workload());
        assertNull(records.get(2).workload());
    }

    @Test
    void handlesMalformedTagWithoutCrashing(@TempDir Path tempDir) throws IOException {
        String csv = """
                Date,ServiceName,ResourceLocation,CostInBillingCurrency,BillingCurrency,Tags
                2026-10-01,Virtual Machines,westeurope,50.00,USD,invalid-non-json
                2026-10-02,Storage,westeurope,20.00,USD,"{""other"": ""tag""}"
                """;
        Path file = tempDir.resolve("azure_malformed.csv");
        Files.writeString(file, csv);

        List<BillingRecord> records = importer.importFile(file, BillingSource.AZURE);
        assertEquals(2, records.size());
        assertNull(records.get(0).workload());
        assertNull(records.get(1).workload());
    }
}
