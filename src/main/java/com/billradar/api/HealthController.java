package com.billradar.api;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.sql.DataSource;
import java.sql.Connection;
import java.time.Instant;
import java.util.Map;

@RestController
@RequestMapping("/api/health")
public class HealthController {

    private final DataSource dataSource;

    public HealthController(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> getHealth() {
        boolean dbHealthy = false;
        try (Connection conn = dataSource.getConnection()) {
            dbHealthy = conn.isValid(2);
        } catch (Exception ignored) {
        }

        Map<String, Object> body = Map.of(
                "status", dbHealthy ? "UP" : "DOWN",
                "database", dbHealthy ? "CONNECTED" : "DISCONNECTED",
                "application", "bill-radar",
                "timestamp", Instant.now().toString());

        return dbHealthy ? ResponseEntity.ok(body) : ResponseEntity.status(503).body(body);
    }
}
