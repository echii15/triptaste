package com.hmdp.skill.registry;

import com.hmdp.skill.core.Skill;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class SkillRegistryService {

    private final Map<String, Skill> builtinSkillMap;

    @Resource
    private JdbcTemplate jdbcTemplate;

    public SkillRegistryService(List<Skill> skills) {
        this.builtinSkillMap = skills.stream().collect(Collectors.toMap(Skill::name, s -> s, (a, b) -> a, LinkedHashMap::new));
    }

    public Optional<Skill> findExecutableSkill(String skillName) {
        Skill skill = builtinSkillMap.get(skillName);
        if (skill == null) {
            return Optional.empty();
        }
        SkillDefinition definition = getDefinition(skillName);
        if (definition != null && Boolean.FALSE.equals(definition.getEnabled())) {
            return Optional.empty();
        }
        return Optional.of(skill);
    }

    public List<SkillDefinition> listDefinitions() {
        Map<String, SkillDefinition> definitions = new LinkedHashMap<>();
        for (Skill skill : builtinSkillMap.values()) {
            SkillDefinition definition = fromSkill(skill);
            definitions.put(definition.getSkillName(), definition);
        }
        for (SkillDefinition dbDefinition : loadDbDefinitions()) {
            definitions.put(dbDefinition.getSkillName(), dbDefinition);
        }
        return new ArrayList<>(definitions.values());
    }

    public SkillDefinition getDefinition(String skillName) {
        for (SkillDefinition definition : loadDbDefinitions()) {
            if (skillName.equals(definition.getSkillName())) {
                return definition;
            }
        }
        Skill skill = builtinSkillMap.get(skillName);
        return skill == null ? null : fromSkill(skill);
    }

    private List<SkillDefinition> loadDbDefinitions() {
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    "SELECT skill_name,skill_type,description,input_schema,output_schema,permission_level,enabled,version FROM ai_skill_registry"
            );
            List<SkillDefinition> list = new ArrayList<>();
            for (Map<String, Object> row : rows) {
                SkillDefinition definition = new SkillDefinition();
                definition.setSkillName(str(row.get("skill_name")));
                definition.setSkillType(str(row.get("skill_type")));
                definition.setDescription(str(row.get("description")));
                definition.setInputSchema(str(row.get("input_schema")));
                definition.setOutputSchema(str(row.get("output_schema")));
                definition.setPermissionLevel(str(row.get("permission_level")));
                definition.setEnabled(toBoolean(row.get("enabled")));
                definition.setVersion(str(row.get("version")));
                list.add(definition);
            }
            return list;
        } catch (Exception e) {
            log.debug("load ai_skill_registry failed, use builtin registry, err={}", e.getMessage());
            return Collections.emptyList();
        }
    }

    private SkillDefinition fromSkill(Skill skill) {
        SkillDefinition definition = new SkillDefinition();
        definition.setSkillName(skill.name());
        definition.setSkillType(skill.type());
        definition.setDescription(skill.description());
        definition.setInputSchema(skill.inputSchema());
        definition.setOutputSchema(skill.outputSchema());
        definition.setPermissionLevel(skill.permissionLevel());
        definition.setEnabled(true);
        definition.setVersion("builtin-1.0");
        return definition;
    }

    private String str(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private Boolean toBoolean(Object value) {
        if (value == null) {
            return true;
        }
        if (value instanceof Number) {
            return ((Number) value).intValue() != 0;
        }
        return Boolean.valueOf(String.valueOf(value));
    }
}
