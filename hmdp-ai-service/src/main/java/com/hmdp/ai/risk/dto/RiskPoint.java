package com.hmdp.ai.risk.dto;

import lombok.Data;

@Data
public class RiskPoint {
    private String type;
    private String component;
    private String reason;
    private Integer score;
}
