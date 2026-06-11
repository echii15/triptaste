package com.hmdp.risk.ai;

import com.hmdp.risk.ai.dto.DdlRiskAiRequest;
import com.hmdp.risk.ai.dto.RiskAiAnalyzeRequest;
import com.hmdp.risk.ai.dto.RiskAiAnalyzeResponse;

public interface RiskAiClient {
    RiskAiAnalyzeResponse analyze(RiskAiAnalyzeRequest request);

    RiskAiAnalyzeResponse checkDdl(DdlRiskAiRequest request);
}
