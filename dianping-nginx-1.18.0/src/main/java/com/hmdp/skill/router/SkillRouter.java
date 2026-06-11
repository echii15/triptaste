package com.hmdp.skill.router;

import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class SkillRouter {

    public SkillRoutePlan route(String userInput, Map<String, Object> params) {
        SkillRoutePlan plan = new SkillRoutePlan();
        String text = userInput == null ? "" : userInput;
        if (containsAny(text, "风控", "质检", "违规", "广告", "隐私", "评价安全吗")) {
            plan.setIntent("REVIEW_RISK_CHECK");
            plan.getCalls().add(call("review_risk_check_skill", params));
            return plan;
        }
        if (containsAny(text, "总结", "口碑", "这家店怎么样", "评价如何") || params.containsKey("shopId") && containsAny(text, "店", "口碑")) {
            plan.setIntent("SHOP_SUMMARY");
            plan.getCalls().add(call("shop_summary_skill", params));
            return plan;
        }
        if (containsAny(text, "下单", "订单", "锁定", "购买")) {
            plan.setIntent("ORDER_DRAFT");
            plan.getCalls().add(call("order_draft_skill", params));
            return plan;
        }
        plan.setIntent("SHOP_RECOMMEND");
        Map<String, Object> merged = new java.util.HashMap<>(params);
        merged.putIfAbsent("query", text);
        plan.getCalls().add(call("shop_recommend_skill", merged));
        return plan;
    }

    private SkillCall call(String skillName, Map<String, Object> params) {
        SkillCall call = new SkillCall();
        call.setSkillName(skillName);
        call.setParams(params == null ? new java.util.HashMap<>() : new java.util.HashMap<>(params));
        return call;
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
