package com.logplatform;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Ponto de Entrada da API Java — Predictive Log Intelligence Platform.
 * 
 * Teoria para aula:
 * - @SpringBootApplication: Esta anotação "liga os motores" do framework, disparando 
 *   o Component Scan (busca de classes documentadas) e a Auto-Configuration 
 *   (configuração automática baseada no que temos no pom.xml).
 * - Orquestração: A API Java serve como o cérebro que conecta o front-end, o 
 *   banco SQL, o cache Redis, a mensageria Kafka e o motor de IA em Python.
 */
@SpringBootApplication
public class LogPlatformApplication {
    public static void main(String[] args) {
        SpringApplication.run(LogPlatformApplication.class, args);
    }
}
