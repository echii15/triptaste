package com.hmdp.skill.executor;

import com.hmdp.skill.core.Skill;
import com.hmdp.skill.core.SkillContext;
import com.hmdp.skill.core.SkillResult;
import com.hmdp.skill.registry.SkillRegistryService;
import org.springframework.stereotype.Service;

@Service
public class SkillExecutor {

    private final SkillRegistryService skillRegistryService;

    public SkillExecutor(SkillRegistryService skillRegistryService) {
        this.skillRegistryService = skillRegistryService;
    }

    public SkillResult execute(String skillName, SkillContext context) {
        Skill skill = skillRegistryService.findExecutableSkill(skillName).orElse(null);
        if (skill == null) {
            return SkillResult.fail(skillName, "Skill 不存在或已禁用");
        }
        if ("HIGH".equalsIgnoreCase(skill.permissionLevel())) {
            Object confirmed = context.getParams().get("confirmed");
            if (!Boolean.TRUE.equals(confirmed)) {
                return SkillResult.fail(skillName, "高风险 Skill 需要用户显式确认");
            }
        }
        return skill.execute(context);
    }
}
