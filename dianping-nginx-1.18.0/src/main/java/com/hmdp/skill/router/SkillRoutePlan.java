package com.hmdp.skill.router;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class SkillRoutePlan {
    private String intent;
    private List<SkillCall> calls = new ArrayList<>();
}
