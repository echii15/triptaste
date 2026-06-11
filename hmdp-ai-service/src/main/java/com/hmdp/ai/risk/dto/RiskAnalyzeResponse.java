package com.hmdp.ai.risk.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class RiskAnalyzeResponse {
    private String riskLevel;
    private Integer riskScore;
    private List<RiskPoint> riskPoints = new ArrayList<>();
    private List<String> impactComponents = new ArrayList<>();
    private List<String> suggestions = new ArrayList<>();
    private List<ChecklistItem> checklist = new ArrayList<>();
    private List<String> fallbackPlan = new ArrayList<>();
    private String engine;
}
