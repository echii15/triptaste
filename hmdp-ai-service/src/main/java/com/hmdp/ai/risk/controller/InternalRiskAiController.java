package com.hmdp.ai.risk.controller;

import com.hmdp.ai.risk.dto.DdlCheckRequest;
import com.hmdp.ai.risk.dto.RiskAnalyzeRequest;
import com.hmdp.ai.risk.dto.RiskAnalyzeResponse;
import com.hmdp.ai.risk.service.RiskAiOrchestrationService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/internal/ai/risk")
public class InternalRiskAiController {

    private final RiskAiOrchestrationService riskAiOrchestrationService;

    public InternalRiskAiController(RiskAiOrchestrationService riskAiOrchestrationService) {
        this.riskAiOrchestrationService = riskAiOrchestrationService;
    }

    @PostMapping("/analyze")
    public RiskAnalyzeResponse analyze(@RequestBody RiskAnalyzeRequest request) {
        return riskAiOrchestrationService.analyze(request);
    }

    @PostMapping("/ddl-check")
    public RiskAnalyzeResponse ddlCheck(@RequestBody DdlCheckRequest request) {
        return riskAiOrchestrationService.checkDdl(request);
    }
}
