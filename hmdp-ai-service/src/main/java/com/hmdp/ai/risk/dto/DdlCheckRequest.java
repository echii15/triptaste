package com.hmdp.ai.risk.dto;

import lombok.Data;

@Data
public class DdlCheckRequest {
    private String ddl;
    private RiskEventContext context;
    private Integer ruleScore;
}
