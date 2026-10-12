package com.billradar.billing;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class CurrencyConversionServiceTests {

    private final CurrencyConversionService service = new CurrencyConversionService(
            Map.of("EUR", new BigDecimal("1.12")));

    @Test
    void convertsUsdToEur() {
        BigDecimal result = service.convertToEur(
                new BigDecimal("112.00"), "USD");

        assertEquals(new BigDecimal("100.00"), result);
    }

    @Test
    void leavesEurUnchanged() {
        BigDecimal result = service.convertToEur(
                new BigDecimal("100.00"), "EUR");

        assertEquals(new BigDecimal("100.00"), result);
    }

    @Test
    void convertsNegativeCreditsCorrectly() {
        BigDecimal result = service.convertToEur(
                new BigDecimal("-112.00"), "USD");

        assertEquals(new BigDecimal("-100.00"), result);
    }

    @Test
    void rejectsUnsupportedCurrencies() {
        assertThrows(
                IllegalArgumentException.class,
                () -> service.convertToEur(
                        new BigDecimal("100.00"), "GBP"));
    }
}
