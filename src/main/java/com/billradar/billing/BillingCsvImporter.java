
package com.billradar.billing;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

import java.io.IOException;
import java.io.Reader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class BillingCsvImporter {

    public List<BillingRecord> importFile(
            Path filePath,
            BillingSource source) throws IOException {

        try (
                Reader reader = Files.newBufferedReader(
                        filePath,
                        StandardCharsets.UTF_8);
                CSVParser parser = CSVFormat.DEFAULT.builder()
                        .setHeader()
                        .setSkipHeaderRecord(true)
                        .get()
                        .parse(reader)) {
            List<BillingRecord> records = new ArrayList<>();

            for (CSVRecord row : parser) {
                BillingRecord record = switch (source) {
                    case AWS -> parseAwsRow(row);
                    case AZURE -> parseAzureRow(row);
                };

                records.add(record);
            }

            return records;
        }
    }

    private BillingRecord parseAwsRow(CSVRecord row) {
        String rawDate = row.get("lineItem/UsageStartDate");

        LocalDate usageDate = OffsetDateTime
                .parse(rawDate.replace("Z", "+00:00"))
                .toLocalDate();

        return new BillingRecord(
                usageDate,
                "AWS",
                row.get("lineItem/ProductCode"),
                row.get("product/region"),
                new BigDecimal(row.get("lineItem/UnblendedCost")),
                row.get("lineItem/CurrencyCode"),
                blankToNull(row.get("resourceTags/user:workload")));
    }

    private BillingRecord parseAzureRow(CSVRecord row) {
        LocalDate usageDate = LocalDate.parse(row.get("Date"));

        String workload = extractAzureWorkload(
                row.get("Tags"));

        return new BillingRecord(
                usageDate,
                "AZURE",
                row.get("ServiceName"),
                row.get("ResourceLocation"),
                new BigDecimal(row.get("CostInBillingCurrency")),
                row.get("BillingCurrency"),
                workload);
    }

    private String extractAzureWorkload(String rawTags) {
        if (rawTags == null || rawTags.isBlank()) {
            return null;
        }

        // The current Azure fixture represents Tags as JSON.
        // Extract the workload value without adding a JSON dependency yet.
        String normalized = rawTags.trim();

        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("\"workload\"\\s*:\\s*\"([^\"]*)\"")
                .matcher(normalized);

        if (matcher.find()) {
            return blankToNull(matcher.group(1));
        }

        return null;
    }

    private String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        return value.trim();
    }
}
