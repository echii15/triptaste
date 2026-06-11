package com.hmdp.risk.knowledge;

import lombok.Data;

@Data
public class RiskKnowledge {
    private String scene;
    private String riskType;
    private String component;
    private String symptom;
    private String checkItems;
    private String solutions;
}
