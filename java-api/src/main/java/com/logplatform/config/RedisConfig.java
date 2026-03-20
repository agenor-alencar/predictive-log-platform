package com.logplatform.config;

import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * Configuração do Redis para Cache.
 * 
 * Teoria para aula: O Redis é um banco de dados em memória (in-memory) de chave-valor.
 * Utilizamos cache aqui para evitar reprocessar predições idênticas em um curto período,
 * economizando recursos de CPU do serviço de ML e reduzindo a latência para o usuário.
 * 
 * TTL (Time To Live): Define por quanto tempo um dado fica no cache antes de expirar.
 */
@Configuration
@EnableCaching
public class RedisConfig {

    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
                // Define o tempo padrão de vida dos dados no cache para 5 minutos
                .entryTtl(Duration.ofMinutes(5))
                // Configura a serialização para JSON, permitindo que os dados sejam lidos 
                // facilmente por outras ferramentas e sejam independentes de versão de classe Java
                .serializeValuesWith(
                        RedisSerializationContext.SerializationPair
                                .fromSerializer(new GenericJackson2JsonRedisSerializer())
                )
                // Evita salvar valores nulos no cache para não "sujar" a memória
                .disableCachingNullValues();

        Map<String, RedisCacheConfiguration> cacheConfigs = new HashMap<>();
        // Cache de 'predictions': Armazena resultados de ML por 5 minutos
        cacheConfigs.put("predictions", defaultConfig.entryTtl(Duration.ofMinutes(5)));
        // Cache de 'statistics': Expira mais rápido (30s) pois estatísticas mudam com frequência
        cacheConfigs.put("statistics", defaultConfig.entryTtl(Duration.ofSeconds(30)));

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(defaultConfig)
                .withInitialCacheConfigurations(cacheConfigs)
                .build();
    }
}
