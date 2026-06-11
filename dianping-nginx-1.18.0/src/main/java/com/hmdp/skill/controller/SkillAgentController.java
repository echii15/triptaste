package com.hmdp.skill.controller;

import com.hmdp.dto.Result;
import com.hmdp.skill.application.SkillAgentService;
import com.hmdp.skill.core.SkillContext;
import com.hmdp.skill.dto.SkillAgentRequest;
import com.hmdp.skill.dto.SkillExecuteRequest;
import com.hmdp.skill.dto.SkillFeedbackRequest;
import com.hmdp.skill.executor.SkillExecutor;
import com.hmdp.skill.profile.UserSkillProfileService;
import com.hmdp.skill.registry.SkillRegistryService;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;

@RestController
@RequestMapping("/skill")
public class SkillAgentController {

    @Resource
    private SkillRegistryService skillRegistryService;
    @Resource
    private SkillExecutor skillExecutor;
    @Resource
    private SkillAgentService skillAgentService;
    @Resource
    private UserSkillProfileService userSkillProfileService;

    @GetMapping("/registry")
    public Result registry() {
        return Result.ok(skillRegistryService.listDefinitions());
    }

    @PostMapping("/execute")
    public Result execute(@RequestBody SkillExecuteRequest request) {
        if (request == null || request.getSkillName() == null || request.getSkillName().trim().isEmpty()) {
            return Result.fail("skillName cannot be blank");
        }
        SkillContext context = new SkillContext();
        context.setUserId(request.getUserId());
        context.setUserInput(request.getUserInput());
        context.setParams(request.getParams());
        context.setProfile(userSkillProfileService.loadProfile(request.getUserId()));
        return Result.ok(skillExecutor.execute(request.getSkillName(), context));
    }

    @PostMapping("/agent/chat")
    public Result chat(@RequestBody SkillAgentRequest request) {
        if (request == null || request.getUserInput() == null || request.getUserInput().trim().isEmpty()) {
            return Result.fail("userInput cannot be blank");
        }
        return Result.ok(skillAgentService.chat(request));
    }

    @PostMapping("/feedback")
    public Result feedback(@RequestBody SkillFeedbackRequest request) {
        userSkillProfileService.updateByFeedback(request);
        return Result.ok();
    }
}
