package com.logplatform.config;

import com.logplatform.repository.PredictionRepository;
// Import do Micrometer para métricas tipo Gauge
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.actuate.autoconfigure.metrics.MeterRegistryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuração de métricas customizadas para observabilidade via
 * Prometheus/Grafana.
 *
 * Registra gauges (medidores) do Micrometer que expõem dados de ML no endpoint
 * /actuator/prometheus. Os gauges consultam o PredictionRepository para contar
 * o volume total de predições e a quantidade por tipo (error, response_time).
 *
 * Métricas expostas:
 * - ml.predictions.volume → total de predições armazenadas
 * - ml.predictions.error.count → total de predições de erro
 * - ml.predictions.response_time.count → total de predições de tempo de
 * resposta
 */
@Configuration
@Slf4j
public class MetricsConfig {

        @Bean
        public MeterRegistryCustomizer<MeterRegistry> metricsCustomizer(
                        PredictionRepository predictionRepository) {

                return registry -> {
                        // Gauge para volume total: Monitora a contagem total de registros no banco.
                        // Útil para ver o crescimento da base de dados ao longo do tempo.
                        Gauge.builder("ml.predictions.volume",
                                        predictionRepository, PredictionRepository::count)
                                        .description("Total number of predictions stored")
                                        .register(registry);

                        // Gauge de erros: Filtra e conta apenas as predições do tipo 'error'.
                        // Ajuda na criação de alertas de saúde do sistema.
                        Gauge.builder("ml.predictions.error.count",
                                        predictionRepository,
                                        repo -> repo.countByPredictionType("error"))
                                        .description("Total error predictions")
                                        .register(registry);

                        // Gauge de tempo de resposta: Monitora a volumetria de previsões de latência.
                        Gauge.builder("ml.predictions.response_time.count",
                                        predictionRepository,
                                        repo -> repo.countByPredictionType("response_time"))
                                        .description("Total response time predictions")
                                        .register(registry);

                        log.info("Métricas customizadas de ML registradas no endpoint do Prometheus");
                };
        }
}
