package com.hmdp.risk.ai;

import com.hmdp.config.properties.AiProperties;
import com.hmdp.risk.ai.dto.DdlRiskAiRequest;
import com.hmdp.risk.ai.dto.RiskAiAnalyzeRequest;
import com.hmdp.risk.ai.dto.RiskAiAnalyzeResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Slf4j
@Component
public class RiskAiClientImpl implements RiskAiClient {

    private final RestTemplate restTemplate;
    private final AiProperties aiProperties;

    public RiskAiClientImpl(@Qualifier("aiRestTemplate") RestTemplate restTemplate, AiProperties aiProperties) {
        this.restTemplate = restTemplate;
        this.aiProperties = aiProperties;
    }

    @Override
    public RiskAiAnalyzeResponse analyze(RiskAiAnalyzeRequest request) {
        return post("/internal/ai/risk/analyze", request);
    }

    @Override
    public RiskAiAnalyzeResponse checkDdl(DdlRiskAiRequest request) {
        return post("/internal/ai/risk/ddl-check", request);
    }

    private RiskAiAnalyzeResponse post(String path, Object body) {
        try {
            ResponseEntity<RiskAiAnalyzeResponse> response = restTemplate.postForEntity(
                    aiProperties.getBaseUrl() + path,
                    body,
                    RiskAiAnalyzeResponse.class
            );
            return response.getBody();
        } catch (Exception e) {
            log.warn("call risk ai failed, path={}, err={}", path, e.getMessage());
            return null;
        }
    }
}
