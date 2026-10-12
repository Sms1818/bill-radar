package com.billradar.budget;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.HashMap;
import java.util.Map;

@Service
public class BudgetService {

    private final ObjectMapper objectMapper;
    private final String budgetFilePath;
    private volatile BudgetConfig cachedConfig;

    public BudgetService(
            ObjectMapper objectMapper,
            @Value("${bill-radar.budget-file:sample-data/budgets.json}") String budgetFilePath) {
        this.objectMapper = objectMapper;
        this.budgetFilePath = budgetFilePath;
    }

    public synchronized BudgetConfig getBudgetConfig() {
        if (cachedConfig == null) {
            cachedConfig = loadBudgetConfig(this.budgetFilePath);
        }
        return cachedConfig;
    }

    public synchronized BudgetConfig reload() {
        cachedConfig = loadBudgetConfig(this.budgetFilePath);
        return cachedConfig;
    }

    public BudgetConfig loadBudgetConfig(String pathStr) {
        try {
            JsonNode root;
            Path path = Path.of(pathStr);
            if (Files.exists(path)) {
                try (InputStream is = Files.newInputStream(path)) {
                    root = objectMapper.readTree(is);
                }
            } else {
                // Try classpath fallback
                String resourcePath = pathStr.startsWith("/") ? pathStr : "/" + pathStr;
                try (InputStream is = getClass().getResourceAsStream(resourcePath)) {
                    if (is == null) {
                        throw new IllegalArgumentException("Budget configuration file not found at: " + pathStr);
                    }
                    root = objectMapper.readTree(is);
                }
            }

            return parseAndValidate(root);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read budget configuration from: " + pathStr, e);
        }
    }

    public BudgetConfig parseAndValidate(JsonNode root) {
        if (root == null || !root.isObject()) {
            throw new IllegalArgumentException("Invalid budget configuration JSON: root must be an object");
        }

        JsonNode currencyNode = root.get("currency");
        if (currencyNode == null || currencyNode.asText().isBlank()) {
            throw new IllegalArgumentException("Budget configuration must specify currency");
        }
        String currency = currencyNode.asText().trim().toUpperCase();
        if (!"EUR".equals(currency)) {
            throw new IllegalArgumentException("Unsupported budget currency: " + currency + ". Only EUR is supported.");
        }

        JsonNode monthNode = root.get("month");
        if (monthNode == null || monthNode.asText().isBlank()) {
            throw new IllegalArgumentException("Budget configuration must specify month (YYYY-MM)");
        }
        YearMonth month = YearMonth.parse(monthNode.asText().trim());

        JsonNode asOfNode = root.get("as_of");
        if (asOfNode == null || asOfNode.asText().isBlank()) {
            throw new IllegalArgumentException("Budget configuration must specify as_of date (YYYY-MM-DD)");
        }
        LocalDate asOf = LocalDate.parse(asOfNode.asText().trim());

        if (!YearMonth.from(asOf).equals(month)) {
            throw new IllegalArgumentException("Configured as_of date (" + asOf + ") does not match billing month (" + month + ")");
        }

        JsonNode fxNode = root.get("fx_to_usd");
        if (fxNode == null || !fxNode.isObject()) {
            throw new IllegalArgumentException("Budget configuration must specify fx_to_usd map");
        }
        Map<String, BigDecimal> fxToUsd = new HashMap<>();
        for (Map.Entry<String, JsonNode> entry : fxNode.properties()) {
            BigDecimal rate = entry.getValue().decimalValue();
            if (rate.signum() <= 0) {
                throw new IllegalArgumentException("Exchange rate for " + entry.getKey() + " must be strictly positive");
            }
            fxToUsd.put(entry.getKey().toUpperCase(), rate);
        }

        if (!fxToUsd.containsKey("EUR")) {
            throw new IllegalArgumentException("Exchange rate map fx_to_usd must contain positive rate for EUR");
        }

        JsonNode budgetsNode = root.get("budgets");
        if (budgetsNode == null || !budgetsNode.isObject()) {
            throw new IllegalArgumentException("Budget configuration must specify budgets map");
        }
        Map<String, BigDecimal> budgets = new HashMap<>();
        for (Map.Entry<String, JsonNode> entry : budgetsNode.properties()) {
            BigDecimal budgetVal = entry.getValue().decimalValue();
            if (budgetVal.signum() < 0) {
                throw new IllegalArgumentException("Budget for workload " + entry.getKey() + " cannot be negative");
            }
            budgets.put(entry.getKey().toLowerCase(), budgetVal);
        }

        return new BudgetConfig(month, asOf, currency, fxToUsd, budgets);
    }
}
