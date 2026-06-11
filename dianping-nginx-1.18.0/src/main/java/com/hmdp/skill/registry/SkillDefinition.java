package com.hmdp.skill.registry;

import lombok.Data;

@Data
public class SkillDefinition {
    private String skillName;
    private String skillType;
    private String description;
    private String inputSchema;
    private String outputSchema;
    private String permissionLevel;
    private Boolean enabled;
    private String version;
}
