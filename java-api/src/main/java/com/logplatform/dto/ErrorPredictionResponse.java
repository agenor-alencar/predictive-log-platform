package com.logplatform.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

/**
 * DTO (Data Transfer Object) para Resposta de Predição de Erro.
 * 
 * Teoria para aula:
 * - DTO: Objeto usado exclusivamente para transportar dados entre sistemas (Java <-> Python).
 * - @JsonProperty: Como o Python usa o padrão 'snake_case' (ex: error_probability) 
 *   e o Java usa 'camelCase' (ex: errorProbability), usamos essa anotação para o 
 *   Jackson fazer a tradução automática.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ErrorPredictionResponse {

    @JsonProperty("error_probability")
    private double errorProbability;

    @JsonProperty("risk_level")
    private String riskLevel;

    @JsonProperty("model_used")
    private String modelUsed;

    @JsonProperty("inference_time_ms")
    private Double inferenceTimeMs;
}
