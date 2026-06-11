package com.hmdp.skill.builtin;

import com.hmdp.skill.core.SkillContext;
import com.hmdp.skill.core.SkillResult;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class OrderDraftSkill extends AbstractSkill {

    @Override
    public String name() {
        return "order_draft_skill";
    }

    @Override
    public String type() {
        return "ORDER";
    }

    @Override
    public String description() {
        return "生成订单草稿。只能给出草稿和校验建议，不能自动支付或确认订单";
    }

    @Override
    public String inputSchema() {
        return "{\"shopId\":\"long\",\"voucherId\":\"long\",\"items\":\"array\",\"confirmed\":\"boolean\"}";
    }

    @Override
    public String outputSchema() {
        return "{\"draftOnly\":\"boolean\",\"shopId\":\"long\",\"voucherId\":\"long\",\"needUserConfirm\":\"boolean\",\"safetyNotice\":\"string\"}";
    }

    @Override
    public String permissionLevel() {
        return "HIGH";
    }

    @Override
    public SkillResult execute(SkillContext context) {
        Map<String, Object> draft = new LinkedHashMap<>();
        draft.put("draftOnly", true);
        draft.put("shopId", longParam(context.getParams(), "shopId"));
        draft.put("voucherId", longParam(context.getParams(), "voucherId"));
        draft.put("items", context.getParams().get("items"));
        draft.put("needUserConfirm", true);
        draft.put("safetyNotice", "该 Skill 只生成订单草稿。价格、库存、优惠券资格和支付必须由服务端重新校验并由用户确认。");
        return SkillResult.ok(name(), draft);
    }
}
