package com.billradar.billing;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class BillingIngestionServiceTests {

    private final CurrencyConversionService currencyConverter = new CurrencyConversionService(
            java.util.Map.of(
                    "EUR",
                    new java.math.BigDecimal("1.12")));

    private final BillingIngestionService service = new BillingIngestionService(currencyConverter);

    @Test
    void removesExactDuplicatesAndPreservesCredits() {
        BillingRecord normal = new BillingRecord(
                LocalDate.parse("2026-10-01"),
                "AWS",
                "AmazonEC2",
                "eu-central-1",
                new BigDecimal("100.00"),
                "USD",
                "checkout");

        BillingRecord credit = new BillingRecord(
                LocalDate.parse("2026-10-02"),
                "AWS",
                "AmazonEC2",
                "eu-central-1",
                new BigDecimal("-25.00"),
                "USD",
                "checkout");

        BillingIngestionService.IngestionResult result = service.process(List.of(normal, normal, credit));

        assertEquals(2, result.records().size());
        assertEquals(1, result.report().duplicateRowsSkipped());
        assertEquals(1, result.report().creditRows());

        assertTrue(result.records().stream()
                .anyMatch(record -> record.cost().signum() < 0));
    }

    @Test
    void countsUntaggedRecordsWithoutDiscardingThem() {
        BillingRecord untagged = new BillingRecord(
                LocalDate.parse("2026-10-03"),
                "AWS",
                "AmazonEC2",
                "eu-central-1",
                new BigDecimal("50.00"),
                "USD",
                null);

        BillingIngestionService.IngestionResult result = service.process(List.of(untagged));

        assertEquals(1, result.records().size());
        assertEquals(1, result.report().untaggedRows());
        assertEquals(0, result.report().duplicateRowsSkipped());
    }

    @Test
    void convertsUsdRecordsToEurDuringIngestion() {
        BillingRecord usdRecord = new BillingRecord(
                LocalDate.parse("2026-10-01"),
                "AWS",
                "AmazonEC2",
                "eu-central-1",
                new BigDecimal("112.00"),
                "USD",
                "checkout");

        BillingIngestionService.IngestionResult result = service.process(List.of(usdRecord));

        BillingRecord normalized = result.records().getFirst();

        assertEquals(new BigDecimal("100.00"), normalized.cost());
        assertEquals("EUR", normalized.currency());
        assertEquals(1, result.report().currencyConversions());
    }

}
