package com.hmdp.skill.application;

import com.hmdp.skill.core.SkillContext;
import com.hmdp.skill.core.SkillResult;
import com.hmdp.skill.dto.SkillAgentRequest;
import com.hmdp.skill.dto.SkillAgentResponse;
import com.hmdp.skill.executor.SkillExecutor;
import com.hmdp.skill.profile.UserSkillProfileService;
import com.hmdp.skill.router.SkillCall;
import com.hmdp.skill.router.SkillRoutePlan;
import com.hmdp.skill.router.SkillRouter;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

@Service
public class SkillAgentService {

    @Resource
    private SkillRouter skillRouter;
    @Resource
    private SkillExecutor skillExecutor;
    @Resource
    private UserSkillProfileService userSkillProfileService;

    public SkillAgentResponse chat(SkillAgentRequest request) {
        SkillRoutePlan plan = skillRouter.route(request.getUserInput(), request.getParams());
        SkillAgentResponse response = new SkillAgentResponse();
        response.setPlan(plan);
        for (SkillCall call : plan.getCalls()) {
            SkillContext context = new SkillContext();
            context.setUserId(request.getUserId());
            context.setUserInput(request.getUserInput());
            context.setParams(call.getParams());
            context.setProfile(userSkillProfileService.loadProfile(request.getUserId()));
            SkillResult result = skillExecutor.execute(call.getSkillName(), context);
            response.getResults().add(result);
        }
        response.setFinalAnswer(buildAnswer(response));
        return response;
    }

    private String buildAnswer(SkillAgentResponse response) {
        if (response.getResults().isEmpty()) {
            return "没有找到可调用的 Skill。";
        }
        SkillResult first = response.getResults().get(0);
        if (!first.isSuccess()) {
            return first.getMessage();
        }
        String intent = response.getPlan() == null ? "" : response.getPlan().getIntent();
        if ("ORDER_DRAFT".equals(intent)) {
            return "已生成订单草稿，需用户确认后才能继续校验库存、价格、优惠券并进入支付。";
        }
        if ("REVIEW_RISK_CHECK".equals(intent)) {
            return "已完成内容风控检查，请查看风险等级、原因和修改建议。";
        }
        if ("SHOP_SUMMARY".equals(intent)) {
            return "已生成店铺口碑总结。";
        }
        return "已根据你的需求完成推荐。";
    }
}
