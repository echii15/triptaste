package com.hmdp.risk.domain.rule;

import com.hmdp.risk.domain.model.ChecklistItem;
import com.hmdp.risk.domain.model.RiskEventContext;
import com.hmdp.risk.domain.model.RiskPoint;
import org.springframework.stereotype.Component;

@Component
public class InventoryDeductRiskRule implements RiskRule {

    @Override
    public boolean supports(RiskEventContext context) {
        return true;
    }

    @Override
    public RiskRuleResult evaluate(RiskEventContext context) {
        String desc = context.getEventDesc() == null ? "" : context.getEventDesc();
        if (!containsAny(desc, "秒杀", "优惠券", "库存", "抢购", "订单创建", "下单")) {
            return RiskRuleResult.empty();
        }
        return RiskRuleResult.of(
                3,
                new RiskPoint("INVENTORY_DEDUCT", "Redis/MySQL", "活动涉及库存扣减或订单创建，存在超卖、重复下单和库存热点风险", 3),
                "库存链路建议采用 Redis + Lua 原子校验，数据库扣减增加 stock > 0 条件，并确认订单创建幂等",
                new ChecklistItem("库存检查", "确认库存扣减、订单创建和一人一单逻辑具备原子性与幂等性", "HIGH")
        );
    }

    private boolean containsAny(String text, String... words) {
        for (String word : words) {
            if (text.contains(word)) {
                return true;
            }
        }
        return false;
    }
}
