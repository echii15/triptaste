package com.hmdp.risk.ai.dto;

import com.hmdp.risk.domain.model.RiskEventContext;
import lombok.Data;

@Data
public class DdlRiskAiRequest {
    private String ddl;
    private RiskEventContext context;
    private Integer ruleScore;
}
