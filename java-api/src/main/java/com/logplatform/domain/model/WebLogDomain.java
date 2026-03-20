package com.logplatform.domain.model;

import java.time.LocalDateTime;

/**
 * Modelo de Domínio para Entradas de Log Web.
 * 
 * Teoria para aula:
 * - Entidade de Domínio: Representa o objeto real que estamos manipulando (um Log).
 * - Independência: Esta classe não possui anotações de banco de dados (JPA) ou de 
 *   frameworks (Lombok). Isso a torna "pura" e fácil de mover para outros projetos.
 */
public class WebLogDomain {

    private Long id;
    private LocalDateTime timestamp;
    private String method;
    private String path;
    private int statusCode;
    private double responseTimeMs;
    private String userAgent;
    private String ipAddress;
    private int bytesSent;
    private LocalDateTime createdAt;

    public WebLogDomain() {
    }

    public WebLogDomain(LocalDateTime timestamp, String method, String path,
            int statusCode, double responseTimeMs, String userAgent,
            String ipAddress, int bytesSent) {
        this.timestamp = timestamp;
        this.method = method;
        this.path = path;
        this.statusCode = statusCode;
        this.responseTimeMs = responseTimeMs;
        this.userAgent = userAgent;
        this.ipAddress = ipAddress;
        this.bytesSent = bytesSent;
        this.createdAt = LocalDateTime.now();
    }

    public boolean isError() {
        return statusCode >= 400;
    }

    public boolean isServerError() {
        return statusCode >= 500;
    }

    // --- Getters & Setters ---

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }

    public String getMethod() {
        return method;
    }

    public void setMethod(String method) {
        this.method = method;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public void setStatusCode(int statusCode) {
        this.statusCode = statusCode;
    }

    public double getResponseTimeMs() {
        return responseTimeMs;
    }

    public void setResponseTimeMs(double responseTimeMs) {
        this.responseTimeMs = responseTimeMs;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public void setUserAgent(String userAgent) {
        this.userAgent = userAgent;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public void setIpAddress(String ipAddress) {
        this.ipAddress = ipAddress;
    }

    public int getBytesSent() {
        return bytesSent;
    }

    public void setBytesSent(int bytesSent) {
        this.bytesSent = bytesSent;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
