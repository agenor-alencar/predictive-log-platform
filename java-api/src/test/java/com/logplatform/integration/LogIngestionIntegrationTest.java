package com.logplatform.integration;

import com.logplatform.config.TestContainersConfig;
import com.logplatform.fixture.CsvFixture;
import com.logplatform.repository.WebLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;

import static org.assertj.core.api.Assertions.assertThat;

import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

class LogIngestionIntegrationTest extends TestContainersConfig {

    @Autowired
    private WebLogRepository webLogRepository;

    @BeforeEach
    void cleanDatabase() {
        webLogRepository.deleteAll();
    }

    @Test
    void uploadCsv_validFile_persistsRecordsInDatabase() {
        String csv = CsvFixture.validCsv(10);
        ResponseEntity<String> response = uploadCsv("web_logs.csv", "text/csv", csv.getBytes());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(webLogRepository.count()).isEqualTo(10);
    }

    @Test
    void uploadCsv_validFile_returnsSuccessBody() {
        String csv = CsvFixture.SINGLE_ROW_CSV;
        ResponseEntity<String> response = uploadCsv("logs.csv", "text/csv", csv.getBytes());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("success");
        assertThat(response.getBody()).contains("recordsProcessed");
    }

    @Test
    void uploadCsv_nonCsvFile_returns400() {
        ResponseEntity<String> response = uploadCsv(
                "data.txt", "text/plain", CsvFixture.NON_CSV_CONTENT.getBytes());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(webLogRepository.count()).isZero();
    }

    @Test
    void uploadCsv_emptyFile_returns400() {
        ResponseEntity<String> response = uploadCsv("empty.csv", "text/csv", new byte[0]);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(webLogRepository.count()).isZero();
    }

    @Test
    void uploadCsv_csvWithInvalidRows_persistsOnlyValidRows() {
        ResponseEntity<String> response = uploadCsv(
                "mixed.csv", "text/csv", CsvFixture.CSV_WITH_INVALID_ROWS.getBytes());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(webLogRepository.count()).isEqualTo(2);
    }

    @Test
    void uploadCsv_largeBatch_persistsAllRecords() {
        String csv = CsvFixture.validCsv(550);
        ResponseEntity<String> response = uploadCsv("big_logs.csv", "text/csv", csv.getBytes());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(webLogRepository.count()).isEqualTo(550);
    }

    private ResponseEntity<String> uploadCsv(String filename, String contentType, byte[] content) {
        ByteArrayResource file = new ByteArrayResource(content) {
            @Override
            public String getFilename() {
                return filename;
            }
        };

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", file);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        return restTemplate.postForEntity(
                baseUrl + "/logs/upload",
                new HttpEntity<>(body, headers),
                String.class);
    }
}
