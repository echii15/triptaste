package com.hmdp.skill.core;

import lombok.Data;

@Data
public class SkillResult {
    private boolean success;
    private String skillName;
    private String message;
    private Object data;

    public static SkillResult ok(String skillName, Object data) {
        SkillResult result = new SkillResult();
        result.setSuccess(true);
        result.setSkillName(skillName);
        result.setData(data);
        return result;
    }

    public static SkillResult fail(String skillName, String message) {
        SkillResult result = new SkillResult();
        result.setSuccess(false);
        result.setSkillName(skillName);
        result.setMessage(message);
        return result;
    }
}
