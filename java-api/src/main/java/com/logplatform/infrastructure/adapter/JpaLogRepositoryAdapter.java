package com.logplatform.infrastructure.adapter;

import com.logplatform.domain.model.WebLogDomain;
import com.logplatform.domain.port.LogRepository;
import com.logplatform.entity.WebLog;
import com.logplatform.repository.WebLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Adaptador de Infraestrutura: Implementa a porta 'LogRepository' via JPA.
 * 
 * Teoria para aula:
 * - Ports & Adapters: O Domínio define "O QUE" deve ser feito (interface), 
 *   e este Adaptador define "COMO" é feito usando uma tecnologia específica (JPA/Hibernate).
 * - Tradução: Esta classe serve como uma "ponte" que converte WebLogDomain (Domínio puro) 
 *   em WebLog (Entidade JPA) e vice-versa.
 */
@Component
@RequiredArgsConstructor
public class JpaLogRepositoryAdapter implements LogRepository {

    private final WebLogRepository jpaRepository;

    @Override
    public void saveAll(List<WebLogDomain> logs) {
        // Converte a lista de domínio para entidades JPA antes de salvar
        List<WebLog> entities = logs.stream()
                .map(this::toEntity)
                .collect(Collectors.toList());
        jpaRepository.saveAll(entities);
    }

    @Override
    public List<WebLogDomain> findAll() {
        return jpaRepository.findAll().stream()
                .map(this::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public long count() {
        return jpaRepository.count();
    }

    private WebLog toEntity(WebLogDomain domain) {
        return WebLog.builder()
                .timestamp(domain.getTimestamp())
                .method(domain.getMethod())
                .path(domain.getPath())
                .statusCode(domain.getStatusCode())
                .responseTimeMs(domain.getResponseTimeMs())
                .userAgent(domain.getUserAgent())
                .ipAddress(domain.getIpAddress())
                .bytesSent(domain.getBytesSent())
                .build();
    }

    private WebLogDomain toDomain(WebLog entity) {
        WebLogDomain domain = new WebLogDomain(
                entity.getTimestamp(),
                entity.getMethod(),
                entity.getPath(),
                entity.getStatusCode(),
                entity.getResponseTimeMs(),
                entity.getUserAgent(),
                entity.getIpAddress(),
                entity.getBytesSent() != null ? entity.getBytesSent() : 0);
        domain.setId(entity.getId());
        domain.setCreatedAt(entity.getCreatedAt());
        return domain;
    }
}
