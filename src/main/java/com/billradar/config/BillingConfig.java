package com.billradar.config;

import com.billradar.billing.BillingCsvImporter;
import com.billradar.billing.BillingIngestionService;
import com.billradar.billing.CurrencyConversionService;
import com.billradar.budget.BudgetService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class BillingConfig {

    @Bean
    public BillingCsvImporter billingCsvImporter() {
        return new BillingCsvImporter();
    }

    @Bean
    public CurrencyConversionService currencyConversionService(BudgetService budgetService) {
        return new CurrencyConversionService(budgetService.getBudgetConfig().fxToUsd());
    }

    @Bean
    public BillingIngestionService billingIngestionService(CurrencyConversionService currencyConversionService) {
        return new BillingIngestionService(currencyConversionService);
    }
}
