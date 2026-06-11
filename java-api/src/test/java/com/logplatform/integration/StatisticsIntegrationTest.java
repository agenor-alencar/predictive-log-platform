package com.logplatform.integration;

import com.logplatform.config.TestContainersConfig;
import com.logplatform.dto.StatsSummary;
import com.logplatform.fixture.WebLogFixture;
import com.logplatform.repository.WebLogRepository;
import com.logplatform.service.StatisticsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

class StatisticsIntegrationTest extends TestContainersConfig {

    @Autowired
    private WebLogRepository webLogRepository;

    @Autowired
    private StatisticsService statisticsService;

    @Autowired
    private CacheManager cacheManager;

    @BeforeEach
    void cleanDatabase() {
        webLogRepository.deleteAll();
        // O cache "statistics" usa chave fixa 'summary' (TTL 30s) — sem limpar,
        // o resultado de um teste vazaria para o seguinte
        Cache statisticsCache = cacheManager.getCache("statistics");
        if (statisticsCache != null) {
            statisticsCache.clear();
        }
    }

    @Test
    void computeSummary_afterIngestion_returnsTotalRecordsGreaterThanZero() {
        webLogRepository.saveAll(WebLogFixture.aListOf(50));

        StatsSummary summary = statisticsService.computeSummary();

        assertThat(summary.getTotalRecords()).isEqualTo(50);
    }

    @Test
    void computeSummary_afterIngestion_calculatesStatisticsCorrectly() {
        webLogRepository.saveAll(WebLogFixture.aListOf(100));

        StatsSummary summary = statisticsService.computeSummary();

        assertThat(summary.getMeanResponseTime()).isGreaterThan(0);
        assertThat(summary.getMedianResponseTime()).isGreaterThan(0);
        assertThat(summary.getPercentile95ResponseTime()).isGreaterThanOrEqualTo(summary.getMeanResponseTime());
        assertThat(summary.getErrorRate()).isGreaterThan(0);
        assertThat(summary.getMethodFrequency()).isNotEmpty();
        assertThat(summary.getStatusCodeFrequencyAbsolute()).isNotEmpty();
    }

    @Test
    void computeSummary_emptyDatabase_returnsZeroedStats() {
        StatsSummary summary = statisticsService.computeSummary();

        assertThat(summary.getTotalRecords()).isZero();
        assertThat(summary.getMeanResponseTime()).isZero();
        assertThat(summary.getErrorRate()).isZero();
    }

    @Test
    void statsEndpoint_afterIngestion_returns200WithData() {
        webLogRepository.saveAll(WebLogFixture.aListOf(20));

        ResponseEntity<StatsSummary> response = restTemplate.getForEntity(
                baseUrl + "/stats/summary", StatsSummary.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getTotalRecords()).isEqualTo(20);
    }

    @Test
    void statsEndpoint_emptyDatabase_returns200WithZeros() {
        ResponseEntity<StatsSummary> response = restTemplate.getForEntity(
                baseUrl + "/stats/summary", StatsSummary.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getTotalRecords()).isZero();
    }
}
