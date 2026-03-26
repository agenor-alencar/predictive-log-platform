package com.logplatform.controller;

import com.logplatform.dto.StatsSummary;
import com.logplatform.service.StatisticsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Controller de estatísticas descritivas dos logs ingeridos.
 *
 * Endpoint: GET /stats/summary
 * - Retorna frequências de status HTTP, métodos, média/mediana/desvio padrão
 * do tempo de resposta, percentil 95, taxa de erro e horário de pico.
 * - O resultado é cacheado no Redis por 30 segundos (@Cacheable no Service).
 */
@RestController
@RequestMapping("/stats")
@RequiredArgsConstructor
@Tag(name = "Statistics", description = "Descriptive statistics for ingested logs")
public class StatsController {

    private final StatisticsService statisticsService;

    @GetMapping("/summary")
    @Operation(summary = "Get descriptive statistics", description = "Returns frequency distributions, mean, median, std dev, P95, peak hour, and error rate")
    public ResponseEntity<StatsSummary> getSummary() {
        // 1. Calcula os dados estatísticos (usando Cache Redis no nível de Service)
        StatsSummary summary = statisticsService.computeSummary();
        
        // 2. Retorna o objeto com média, mediana, P95, desvio padrão e pico de acessos
        return ResponseEntity.ok(summary);
    }
}
