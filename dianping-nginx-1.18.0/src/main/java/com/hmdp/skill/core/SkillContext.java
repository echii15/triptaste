package com.hmdp.skill.core;

import lombok.Data;

import java.util.HashMap;
import java.util.Map;

@Data
public class SkillContext {
    private Long userId;
    private String userInput;
    private Map<String, Object> params = new HashMap<>();
    private Map<String, Object> profile = new HashMap<>();
}
