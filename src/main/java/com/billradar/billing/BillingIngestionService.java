package com.billradar.billing;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class BillingIngestionService {

    private final CurrencyConversionService currencyConversionService;

    public BillingIngestionService(
            CurrencyConversionService currencyConversionService) {
        this.currencyConversionService = currencyConversionService;
    }

    public IngestionResult process(List<BillingRecord> records) {
        List<BillingRecord> normalizedRecords = new ArrayList<>();
        Set<BillingRecord> seenRecords = new HashSet<>();

        int duplicateRows = 0;
        int untaggedRows = 0;
        int creditRows = 0;
        int currencyConversions = 0;

        for (BillingRecord record : records) {
            // Detect duplicates using the original record, before conversion.
            if (!seenRecords.add(record)) {
                duplicateRows++;
                continue;
            }

            if (record.workload() == null || record.workload().isBlank()) {
                untaggedRows++;
            }

            if (record.cost().signum() < 0) {
                creditRows++;
            }

            var eurCost = currencyConversionService.convertToEur(
                    record.cost(),
                    record.currency());

            if (!"EUR".equalsIgnoreCase(record.currency())) {
                currencyConversions++;
            }

            BillingRecord normalizedRecord = new BillingRecord(
                    record.usageDate(),
                    record.provider(),
                    record.service(),
                    record.region(),
                    eurCost,
                    "EUR",
                    record.workload());

            normalizedRecords.add(normalizedRecord);
        }

        DataQualityReport report = new DataQualityReport(
                records.size(),
                untaggedRows,
                duplicateRows,
                0,
                creditRows,
                currencyConversions);

        return new IngestionResult(normalizedRecords, report);
    }

    public record IngestionResult(
            List<BillingRecord> records,
            DataQualityReport report) {
        public IngestionResult {
            records = List.copyOf(records);
        }
    }
}
