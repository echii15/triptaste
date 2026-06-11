package com.hmdp.risk.ai.dto;

import com.hmdp.risk.domain.model.ChecklistItem;
import com.hmdp.risk.domain.model.RiskPoint;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class RiskAiAnalyzeResponse {
    private String riskLevel;
    private Integer riskScore;
    private List<RiskPoint> riskPoints = new ArrayList<>();
    private List<String> impactComponents = new ArrayList<>();
    private List<String> suggestions = new ArrayList<>();
    private List<ChecklistItem> checklist = new ArrayList<>();
    private List<String> fallbackPlan = new ArrayList<>();
    private String engine;
}
