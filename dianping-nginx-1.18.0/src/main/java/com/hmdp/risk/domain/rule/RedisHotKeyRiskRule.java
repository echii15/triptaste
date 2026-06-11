package com.hmdp.risk.domain.rule;

import com.hmdp.risk.domain.model.ChecklistItem;
import com.hmdp.risk.domain.model.RiskEventContext;
import com.hmdp.risk.domain.model.RiskPoint;
import org.springframework.stereotype.Component;

@Component
public class RedisHotKeyRiskRule implements RiskRule {

    @Override
    public boolean supports(RiskEventContext context) {
        return context.getRedisSignals() != null && !context.getRedisSignals().isEmpty();
    }

    @Override
    public RiskRuleResult evaluate(RiskEventContext context) {
        for (String signal : context.getRedisSignals()) {
            if (signal.contains("库存") || signal.contains("热 Key") || signal.contains("seckill:stock")) {
                return RiskRuleResult.of(
                        3,
                        new RiskPoint("REDIS_HOT_KEY", "Redis", "活动命中库存/秒杀 Redis Key，热点 Key 可能造成 Redis 单分片压力", 3),
                        "对库存 Key 做分桶或本地热点缓存，活动前完成商品和店铺缓存预热",
                        new ChecklistItem("Redis 检查", "检查库存 Key QPS、分桶方案、缓存预热和过期时间", "HIGH")
                );
            }
        }
        return RiskRuleResult.empty();
    }
}
