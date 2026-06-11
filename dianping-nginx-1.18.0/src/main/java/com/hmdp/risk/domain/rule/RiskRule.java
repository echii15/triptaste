package com.hmdp.risk.domain.rule;

import com.hmdp.risk.domain.model.RiskEventContext;

public interface RiskRule {
    boolean supports(RiskEventContext context);

    RiskRuleResult evaluate(RiskEventContext context);
}
