package com.hmdp.skill.dto;

import lombok.Data;

import java.util.HashMap;
import java.util.Map;

@Data
public class SkillFeedbackRequest {
    private Long userId;
    private String feedbackType;
    private String skillName;
    private Map<String, Object> payload = new HashMap<>();
}
