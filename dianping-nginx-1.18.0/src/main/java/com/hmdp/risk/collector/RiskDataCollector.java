package com.hmdp.risk.collector;

import com.hmdp.risk.domain.model.RiskEventContext;

public interface RiskDataCollector {
    String supportType();

    RiskCollectedData collect(RiskEventContext context);
}
