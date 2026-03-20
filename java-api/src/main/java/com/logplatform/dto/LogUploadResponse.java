package com.logplatform.dto;

import lombok.*;

/**
 * DTO de Resposta do Upload de CSV.
 * 
 * Teoria para aula:
 * - Feedback ao Usuário: Após o processamento síncrono, é vital retornar 
 *   exatamente quantos registros foram processados com sucesso e quantos falharam.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LogUploadResponse {
    private String status;
    private int recordsProcessed;
    private int recordsFailed;
    private String message;
}
