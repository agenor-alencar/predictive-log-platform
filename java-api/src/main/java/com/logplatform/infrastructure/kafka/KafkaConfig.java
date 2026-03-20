package com.logplatform.infrastructure.kafka;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafkaStreams;
import org.springframework.kafka.config.TopicBuilder;

/**
 * Configuração de Tópicos do Kafka.
 * 
 * Teoria para aula: 
 * O Kafka funciona como um sistema de mensageria pub/sub (publicador/assinante) de alto rendimento.
 * - Tópico: É como uma "gaveta" ou "canal" onde as mensagens são colocadas.
 * - Partições: Permitem que o Kafka escale paralelamente. Quanto mais partições, mais 
 *   consumidores podem ler os dados ao mesmo tempo.
 * - Réplicas: Garantem que os dados não sejam perdidos se um servidor Kafka cair.
 */
@Configuration
@EnableKafkaStreams
public class KafkaConfig {

    // Tópico para logs brutos (exatamente como chegam do sistema)
    public static final String LOGS_RAW_TOPIC = "plip.logs.raw";
    // Tópico para logs processados e agregados (mais fáceis de analisar)
    public static final String LOGS_AGGREGATED_TOPIC = "plip.logs.aggregated";

    @Bean
    public NewTopic logsRawTopic() {
        return TopicBuilder.name(LOGS_RAW_TOPIC)
                .partitions(3) // 3 partições permitem até 3 consumidores em paralelo
                .replicas(1)   // 1 réplica (mínimo para desenvolvimento)
                .build();
    }

    @Bean
    public NewTopic logsAggregatedTopic() {
        return TopicBuilder.name(LOGS_AGGREGATED_TOPIC)
                .partitions(3)
                .replicas(1)
                .build();
    }
}
