package com.logplatform.domain.port;

import com.logplatform.domain.model.WebLogDomain;
import java.util.List;

/**
 * Port (interface) para persistência de logs.
 * 
 * Teoria para aula:
 * - Camada de Domínio: O "coração" da aplicação. Não deve conhecer detalhes técnicos 
 *   como SQL ou NoSQL.
 * - Port (Porta): Define o CONTRATO. O domínio diz: "Eu preciso salvar logs em massa", 
 *   e a infraestrutura (Adapter) deve descobrir como fazer isso.
 */
public interface LogRepository {

    void saveAll(List<WebLogDomain> logs);

    List<WebLogDomain> findAll();

    long count();
}
