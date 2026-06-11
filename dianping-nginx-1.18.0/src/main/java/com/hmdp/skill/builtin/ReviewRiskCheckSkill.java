package com.hmdp.skill.builtin;

import com.hmdp.dto.Result;
import com.hmdp.dto.ai.AiReviewRiskCheckRequestDTO;
import com.hmdp.service.IAiService;
import com.hmdp.skill.core.SkillContext;
import com.hmdp.skill.core.SkillResult;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

@Component
public class ReviewRiskCheckSkill extends AbstractSkill {

    @Resource
    private IAiService aiService;

    @Override
    public String name() {
        return "review_risk_check_skill";
    }

    @Override
    public String type() {
        return "CONTENT";
    }

    @Override
    public String description() {
        return "对点评、笔记或评论草稿进行广告、隐私、违法违规和攻击性内容风控";
    }

    @Override
    public String inputSchema() {
        return "{\"scene\":\"string\",\"title\":\"string\",\"content\":\"string\",\"shopId\":\"long\"}";
    }

    @Override
    public String outputSchema() {
        return "{\"pass\":\"boolean\",\"riskLevel\":\"string\",\"riskScore\":\"int\",\"riskTags\":\"array\",\"reasons\":\"array\",\"suggestion\":\"string\"}";
    }

    @Override
    public String permissionLevel() {
        return "LOW";
    }

    @Override
    public SkillResult execute(SkillContext context) {
        String content = strParam(context.getParams(), "content");
        if (content == null || content.trim().isEmpty()) {
            content = context.getUserInput();
        }
        if (content == null || content.trim().isEmpty()) {
            return SkillResult.fail(name(), "content 不能为空");
        }
        AiReviewRiskCheckRequestDTO request = new AiReviewRiskCheckRequestDTO();
        request.setScene(strParam(context.getParams(), "scene"));
        request.setTitle(strParam(context.getParams(), "title"));
        request.setContent(content);
        request.setShopId(longParam(context.getParams(), "shopId"));
        Result result = aiService.checkReviewRisk(request);
        return Boolean.TRUE.equals(result.getSuccess())
                ? SkillResult.ok(name(), result.getData())
                : SkillResult.fail(name(), result.getErrorMsg());
    }
}
