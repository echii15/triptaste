package com.hmdp.risk.domain.rule;

import com.hmdp.risk.domain.model.ChecklistItem;
import com.hmdp.risk.domain.model.RiskEventContext;
import com.hmdp.risk.domain.model.RiskPoint;
import org.springframework.stereotype.Component;

@Component
public class MqBacklogRiskRule implements RiskRule {

    @Override
    public boolean supports(RiskEventContext context) {
        return context.getMqSignals() != null && !context.getMqSignals().isEmpty();
    }

    @Override
    public RiskRuleResult evaluate(RiskEventContext context) {
        RiskRuleResult result = RiskRuleResult.empty();
        for (String signal : context.getMqSignals()) {
            if (signal.contains("ready=")) {
                Integer ready = parseReady(signal);
                if (ready != null && ready > 1000) {
                    result.setScore(result.getScore() + 2);
                    result.getRiskPoints().add(new RiskPoint("MQ_BACKLOG", "RabbitMQ", signal + "，存在消息堆积风险", 2));
                    result.getSuggestions().add("活动前确认消费者实例数、消费幂等、重试次数和死信队列处理预案");
                    result.getChecklist().add(new ChecklistItem("MQ 检查", "检查 QA/QD 队列堆积、消费者数量、死信队列和补偿任务", "HIGH"));
                }
            } else if (signal.contains("不可用")) {
                result.setScore(result.getScore() + 1);
                result.getChecklist().add(new ChecklistItem("MQ 检查", "RabbitMQ 指标不可用时需人工确认队列堆积和消费者状态", "MEDIUM"));
            }
        }
        return result;
    }

    private Integer parseReady(String signal) {
        try {
            int start = signal.indexOf("ready=");
            if (start < 0) {
                return null;
            }
            int end = signal.indexOf(",", start);
            String value = end > start ? signal.substring(start + 6, end) : signal.substring(start + 6);
            return Integer.valueOf(value.trim());
        } catch (Exception e) {
            return null;
        }
    }
}
