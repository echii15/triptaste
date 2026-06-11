package com.hmdp.skill.builtin;

import com.hmdp.dto.Result;
import com.hmdp.service.IAiService;
import com.hmdp.skill.core.SkillContext;
import com.hmdp.skill.core.SkillResult;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

@Component
public class ShopSummarySkill extends AbstractSkill {

    @Resource
    private IAiService aiService;

    @Override
    public String name() {
        return "shop_summary_skill";
    }

    @Override
    public String type() {
        return "SHOP";
    }

    @Override
    public String description() {
        return "根据店铺 ID 汇总店铺口碑、亮点和消费建议";
    }

    @Override
    public String inputSchema() {
        return "{\"shopId\":\"long\",\"refresh\":\"boolean\"}";
    }

    @Override
    public String outputSchema() {
        return "{\"shopId\":\"long\",\"finalSummary\":\"string\",\"highFrequencyHighlights\":\"array\",\"uniqueHighlights\":\"array\",\"advice\":\"string\"}";
    }

    @Override
    public String permissionLevel() {
        return "LOW";
    }

    @Override
    public SkillResult execute(SkillContext context) {
        Long shopId = longParam(context.getParams(), "shopId");
        if (shopId == null) {
            return SkillResult.fail(name(), "shopId 不能为空");
        }
        Result result = aiService.getShopSummary(shopId, boolParam(context.getParams(), "refresh"));
        return Boolean.TRUE.equals(result.getSuccess())
                ? SkillResult.ok(name(), result.getData())
                : SkillResult.fail(name(), result.getErrorMsg());
    }
}
