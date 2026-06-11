package com.hmdp.risk.knowledge;

import com.hmdp.risk.domain.model.RiskEventContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class RiskKnowledgeService {

    @Resource
    private JdbcTemplate jdbcTemplate;

    private final List<RiskKnowledge> builtInKnowledge = Arrays.asList(
            knowledge("秒杀活动", "Redis 热 Key", "Redis", "库存 Key 被大量请求集中访问", "检查库存 Key QPS、分桶、预热、TTL", "使用库存分桶、Lua 原子扣减、本地热点缓存和限流"),
            knowledge("秒杀活动", "库存超卖", "MySQL/Redis", "库存扣减链路缺少原子性或幂等", "检查 Lua 脚本、一人一单、DB stock > 0", "Redis 预扣库存，DB 条件扣减，订单唯一约束兜底"),
            knowledge("大促活动", "MQ 堆积", "RabbitMQ", "订单创建依赖异步消费，消费者不足或失败重试造成堆积", "检查消费者数量、ready 消息数、死信队列", "消费者水平扩容、幂等消费、失败补偿和死信巡检"),
            knowledge("大促活动", "缓存击穿", "Redis/MySQL", "热点商品缓存同时失效后回源数据库", "检查热点缓存 TTL、预热、逻辑过期", "热点数据预热、互斥锁重建、逻辑过期和限流降级"),
            knowledge("DDL 变更", "锁表风险", "MySQL", "大表 ALTER 可能触发表重建或长时间 MDL 锁", "检查表行数、DDL 类型、执行窗口", "低峰执行、online schema change、nullable 字段、分批回填")
    );

    public List<String> search(RiskEventContext context) {
        List<String> snippets = new ArrayList<>();
        List<RiskKnowledge> knowledgeList = loadKnowledge();
        String desc = context.getEventDesc() == null ? "" : context.getEventDesc();
        for (RiskKnowledge knowledge : knowledgeList) {
            if (desc.contains(knowledge.getScene().replace("活动", ""))
                    || desc.contains(knowledge.getRiskType())
                    || desc.contains(knowledge.getComponent())
                    || matchHighRiskResource(context, knowledge)) {
                snippets.add(toSnippet(knowledge));
            }
        }
        if (snippets.isEmpty()) {
            for (RiskKnowledge knowledge : knowledgeList) {
                snippets.add(toSnippet(knowledge));
                if (snippets.size() >= 3) {
                    break;
                }
            }
        }
        return snippets;
    }

    private List<RiskKnowledge> loadKnowledge() {
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    "SELECT scene,risk_type,component,symptom,check_items,solutions FROM risk_knowledge WHERE enabled = 1"
            );
            if (rows.isEmpty()) {
                return builtInKnowledge;
            }
            List<RiskKnowledge> list = new ArrayList<>();
            for (Map<String, Object> row : rows) {
                RiskKnowledge knowledge = new RiskKnowledge();
                knowledge.setScene(str(row.get("scene")));
                knowledge.setRiskType(str(row.get("risk_type")));
                knowledge.setComponent(str(row.get("component")));
                knowledge.setSymptom(str(row.get("symptom")));
                knowledge.setCheckItems(str(row.get("check_items")));
                knowledge.setSolutions(str(row.get("solutions")));
                list.add(knowledge);
            }
            return list;
        } catch (Exception e) {
            log.debug("load risk knowledge from db failed, use built-in knowledge, err={}", e.getMessage());
            return builtInKnowledge;
        }
    }

    private boolean matchHighRiskResource(RiskEventContext context, RiskKnowledge knowledge) {
        if (context.getHighRiskResources() == null) {
            return false;
        }
        for (String resource : context.getHighRiskResources()) {
            if (knowledge.getComponent().contains(resource) || knowledge.getRiskType().contains(resource)) {
                return true;
            }
        }
        return false;
    }

    private String toSnippet(RiskKnowledge knowledge) {
        return knowledge.getScene() + " -> " + knowledge.getRiskType()
                + " -> " + knowledge.getComponent()
                + "；症状：" + knowledge.getSymptom()
                + "；检查：" + knowledge.getCheckItems()
                + "；方案：" + knowledge.getSolutions();
    }

    private static RiskKnowledge knowledge(String scene, String riskType, String component,
                                           String symptom, String checkItems, String solutions) {
        RiskKnowledge knowledge = new RiskKnowledge();
        knowledge.setScene(scene);
        knowledge.setRiskType(riskType);
        knowledge.setComponent(component);
        knowledge.setSymptom(symptom);
        knowledge.setCheckItems(checkItems);
        knowledge.setSolutions(solutions);
        return knowledge;
    }

    private String str(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
