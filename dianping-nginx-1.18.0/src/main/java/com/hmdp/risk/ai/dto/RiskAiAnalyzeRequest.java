package com.hmdp.risk.ai.dto;

import com.hmdp.risk.domain.model.RiskEventContext;
import com.hmdp.risk.domain.model.RiskPoint;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class RiskAiAnalyzeRequest {
    private RiskEventContext context;
    private Integer ruleScore;
    private List<RiskPoint> ruleRiskPoints = new ArrayList<>();
}
