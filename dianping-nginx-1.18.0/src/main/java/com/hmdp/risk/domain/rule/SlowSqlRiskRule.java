package com.hmdp.risk.domain.rule;

import com.hmdp.risk.domain.model.ChecklistItem;
import com.hmdp.risk.domain.model.RiskEventContext;
import com.hmdp.risk.domain.model.RiskPoint;
import org.springframework.stereotype.Component;

@Component
public class SlowSqlRiskRule implements RiskRule {

    @Override
    public boolean supports(RiskEventContext context) {
        return context.getSlowSqlList() != null && !context.getSlowSqlList().isEmpty();
    }

    @Override
    public RiskRuleResult evaluate(RiskEventContext context) {
        return RiskRuleResult.of(
                3,
                new RiskPoint("SLOW_SQL", "MySQL", "活动链路存在慢 SQL，流量放大后可能拖垮连接池和核心接口 RT", 3),
                "对慢 SQL 执行 EXPLAIN，优先优化索引、分页和回表问题",
                new ChecklistItem("数据库检查", "检查活动链路慢 SQL、执行计划和连接池水位", "HIGH")
        );
    }
}
