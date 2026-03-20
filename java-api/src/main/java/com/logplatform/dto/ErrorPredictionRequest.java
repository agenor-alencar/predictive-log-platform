package com.logplatform.dto;

import jakarta.validation.constraints.*;
import lombok.*;

/**
 * DTO de Entrada para Pedido de Predição.
 * 
 * Teoria para aula:
 * - Validação (Jakarta Validation): Usamos anotações como @NotBlank, @Min e @Max 
 *   para garantir que o usuário não envie dados inválidos (ex: hora negativa).
 * - Isso protege o motor de ML de receber dados que causariam erro no cálculo.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ErrorPredictionRequest {

    @NotBlank(message = "HTTP method is required")
    private String method;

    @Min(value = 0, message = "Hour must be between 0 and 23")
    @Max(value = 23, message = "Hour must be between 0 and 23")
    private int hour;

    @Positive(message = "Historical average response must be positive")
    private double historicalAvgResponse;

    @Min(0)
    @Max(6)
    @Builder.Default
    private int dayOfWeek = 2;
}
