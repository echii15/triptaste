package com.hmdp.skill.dto;

import com.hmdp.skill.core.SkillResult;
import com.hmdp.skill.router.SkillRoutePlan;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class SkillAgentResponse {
    private SkillRoutePlan plan;
    private List<SkillResult> results = new ArrayList<>();
    private String finalAnswer;
}
