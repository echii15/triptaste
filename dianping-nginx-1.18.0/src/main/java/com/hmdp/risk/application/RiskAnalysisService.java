package com.hmdp.risk.application;

import cn.hutool.json.JSONUtil;
import com.hmdp.risk.ai.RiskAiClient;
import com.hmdp.risk.ai.dto.DdlRiskAiRequest;
import com.hmdp.risk.ai.dto.RiskAiAnalyzeRequest;
import com.hmdp.risk.ai.dto.RiskAiAnalyzeResponse;
import com.hmdp.risk.collector.RiskCollectedData;
import com.hmdp.risk.collector.RiskDataCollector;
import com.hmdp.risk.ddl.DdlParser;
import com.hmdp.risk.ddl.DdlSafetyPolicy;
import com.hmdp.risk.domain.model.ChecklistItem;
import com.hmdp.risk.domain.model.RiskEventContext;
import com.hmdp.risk.domain.model.RiskLevel;
import com.hmdp.risk.domain.model.RiskPoint;
import com.hmdp.risk.domain.model.RiskReport;
import com.hmdp.risk.domain.scorer.RiskScoreEngine;
import com.hmdp.risk.knowledge.RiskKnowledgeService;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Service
public class RiskAnalysisService {

    @Resource
    private EventParser eventParser;
    @Resource
    private List<RiskDataCollector> collectors;
    @Resource
    private RiskKnowledgeService riskKnowledgeService;
    @Resource
    private RiskScoreEngine riskScoreEngine;
    @Resource
    private RiskAiClient riskAiClient;
    @Resource
    private DdlParser ddlParser;
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private JdbcTemplate jdbcTemplate;

    public RiskReport analyze(String eventDesc) {
        RiskEventContext context = eventParser.parse(eventDesc);
        collect(context);
        context.setKnowledgeSnippets(riskKnowledgeService.search(context));

        RiskScoreEngine.ScoreResult scoreResult = riskScoreEngine.evaluate(context);
        RiskAiAnalyzeRequest aiRequest = new RiskAiAnalyzeRequest();
        aiRequest.setContext(context);
        aiRequest.setRuleScore(scoreResult.getScore());
        aiRequest.setRuleRiskPoints(scoreResult.getRiskPoints());
        RiskAiAnalyzeResponse aiResponse = riskAiClient.analyze(aiRequest);

        RiskReport report = mergeReport(context, scoreResult, aiResponse);
        persistEvent(context, report);
        cacheReport("risk:report:" + context.getEventName() + ":" + System.currentTimeMillis(), report);
        return report;
    }

    public RiskReport checkDdl(String ddl) {
        RiskEventContext context = eventParser.parse("DDL 风险分析");
        context.setDdl(ddl);
        context.setEventName("DDL 风险分析");
        context.setEventType("DDL");
        collect(context);

        RiskScoreEngine.ScoreResult scoreResult = new RiskScoreEngine.ScoreResult();
        if (ddl == null || ddl.trim().isEmpty()) {
            scoreResult.setScore(3);
            scoreResult.getRiskPoints().add(new RiskPoint("DDL_EMPTY", "MySQL", "DDL 内容为空，无法进行上线评审", 3));
        } else if (DdlSafetyPolicy.containsForbiddenSql(ddl)) {
            scoreResult.setScore(10);
            scoreResult.getRiskPoints().add(new RiskPoint("FORBIDDEN_SQL", "MySQL", "输入包含 DROP/DELETE/UPDATE/INSERT/TRUNCATE 等高危语句，系统仅允许分析不允许执行", 10));
        } else if (!DdlSafetyPolicy.isAlterTable(ddl)) {
            scoreResult.setScore(4);
            scoreResult.getRiskPoints().add(new RiskPoint("UNKNOWN_DDL", "MySQL", "非 ALTER TABLE 类型语句，需人工确认变更影响", 4));
        } else {
            String tableName = ddlParser.extractTableName(ddl);
            if (ddlParser.addNotNullColumn(ddl)) {
                scoreResult.setScore(scoreResult.getScore() + 3);
                scoreResult.getRiskPoints().add(new RiskPoint("ADD_NOT_NULL_COLUMN", "MySQL", "新增 NOT NULL 字段可能触发表重建或长时间锁表", 3));
            }
            if (ddlParser.modifyColumnType(ddl)) {
                scoreResult.setScore(scoreResult.getScore() + 3);
                scoreResult.getRiskPoints().add(new RiskPoint("MODIFY_COLUMN", "MySQL", "修改字段类型可能触发表重建，影响读写", 3));
            }
            scoreResult.getChecklist().add(new ChecklistItem("DDL 检查", "确认 " + tableName + " 表数据量、执行窗口和回滚方案", "HIGH"));
            scoreResult.getSuggestions().add("大表 DDL 建议采用 nullable 字段、应用双写、后台分批回填和校验后切换读流量");
        }

        DdlRiskAiRequest aiRequest = new DdlRiskAiRequest();
        aiRequest.setDdl(ddl);
        aiRequest.setContext(context);
        aiRequest.setRuleScore(scoreResult.getScore());
        RiskAiAnalyzeResponse aiResponse = riskAiClient.checkDdl(aiRequest);
        RiskReport report = mergeReport(context, scoreResult, aiResponse);
        persistEvent(context, report);
        return report;
    }

    private void collect(RiskEventContext context) {
        for (RiskDataCollector collector : collectors) {
            RiskCollectedData data = collector.collect(context);
            if (data == null) {
                continue;
            }
            context.getTableMetadataList().addAll(data.getTableMetadataList());
            context.getSlowSqlList().addAll(data.getSlowSqlList());
            context.getRedisSignals().addAll(data.getRedisSignals());
            context.getMqSignals().addAll(data.getMqSignals());
        }
    }

    private RiskReport mergeReport(RiskEventContext context,
                                   RiskScoreEngine.ScoreResult scoreResult,
                                   RiskAiAnalyzeResponse aiResponse) {
        RiskReport report = new RiskReport();
        report.setEventName(context.getEventName());
        report.setEventDesc(context.getEventDesc());
        int aiScore = aiResponse == null || aiResponse.getRiskScore() == null ? 0 : aiResponse.getRiskScore();
        int finalScore = Math.max(scoreResult.getScore(), aiScore);
        report.setRiskScore(finalScore);
        RiskLevel ruleLevel = RiskLevel.fromScore(scoreResult.getScore());
        RiskLevel aiLevel = parseLevel(aiResponse == null ? null : aiResponse.getRiskLevel());
        report.setRiskLevel(RiskLevel.max(RiskLevel.fromScore(finalScore), RiskLevel.max(ruleLevel, aiLevel)));
        report.getRiskPoints().addAll(scoreResult.getRiskPoints());
        report.getSuggestions().addAll(scoreResult.getSuggestions());
        report.getChecklist().addAll(scoreResult.getChecklist());

        if (aiResponse != null) {
            report.getRiskPoints().addAll(aiResponse.getRiskPoints());
            report.getImpactComponents().addAll(aiResponse.getImpactComponents());
            report.getSuggestions().addAll(aiResponse.getSuggestions());
            report.getChecklist().addAll(aiResponse.getChecklist());
            report.getFallbackPlan().addAll(aiResponse.getFallbackPlan());
            report.setEngine(aiResponse.getEngine());
        } else {
            report.setEngine("RULE_FALLBACK");
        }
        enrichDefaults(report, context);
        dedupe(report);
        report.setGeneratedAt(LocalDateTime.now());
        return report;
    }

    private void enrichDefaults(RiskReport report, RiskEventContext context) {
        if (report.getImpactComponents().isEmpty()) {
            report.getImpactComponents().addAll(context.getHighRiskResources());
        }
        if (report.getChecklist().isEmpty()) {
            report.getChecklist().add(new ChecklistItem("API 检查", "确认核心接口限流、超时、降级和压测基线", "MEDIUM"));
        }
        if (report.getFallbackPlan().isEmpty()) {
            report.getFallbackPlan().add("活动期间保留人工审核入口，风险建议不得自动执行线上变更");
            report.getFallbackPlan().add("出现 RT 飙升时优先限流、降级非核心能力并检查 Redis/MQ/MySQL 水位");
        }
    }

    private RiskLevel parseLevel(String level) {
        if (level == null) {
            return RiskLevel.LOW;
        }
        try {
            return RiskLevel.valueOf(level.toUpperCase());
        } catch (Exception e) {
            return RiskLevel.LOW;
        }
    }

    private void dedupe(RiskReport report) {
        report.setSuggestions(dedupeList(report.getSuggestions()));
        report.setImpactComponents(dedupeList(report.getImpactComponents()));
        report.setFallbackPlan(dedupeList(report.getFallbackPlan()));
    }

    private List<String> dedupeList(List<String> list) {
        Set<String> set = new LinkedHashSet<>();
        if (list != null) {
            for (String item : list) {
                if (item != null && item.trim().length() > 0) {
                    set.add(item);
                }
            }
        }
        return new java.util.ArrayList<>(set);
    }

    private void cacheReport(String key, RiskReport report) {
        try {
            stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(report), 30, TimeUnit.MINUTES);
        } catch (Exception ignored) {
        }
    }

    private void persistEvent(RiskEventContext context, RiskReport report) {
        try {
            jdbcTemplate.update(
                    "INSERT INTO risk_event(event_name,event_desc,event_type,risk_level,risk_score,status,report) VALUES(?,?,?,?,?,?,?)",
                    report.getEventName(),
                    report.getEventDesc(),
                    context.getEventType(),
                    report.getRiskLevel() == null ? null : report.getRiskLevel().name(),
                    report.getRiskScore(),
                    "GENERATED",
                    JSONUtil.toJsonStr(report)
            );
        } catch (Exception ignored) {
        }
    }
}
