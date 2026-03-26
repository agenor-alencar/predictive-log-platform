package com.logplatform.infrastructure.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.logplatform.domain.model.WebLogDomain;
import com.logplatform.domain.port.LogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Consumidor de Eventos Kafka.
 * 
 * Teoria para aula:
 * - Consumo Assíncrono: A API Java "escuta" o tópico do Kafka. Quando um novo log 
 *   chega, ele é processado e salvo no banco automaticamente.
 * - @KafkaListener: Anotação que transforma o método em um "ouvinte" de mensagens, 
 *   permitindo que o sistema processe dados em background sem travar o usuário.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class LogEventConsumer {

    private final LogRepository logRepository;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = KafkaConfig.LOGS_RAW_TOPIC, groupId = "plip-log-consumer", containerFactory = "kafkaListenerContainerFactory")
    public void consume(String message) {
        try {
            WebLogDomain logEntry = objectMapper.readValue(message, WebLogDomain.class);
            logRepository.saveAll(List.of(logEntry));

            if (logEntry.isError()) {
                log.warn("Error log ingested via Kafka: {} {} → {}",
                        logEntry.getMethod(), logEntry.getPath(), logEntry.getStatusCode());
            }
        } catch (Exception e) {
            log.error("Failed to process Kafka log event: {}", e.getMessage());
        }
    }
}
