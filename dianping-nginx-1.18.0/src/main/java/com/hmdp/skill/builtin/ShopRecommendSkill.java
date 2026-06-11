package com.hmdp.skill.builtin;

import com.hmdp.dto.Result;
import com.hmdp.dto.ai.AiAssistantRequestDTO;
import com.hmdp.service.IAiService;
import com.hmdp.skill.core.SkillContext;
import com.hmdp.skill.core.SkillResult;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

@Component
public class ShopRecommendSkill extends AbstractSkill {

    @Resource
    private IAiService aiService;

    @Override
    public String name() {
        return "shop_recommend_skill";
    }

    @Override
    public String type() {
        return "SHOP";
    }

    @Override
    public String description() {
        return "根据自然语言需求、位置、品类偏好推荐附近店铺并生成理由";
    }

    @Override
    public String inputSchema() {
        return "{\"query\":\"string\",\"x\":\"double\",\"y\":\"double\",\"currentTypeId\":\"long\"}";
    }

    @Override
    public String outputSchema() {
        return "{\"query\":\"string\",\"intentSummary\":\"string\",\"keywords\":\"array\",\"recommendShops\":\"array\"}";
    }

    @Override
    public String permissionLevel() {
        return "LOW";
    }

    @Override
    public SkillResult execute(SkillContext context) {
        String query = strParam(context.getParams(), "query");
        if (query == null || query.trim().isEmpty()) {
            query = context.getUserInput();
        }
        if (query == null || query.trim().isEmpty()) {
            return SkillResult.fail(name(), "query 不能为空");
        }
        AiAssistantRequestDTO request = new AiAssistantRequestDTO();
        request.setQuery(query);
        request.setX(doubleParam(context.getParams(), "x"));
        request.setY(doubleParam(context.getParams(), "y"));
        request.setCurrentTypeId(longParam(context.getParams(), "currentTypeId"));
        Result result = aiService.assistantRecommend(request);
        return Boolean.TRUE.equals(result.getSuccess())
                ? SkillResult.ok(name(), result.getData())
                : SkillResult.fail(name(), result.getErrorMsg());
    }
}
