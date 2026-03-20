package com.logplatform.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Configuração do WebClient reativo (Spring WebFlux).
 * 
 * Teoria para aula: 
 * Ao contrário do RestTemplate (que é bloqueante), o WebClient é assíncrono e reativo.
 * Isso significa que a thread que faz a requisição não fica parada esperando a resposta do 
 * serviço de ML (Python), permitindo que ela processe outros logs enquanto espera.
 *
 * O WebClient é o "cliente HTTP" moderno do Spring para sistemas de alta performance.
 */
@Configuration
public class WebClientConfig {

    @Bean
    public WebClient.Builder webClientBuilder() {
        return WebClient.builder()
                .codecs(configurer -> configurer
                        .defaultCodecs()
                        // Aumenta o limite de memória para 16MB. 
                        // Importante para receber respostas grandes do ML (como arrays de predições)
                        .maxInMemorySize(16 * 1024 * 1024));
    }
}
