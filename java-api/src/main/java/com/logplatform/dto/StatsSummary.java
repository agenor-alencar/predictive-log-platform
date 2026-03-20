package com.logplatform.dto;

import lombok.*;
import java.util.Map;

/**
 * DTO de Resposta do Sumário Estatístico.
 * 
 * Teoria para aula:
 * - Estatística Descritiva: Transforma milhares de linhas de log em informações 
 *   decisivas como Percentil 95 (P95) e taxa de erro global.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StatsSummary {
    private long totalRecords;
    private Map<Integer, Long> statusCodeFrequencyAbsolute;
    private Map<Integer, Double> statusCodeFrequencyRelative;
    private Map<String, Long> methodFrequency;
    private double meanResponseTime;
    private double medianResponseTime;
    private double stdDevResponseTime;
    private double percentile95ResponseTime;
    private double errorRate;
    private Integer peakHour;
    private long peakHourCount;
}
