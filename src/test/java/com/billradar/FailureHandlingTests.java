package com.billradar;

import com.billradar.api.GlobalExceptionHandler;
import com.billradar.billing.BillingCsvImporter;
import com.billradar.billing.BillingSource;
import com.billradar.budget.BudgetService;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.io.IOException;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class FailureHandlingTests {

    private final BillingCsvImporter csvImporter = new BillingCsvImporter();
    private final GlobalExceptionHandler exceptionHandler = new GlobalExceptionHandler();

    @Test
    void missingCsvFileThrowsIOException() {
        assertThrows(IOException.class, () -> {
            csvImporter.importFile(Path.of("non-existent-directory/billing.csv"), BillingSource.AWS);
        });
    }

    @Test
    void missingBudgetConfigurationThrowsException() {
        BudgetService budgetService = new BudgetService(new ObjectMapper(), "missing-path/budgets.json");
        assertThrows(IllegalArgumentException.class, budgetService::getBudgetConfig);
    }

    @Test
    void globalExceptionHandlerHandlesIllegalArgumentGracefully() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/costs/workloads");
        IllegalArgumentException ex = new IllegalArgumentException("Invalid parameter supplied");

        ResponseEntity<Map<String, Object>> response = exceptionHandler.handleIllegalArgument(ex, request);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(400, response.getBody().get("status"));
        assertEquals("Bad Request", response.getBody().get("error"));
        assertEquals("Invalid parameter supplied", response.getBody().get("message"));
        assertEquals("/api/costs/workloads", response.getBody().get("path"));
        assertNotNull(response.getBody().get("timestamp"));
        assertNull(response.getBody().get("trace"), "Stack trace must not be exposed");
    }

    @Test
    void globalExceptionHandlerHandlesGenericExceptionWithoutLeakingStackTrace() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/ingest");
        Exception ex = new RuntimeException("Unexpected internal DB timeout");

        ResponseEntity<Map<String, Object>> response = exceptionHandler.handleGenericException(ex, request);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(500, response.getBody().get("status"));
        assertEquals("Internal Server Error", response.getBody().get("error"));
        assertTrue(response.getBody().get("message").toString().contains("Unexpected internal DB timeout"));
        assertEquals("/api/ingest", response.getBody().get("path"));
        assertNull(response.getBody().get("trace"), "Stack trace must not be exposed");
    }
}
