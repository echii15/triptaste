package com.hmdp.skill.router;

import lombok.Data;

import java.util.HashMap;
import java.util.Map;

@Data
public class SkillCall {
    private String skillName;
    private Map<String, Object> params = new HashMap<>();
}
