package com.hmdp.skill.core;

public interface Skill {
    String name();

    String type();

    String description();

    String inputSchema();

    String outputSchema();

    String permissionLevel();

    SkillResult execute(SkillContext context);
}
