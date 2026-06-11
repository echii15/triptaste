package com.hmdp.risk.domain.scorer;

import com.hmdp.risk.domain.model.ChecklistItem;
import com.hmdp.risk.domain.model.RiskEventContext;
import com.hmdp.risk.domain.model.RiskPoint;
import com.hmdp.risk.domain.rule.RiskRule;
import com.hmdp.risk.domain.rule.RiskRuleResult;
import lombok.Data;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class RiskScoreEngine {

    private final List<RiskRule> rules;

    public RiskScoreEngine(List<RiskRule> rules) {
        this.rules = rules;
    }

    public ScoreResult evaluate(RiskEventContext context) {
        ScoreResult result = new ScoreResult();
        for (RiskRule rule : rules) {
            if (!rule.supports(context)) {
                continue;
            }
            RiskRuleResult ruleResult = rule.evaluate(context);
            if (ruleResult == null) {
                continue;
            }
            result.setScore(result.getScore() + ruleResult.getScore());
            result.getRiskPoints().addAll(ruleResult.getRiskPoints());
            result.getSuggestions().addAll(ruleResult.getSuggestions());
            result.getChecklist().addAll(ruleResult.getChecklist());
        }
        return result;
    }

    @Data
    public static class ScoreResult {
        private int score;
        private List<RiskPoint> riskPoints = new ArrayList<>();
        private List<String> suggestions = new ArrayList<>();
        private List<ChecklistItem> checklist = new ArrayList<>();
    }
}
