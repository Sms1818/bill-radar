package com.billradar.budget;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;

import static org.junit.jupiter.api.Assertions.*;

class BudgetServiceTests {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final BudgetService budgetService = new BudgetService(objectMapper, "sample-data/budgets.json");

    @Test
    void loadsSampleBudgetsJsonSuccessfully() {
        BudgetConfig config = budgetService.getBudgetConfig();

        assertNotNull(config);
        assertEquals(YearMonth.of(2026, 10), config.month());
        assertEquals(LocalDate.of(2026, 10, 12), config.asOf());
        assertEquals("EUR", config.currency());
        assertEquals(new BigDecimal("1.12"), config.fxToUsd().get("EUR"));
        assertEquals(5, config.budgets().size());
        assertEquals(new BigDecimal("8035.71"), config.getBudgetForWorkload("checkout"));
        assertEquals(new BigDecimal("4464.29"), config.getBudgetForWorkload("analytics"));
        assertEquals(new BigDecimal("10714.29"), config.getBudgetForWorkload("ml-training"));
        assertEquals(new BigDecimal("2008.93"), config.getBudgetForWorkload("internal-tools"));
        assertEquals(new BigDecimal("5357.14"), config.getBudgetForWorkload("search"));
    }

    @Test
    void rejectsUnsupportedCurrency() {
        String json = """
                {
                  "month": "2026-10",
                  "as_of": "2026-10-12",
                  "currency": "USD",
                  "fx_to_usd": { "EUR": 1.12 },
                  "budgets": { "checkout": 1000 }
                }
                """;
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> {
            budgetService.parseAndValidate(objectMapper.readTree(json));
        });
        assertTrue(ex.getMessage().contains("Unsupported budget currency"));
    }

    @Test
    void rejectsMissingEurExchangeRate() {
        String json = """
                {
                  "month": "2026-10",
                  "as_of": "2026-10-12",
                  "currency": "EUR",
                  "fx_to_usd": { "GBP": 1.25 },
                  "budgets": { "checkout": 1000 }
                }
                """;
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> {
            budgetService.parseAndValidate(objectMapper.readTree(json));
        });
        assertTrue(ex.getMessage().contains("must contain positive rate for EUR"));
    }

    @Test
    void rejectsNonMatchingMonthAndAsOfDate() {
        String json = """
                {
                  "month": "2026-11",
                  "as_of": "2026-10-12",
                  "currency": "EUR",
                  "fx_to_usd": { "EUR": 1.12 },
                  "budgets": { "checkout": 1000 }
                }
                """;
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> {
            budgetService.parseAndValidate(objectMapper.readTree(json));
        });
        assertTrue(ex.getMessage().contains("does not match billing month"));
    }
}
