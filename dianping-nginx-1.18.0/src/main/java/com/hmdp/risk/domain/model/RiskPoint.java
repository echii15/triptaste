package com.hmdp.risk.domain.model;

import lombok.Data;

@Data
public class RiskPoint {
    private String type;
    private String component;
    private String reason;
    private Integer score;

    public RiskPoint() {
    }

    public RiskPoint(String type, String component, String reason, Integer score) {
        this.type = type;
        this.component = component;
        this.reason = reason;
        this.score = score;
    }
}
