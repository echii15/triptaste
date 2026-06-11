package com.hmdp.risk.collector;

import com.hmdp.risk.domain.model.RiskEventContext;
import org.springframework.amqp.core.QueueInformation;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

@Component
public class RabbitMqMetricCollector implements RiskDataCollector {

    @Resource
    private RabbitAdmin rabbitAdmin;

    @Override
    public String supportType() {
        return "RABBITMQ";
    }

    @Override
    public RiskCollectedData collect(RiskEventContext context) {
        RiskCollectedData data = new RiskCollectedData();
        collectQueue(data, "QA");
        collectQueue(data, "QD");
        return data;
    }

    private void collectQueue(RiskCollectedData data, String queueName) {
        try {
            QueueInformation info = rabbitAdmin.getQueueInfo(queueName);
            if (info == null) {
                data.getMqSignals().add(queueName + " 队列暂未声明或不可见");
                return;
            }
            data.getMqSignals().add(queueName + " ready=" + info.getMessageCount() + ", consumers=" + info.getConsumerCount());
        } catch (Exception e) {
            data.getMqSignals().add(queueName + " 队列指标暂不可用，需人工确认堆积和消费者状态");
        }
    }
}
