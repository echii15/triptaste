package com.hmdp.ai.risk.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hmdp.ai.risk.dto.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class RiskAiOrchestrationService {

    private static final String ENGINE_LLM = "LLM";
    private static final String ENGINE_FALLBACK = "FALLBACK";

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper;
    private final boolean aiEnabled;
    private final String apiKey;
    private final String baseUrl;
    private final String model;
    private final Double temperature;

    public RiskAiOrchestrationService(ObjectMapper objectMapper,
                                      @Value("${spring.ai.openai.api-key:}") String openAiApiKey,
                                      @Value("${spring.ai.openai.base-url:https://api.deepseek.com/v1}") String baseUrl,
                                      @Value("${spring.ai.openai.chat.options.model:deepseek-chat}") String model,
                                      @Value("${spring.ai.openai.chat.options.temperature:0.2}") Double temperature) {
        this.objectMapper = objectMapper;
        this.apiKey = openAiApiKey;
        this.baseUrl = baseUrl;
        this.model = model;
        this.temperature = temperature;
        this.aiEnabled = StringUtils.hasText(openAiApiKey);
    }

    public RiskAnalyzeResponse analyze(RiskAnalyzeRequest request) {
        RiskAnalyzeResponse fallback = fallbackAnalyze(request);
        if (!aiEnabled) {
            return fallback;
        }

        String system = "你是高并发系统风险分析助手，只输出 JSON，不要输出 markdown。所有建议必须经过人工审核，不得要求自动执行线上变更。";
        String user = "请根据活动信息、元数据、Redis/MQ 信号、知识库和规则结果，输出 JSON 字段："
                + "riskLevel(string:LOW|MEDIUM|HIGH|CRITICAL),riskScore(int),riskPoints(array),"
                + "impactComponents(array),suggestions(array),checklist(array),fallbackPlan(array)。"
                + "riskPoints 元素字段为 type,component,reason,score；checklist 元素字段为 category,item,priority。"
                + "活动上下文：" + toJson(safeContext(request))
                + "；规则评分：" + safeRuleScore(request)
                + "；规则风险点：" + toJson(request == null ? Collections.emptyList() : request.getRuleRiskPoints());
        RiskAnalyzeResponse response = callAiForJson(system, user, RiskAnalyzeResponse.class);
        if (!isValid(response)) {
            return fallback;
        }
        normalize(response, fallback);
        response.setEngine(ENGINE_LLM);
        return response;
    }

    public RiskAnalyzeResponse checkDdl(DdlCheckRequest request) {
        RiskAnalyzeResponse fallback = fallbackDdl(request);
        if (!aiEnabled) {
            return fallback;
        }

        String system = "你是 MySQL DDL 上线风险评审助手，只输出 JSON，不要输出 markdown。禁止建议直接在线执行高危操作。";
        String user = "请分析以下 DDL 风险，输出 JSON 字段：riskLevel,riskScore,riskPoints,impactComponents,"
                + "suggestions,checklist,fallbackPlan。重点识别锁表、表重建、长事务、写入影响和灰度迁移方案。"
                + "DDL：" + (request == null ? "" : sanitize(request.getDdl()))
                + "；上下文：" + toJson(request == null ? null : request.getContext())
                + "；规则评分：" + (request == null ? 0 : request.getRuleScore());
        RiskAnalyzeResponse response = callAiForJson(system, user, RiskAnalyzeResponse.class);
        if (!isValid(response)) {
            return fallback;
        }
        normalize(response, fallback);
        response.setEngine(ENGINE_LLM);
        return response;
    }

    private RiskAnalyzeResponse fallbackAnalyze(RiskAnalyzeRequest request) {
        RiskAnalyzeResponse response = new RiskAnalyzeResponse();
        RiskEventContext context = safeContext(request);
        int score = safeRuleScore(request);
        response.setRiskScore(score);
        response.setRiskLevel(levelFromScore(score));
        response.setRiskPoints(copyRulePoints(request));
        response.getImpactComponents().addAll(safeList(context.getHighRiskResources()));
        if (response.getImpactComponents().isEmpty()) {
            response.getImpactComponents().addAll(Arrays.asList("MySQL", "Redis", "RabbitMQ", "API"));
        }
        response.getSuggestions().addAll(defaultSuggestions(context));
        response.getChecklist().addAll(defaultChecklist(context));
        response.getFallbackPlan().add("保留人工审核，AI 输出仅作为上线评审建议");
        response.getFallbackPlan().add("活动期间监控接口 RT、Redis 热点、MySQL 慢 SQL 和 RabbitMQ 队列堆积");
        response.setEngine(ENGINE_FALLBACK);
        return response;
    }

    private RiskAnalyzeResponse fallbackDdl(DdlCheckRequest request) {
        RiskAnalyzeResponse response = new RiskAnalyzeResponse();
        int score = request == null || request.getRuleScore() == null ? 6 : request.getRuleScore();
        response.setRiskScore(score);
        response.setRiskLevel(levelFromScore(score));
        RiskPoint point = new RiskPoint();
        point.setType("DDL_RISK");
        point.setComponent("MySQL");
        point.setReason("DDL 需要人工评审表数据量、锁表风险、回滚方案和执行窗口");
        point.setScore(Math.max(score, 4));
        response.getRiskPoints().add(point);
        response.getImpactComponents().add("MySQL");
        response.getSuggestions().add("大表变更优先采用 nullable 字段、应用双写、后台分批回填、校验后切换读流量");
        response.getSuggestions().add("避免高峰期执行 DDL，执行前确认备份、回滚和 kill 长事务预案");
        response.getChecklist().add(item("DDL 检查", "确认 DDL 是否触发表重建或长时间 MDL 锁", "HIGH"));
        response.getChecklist().add(item("数据库检查", "确认目标表行数、索引、写入 QPS 和慢 SQL", "HIGH"));
        response.getFallbackPlan().add("DDL 不由系统自动执行，必须由 DBA 或负责人审核后手工变更");
        response.setEngine(ENGINE_FALLBACK);
        return response;
    }

    private List<String> defaultSuggestions(RiskEventContext context) {
        List<String> suggestions = new ArrayList<>();
        suggestions.add("活动前完成核心接口压测，确认限流、超时、熔断和降级开关");
        if (containsAny(context.getEventDesc(), "秒杀", "优惠券", "库存", "抢购")) {
            suggestions.add("库存链路使用 Redis + Lua 原子扣减，数据库通过条件扣减和唯一约束兜底");
            suggestions.add("对库存 Key 进行分桶或热点保护，提前预热商品和店铺缓存");
        }
        suggestions.add("确认 RabbitMQ 消费幂等、重试次数、死信队列和补偿任务");
        suggestions.add("确认 MySQL 慢 SQL、核心索引、连接池水位和大表 DDL 风险");
        return suggestions;
    }

    private List<ChecklistItem> defaultChecklist(RiskEventContext context) {
        List<ChecklistItem> list = new ArrayList<>();
        list.add(item("API 检查", "确认核心接口限流、超时和降级策略", "HIGH"));
        list.add(item("Redis 检查", "检查热点 Key、缓存预热、TTL 和内存水位", "HIGH"));
        list.add(item("MQ 检查", "检查消费者幂等、死信队列和消息补偿", "HIGH"));
        list.add(item("数据库检查", "检查慢 SQL、索引、连接池和大表变更", "HIGH"));
        return list;
    }

    private ChecklistItem item(String category, String item, String priority) {
        ChecklistItem checklistItem = new ChecklistItem();
        checklistItem.setCategory(category);
        checklistItem.setItem(item);
        checklistItem.setPriority(priority);
        return checklistItem;
    }

    private RiskEventContext safeContext(RiskAnalyzeRequest request) {
        if (request == null || request.getContext() == null) {
            return new RiskEventContext();
        }
        return request.getContext();
    }

    private int safeRuleScore(RiskAnalyzeRequest request) {
        return request == null || request.getRuleScore() == null ? 0 : request.getRuleScore();
    }

    private List<RiskPoint> copyRulePoints(RiskAnalyzeRequest request) {
        if (request == null || request.getRuleRiskPoints() == null) {
            return new ArrayList<>();
        }
        return new ArrayList<>(request.getRuleRiskPoints());
    }

    private String levelFromScore(int score) {
        if (score >= 10) {
            return "CRITICAL";
        }
        if (score >= 7) {
            return "HIGH";
        }
        if (score >= 4) {
            return "MEDIUM";
        }
        return "LOW";
    }

    private boolean isValid(RiskAnalyzeResponse response) {
        return response != null && StringUtils.hasText(response.getRiskLevel()) && response.getRiskScore() != null;
    }

    private void normalize(RiskAnalyzeResponse response, RiskAnalyzeResponse fallback) {
        if (response.getRiskPoints() == null || response.getRiskPoints().isEmpty()) {
            response.setRiskPoints(fallback.getRiskPoints());
        }
        if (response.getImpactComponents() == null || response.getImpactComponents().isEmpty()) {
            response.setImpactComponents(fallback.getImpactComponents());
        }
        if (response.getSuggestions() == null || response.getSuggestions().isEmpty()) {
            response.setSuggestions(fallback.getSuggestions());
        }
        if (response.getChecklist() == null || response.getChecklist().isEmpty()) {
            response.setChecklist(fallback.getChecklist());
        }
        if (response.getFallbackPlan() == null || response.getFallbackPlan().isEmpty()) {
            response.setFallbackPlan(fallback.getFallbackPlan());
        }
        response.setRiskLevel(levelFromScore(Math.max(response.getRiskScore(), fallback.getRiskScore())));
        response.setRiskScore(Math.max(response.getRiskScore(), fallback.getRiskScore()));
    }

    private <T> T callAiForJson(String systemPrompt, String userPrompt, Class<T> clazz) {
        try {
            String raw = callOpenAiCompatible(systemPrompt, userPrompt);
            return parseJson(raw, clazz);
        } catch (Exception e) {
            log.warn("risk ai call failed: {}", e.getMessage());
            return null;
        }
    }

    private String callOpenAiCompatible(String systemPrompt, String userPrompt) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("temperature", temperature);
        List<Map<String, String>> messages = new ArrayList<>();
        Map<String, String> system = new LinkedHashMap<>();
        system.put("role", "system");
        system.put("content", systemPrompt);
        messages.add(system);
        Map<String, String> user = new LinkedHashMap<>();
        user.put("role", "user");
        user.put("content", userPrompt);
        messages.add(user);
        body.put("messages", messages);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);
        ResponseEntity<Map> response = restTemplate.postForEntity(
                normalizeBaseUrl(baseUrl) + "/chat/completions",
                new HttpEntity<>(body, headers),
                Map.class
        );
        Map responseBody = response.getBody();
        if (responseBody == null) {
            return null;
        }
        List choices = (List) responseBody.get("choices");
        if (choices == null || choices.isEmpty()) {
            return null;
        }
        Map first = (Map) choices.get(0);
        Map message = (Map) first.get("message");
        return message == null ? null : String.valueOf(message.get("content"));
    }

    private String normalizeBaseUrl(String url) {
        if (!StringUtils.hasText(url)) {
            return "https://api.deepseek.com/v1";
        }
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    private <T> T parseJson(String raw, Class<T> clazz) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        try {
            String cleaned = raw.trim().replace("```json", "").replace("```", "").trim();
            int start = cleaned.indexOf("{");
            int end = cleaned.lastIndexOf("}");
            if (start >= 0 && end > start) {
                cleaned = cleaned.substring(start, end + 1);
            }
            return objectMapper.readValue(cleaned, clazz);
        } catch (Exception e) {
            log.warn("parse risk ai json failed: {}", e.getMessage());
            return null;
        }
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return String.valueOf(value);
        }
    }

    private String sanitize(String text) {
        if (text == null) {
            return "";
        }
        String t = text.replaceAll("<[^>]+>", " ").replaceAll("\\s+", " ").trim();
        return t.length() > 1000 ? t.substring(0, 1000) : t;
    }

    private boolean containsAny(String text, String... words) {
        if (text == null) {
            return false;
        }
        for (String word : words) {
            if (text.contains(word)) {
                return true;
            }
        }
        return false;
    }

    private List<String> safeList(List<String> list) {
        return list == null ? Collections.emptyList() : list.stream()
                .filter(StringUtils::hasText)
                .distinct()
                .collect(Collectors.toList());
    }
}
