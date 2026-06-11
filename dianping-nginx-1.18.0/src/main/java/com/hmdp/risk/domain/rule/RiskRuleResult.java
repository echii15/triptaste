package com.hmdp.risk.domain.rule;

import com.hmdp.risk.domain.model.ChecklistItem;
import com.hmdp.risk.domain.model.RiskPoint;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class RiskRuleResult {
    private int score;
    private List<RiskPoint> riskPoints = new ArrayList<>();
    private List<String> suggestions = new ArrayList<>();
    private List<ChecklistItem> checklist = new ArrayList<>();

    public static RiskRuleResult empty() {
        return new RiskRuleResult();
    }

    public static RiskRuleResult of(int score, RiskPoint point, String suggestion, ChecklistItem checklistItem) {
        RiskRuleResult result = new RiskRuleResult();
        result.setScore(score);
        if (point != null) {
            result.getRiskPoints().add(point);
        }
        if (suggestion != null && suggestion.trim().length() > 0) {
            result.getSuggestions().add(suggestion);
        }
        if (checklistItem != null) {
            result.getChecklist().add(checklistItem);
        }
        return result;
    }
}
