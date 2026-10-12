package com.billradar.billing;

import java.math.BigDecimal;
import java.time.LocalDate;

public record BillingRecord(
        LocalDate usageDate,
        String provider,
        String service,
        String region,
        BigDecimal cost,
        String currency,
        String workload) {
}