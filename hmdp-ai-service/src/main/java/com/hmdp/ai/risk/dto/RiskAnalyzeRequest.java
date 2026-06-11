package com.hmdp.ai.risk.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class RiskAnalyzeRequest {
    private RiskEventContext context;
    private Integer ruleScore;
    private List<RiskPoint> ruleRiskPoints = new ArrayList<>();
}
