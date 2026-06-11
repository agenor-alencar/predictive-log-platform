package com.logplatform.integration;

import com.logplatform.config.TestContainersConfig;
import com.logplatform.domain.model.PredictionResult;
import com.logplatform.domain.port.MlServicePort;
import com.logplatform.dto.ErrorPredictionRequest;
import com.logplatform.dto.ErrorPredictionResponse;
import com.logplatform.repository.PredictionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

class PredictionAuditIntegrationTest extends TestContainersConfig {

    @MockBean
    private MlServicePort mlServicePort;

    @Autowired
    private PredictionRepository predictionRepository;

    @BeforeEach
    void cleanDatabase() {
        predictionRepository.deleteAll();
    }

    @Test
    void predictError_savesAuditRecordInDatabase() {
        when(mlServicePort.predictError(anyString(), anyInt(), anyDouble(), anyInt()))
                .thenReturn(PredictionResult.errorPrediction(0.15, "LOW", "RandomForestClassifier"));

        ErrorPredictionRequest request = new ErrorPredictionRequest();
        request.setMethod("GET");
        request.setHour(14);
        request.setHistoricalAvgResponse(250.0);
        request.setDayOfWeek(2);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        ResponseEntity<ErrorPredictionResponse> response = restTemplate.postForEntity(
                baseUrl + "/predict/error",
                new HttpEntity<>(request, headers),
                ErrorPredictionResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(predictionRepository.count()).isEqualTo(1);
    }

    @Test
    void predictError_returnsCorrectPredictionValues() {
        PredictionResult fakeResult = PredictionResult.errorPrediction(0.87, "CRITICAL", "XGBoostClassifier");
        fakeResult.setInferenceTimeMs(5.2);
        when(mlServicePort.predictError(anyString(), anyInt(), anyDouble(), anyInt()))
                .thenReturn(fakeResult);

        ErrorPredictionRequest request = new ErrorPredictionRequest();
        request.setMethod("POST");
        request.setHour(3);
        request.setHistoricalAvgResponse(5000.0);
        request.setDayOfWeek(0);

        ResponseEntity<ErrorPredictionResponse> response = restTemplate.postForEntity(
                baseUrl + "/predict/error",
                new HttpEntity<>(request, new HttpHeaders() {{ setContentType(MediaType.APPLICATION_JSON); }}),
                ErrorPredictionResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        ErrorPredictionResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.getErrorProbability()).isEqualTo(0.87);
        assertThat(body.getRiskLevel()).isEqualTo("CRITICAL");
        assertThat(body.getModelUsed()).isEqualTo("XGBoostClassifier");
    }

    @Test
    void predictError_multipleCalls_savesAllAuditRecords() {
        when(mlServicePort.predictError(anyString(), anyInt(), anyDouble(), anyInt()))
                .thenReturn(PredictionResult.errorPrediction(0.10, "LOW", "LogisticRegression"));

        ErrorPredictionRequest request = new ErrorPredictionRequest();
        request.setMethod("GET");
        request.setHour(10);
        request.setHistoricalAvgResponse(150.0);
        request.setDayOfWeek(1);

        HttpEntity<ErrorPredictionRequest> entity = new HttpEntity<>(request, new HttpHeaders() {{
            setContentType(MediaType.APPLICATION_JSON);
        }});

        for (int i = 0; i < 3; i++) {
            restTemplate.postForEntity(baseUrl + "/predict/error", entity, ErrorPredictionResponse.class);
        }

        assertThat(predictionRepository.count()).isEqualTo(3);
    }
}
