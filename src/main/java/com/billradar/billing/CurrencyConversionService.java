package com.billradar.billing;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;

public class CurrencyConversionService {

    private final Map<String, BigDecimal> fxToUsd;

    public CurrencyConversionService(Map<String, BigDecimal> fxToUsd) {
        this.fxToUsd = Map.copyOf(fxToUsd);
    }

    public BigDecimal convertToEur(BigDecimal amount, String currency) {
        if (currency == null || currency.isBlank()) {
            throw new IllegalArgumentException("Currency is required");
        }

        String normalizedCurrency = currency.toUpperCase();

        if ("EUR".equals(normalizedCurrency)) {
            return amount.setScale(2, RoundingMode.HALF_UP);
        }

        if ("USD".equals(normalizedCurrency)) {
            BigDecimal eurToUsd = fxToUsd.get("EUR");

            if (eurToUsd == null || eurToUsd.signum() <= 0) {
                throw new IllegalArgumentException(
                        "A positive EUR-to-USD exchange rate is required");
            }

            return amount.divide(eurToUsd, 2, RoundingMode.HALF_UP);
        }

        throw new IllegalArgumentException(
                "Unsupported currency: " + currency);
    }

}
