package com.billradar.billing;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.HexFormat;

public final class FingerprintUtil {

    private FingerprintUtil() {
    }

    public static String computeFingerprint(
            LocalDate usageDate,
            String provider,
            String service,
            String region,
            BigDecimal cost,
            String currency,
            String workload) {

        String key = String.format("%s|%s|%s|%s|%s|%s|%s",
                provider != null ? provider.trim().toUpperCase() : "",
                usageDate != null ? usageDate.toString() : "",
                service != null ? service.trim() : "",
                region != null ? region.trim() : "",
                cost != null ? cost.stripTrailingZeros().toPlainString() : "0",
                currency != null ? currency.trim().toUpperCase() : "",
                workload != null ? workload.trim().toLowerCase() : "");

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(key.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }
}
