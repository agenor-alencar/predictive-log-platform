package com.logplatform.fixture;

import com.logplatform.entity.WebLog;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Factory for WebLog entities used in integration tests.
 */
public final class WebLogFixture {

    private WebLogFixture() {}

    public static WebLog aWebLog() {
        return WebLog.builder()
                .timestamp(LocalDateTime.of(2025, 6, 1, 10, 0))
                .method("GET")
                .path("/api/test")
                .statusCode(200)
                .responseTimeMs(150.0)
                .userAgent("Mozilla/5.0")
                .ipAddress("192.168.1.1")
                .bytesSent(1024)
                .build();
    }

    public static WebLog anErrorLog() {
        return WebLog.builder()
                .timestamp(LocalDateTime.of(2025, 6, 1, 14, 30))
                .method("POST")
                .path("/api/resource")
                .statusCode(500)
                .responseTimeMs(3200.0)
                .userAgent("curl/8.4.0")
                .ipAddress("10.0.0.1")
                .bytesSent(0)
                .build();
    }

    public static List<WebLog> aListOf(int count) {
        List<WebLog> logs = new ArrayList<>();
        LocalDateTime base = LocalDateTime.of(2025, 6, 1, 8, 0);
        for (int i = 0; i < count; i++) {
            logs.add(WebLog.builder()
                    .timestamp(base.plusMinutes(i))
                    .method(i % 3 == 0 ? "POST" : "GET")
                    .path("/api/items/" + i)
                    .statusCode(i % 10 == 0 ? 500 : 200)
                    .responseTimeMs(100.0 + i * 2.5)
                    .userAgent("TestAgent/1.0")
                    .ipAddress("172.16.0." + (i % 254 + 1))
                    .bytesSent(512 + i * 10)
                    .build());
        }
        return logs;
    }
}
