package com.hmdp.skill.dto;

import lombok.Data;

import java.util.HashMap;
import java.util.Map;

@Data
public class SkillAgentRequest {
    private Long userId;
    private String userInput;
    private Map<String, Object> params = new HashMap<>();
}
