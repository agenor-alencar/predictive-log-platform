package com.logplatform.infrastructure.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.logplatform.domain.model.WebLogDomain;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Produtor de Eventos Kafka.
 * 
 * Teoria para aula:
 * - Event-Driven (Arquitetura Orientada a Eventos): Em vez de apenas salvar no banco, 
 *   avisamos ao Kafka que um log chegou. Isso permite que outros sistemas 
 *   (como o processador de streams) reajam a essa informação imediatamente.
 * - KafkaTemplate: Ferramenta do Spring para enviar mensagens para tópicos específicos.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class LogEventProducer {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public void publish(WebLogDomain logDomain) {
        try {
            String json = objectMapper.writeValueAsString(logDomain);
            kafkaTemplate.send(KafkaConfig.LOGS_RAW_TOPIC, logDomain.getMethod(), json)
                    .whenComplete((result, ex) -> {
                        if (ex != null) {
                            log.error("Failed to publish log to Kafka: {}", ex.getMessage());
                        } else {
                            log.debug("Published log to Kafka: partition={}, offset={}",
                                    result.getRecordMetadata().partition(),
                                    result.getRecordMetadata().offset());
                        }
                    });
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize log for Kafka: {}", e.getMessage());
        }
    }

    public void publishBatch(java.util.List<WebLogDomain> logs) {
        logs.forEach(this::publish);
        log.info("Published batch of {} logs to Kafka topic {}", logs.size(), KafkaConfig.LOGS_RAW_TOPIC);
    }
}
