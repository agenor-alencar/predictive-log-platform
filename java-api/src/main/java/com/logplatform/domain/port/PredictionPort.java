package com.logplatform.domain.port;

import com.logplatform.domain.model.PredictionResult;

/**
 * Port (interface) para persistência de auditoria de predições.
 * 
 * Teoria para aula:
 * - Auditoria: Define uma porta específica para que os resultados das predições 
 *   sejam armazenados, garantindo rastreabilidade no domínio.
 */
public interface PredictionPort {

    void save(String type, String inputJson, String resultJson);
}
