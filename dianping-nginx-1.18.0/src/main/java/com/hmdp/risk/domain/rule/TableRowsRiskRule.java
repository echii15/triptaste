package com.hmdp.risk.domain.rule;

import com.hmdp.risk.domain.model.ChecklistItem;
import com.hmdp.risk.domain.model.RiskEventContext;
import com.hmdp.risk.domain.model.RiskPoint;
import com.hmdp.risk.domain.model.TableMetadata;
import org.springframework.stereotype.Component;

@Component
public class TableRowsRiskRule implements RiskRule {

    private static final long LARGE_TABLE_ROWS = 10000000L;

    @Override
    public boolean supports(RiskEventContext context) {
        return context.getTableMetadataList() != null && !context.getTableMetadataList().isEmpty();
    }

    @Override
    public RiskRuleResult evaluate(RiskEventContext context) {
        RiskRuleResult result = RiskRuleResult.empty();
        for (TableMetadata metadata : context.getTableMetadataList()) {
            if (metadata.getTableRows() != null && metadata.getTableRows() >= LARGE_TABLE_ROWS) {
                result.setScore(result.getScore() + 2);
                result.getRiskPoints().add(new RiskPoint(
                        "LARGE_TABLE",
                        "MySQL",
                        metadata.getTableName() + " 表数据量超过 1000 万，活动前 DDL、全表扫描和回表风险升高",
                        2
                ));
                result.getSuggestions().add("对 " + metadata.getTableName() + " 的核心查询执行 EXPLAIN，确认索引命中和扫描行数");
                result.getChecklist().add(new ChecklistItem("数据库检查", "确认 " + metadata.getTableName() + " 大表查询索引与归档策略", "HIGH"));
            }
        }
        return result;
    }
}
