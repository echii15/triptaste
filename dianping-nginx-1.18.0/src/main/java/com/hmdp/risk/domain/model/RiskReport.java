package com.hmdp.risk.domain.model;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
public class RiskReport {
    private String eventName;
    private String eventDesc;
    private RiskLevel riskLevel;
    private Integer riskScore;
    private List<RiskPoint> riskPoints = new ArrayList<>();
    private List<String> impactComponents = new ArrayList<>();
    private List<String> suggestions = new ArrayList<>();
    private List<ChecklistItem> checklist = new ArrayList<>();
    private List<String> fallbackPlan = new ArrayList<>();
    private String engine;
    private LocalDateTime generatedAt;
}
