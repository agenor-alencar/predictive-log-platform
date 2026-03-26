package com.logplatform.domain.port;

import com.logplatform.domain.model.PredictionResult;
import java.util.Map;

/**
 * Port (interface) para comunicação com o serviço de ML.
 * 
 * Teoria para aula:
 * - Isolamento: O domínio não sabe se o serviço de ML é uma chamada HTTP, gRPC ou 
 *   se está rodando na mesma máquina. Ele apenas define "O QUE" quer receber.
 * - Abstração: Isso permite trocar a implementação do motor de IA sem mexer em 
 *   uma única linha da lógica de negócio.
 */
public interface MlServicePort {

    PredictionResult predictError(String method, int hour, double historicalAvgResponse, int dayOfWeek);

    PredictionResult predictResponseTime(String method, int hour, double historicalAvgResponse, int dayOfWeek);
}
