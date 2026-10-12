package com.billradar.billing;

public record DataQualityReport(
        int rowsProcessed,
        int untaggedRows,
        int duplicateRowsSkipped,
        int invalidRows,
        int creditRows,
        int currencyConversions) {
}
