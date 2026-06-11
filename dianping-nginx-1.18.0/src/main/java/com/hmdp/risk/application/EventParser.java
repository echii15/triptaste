package com.hmdp.risk.application;

import com.hmdp.risk.domain.model.RiskEventContext;
import org.springframework.stereotype.Component;

@Component
public class EventParser {

    public RiskEventContext parse(String eventDesc) {
        RiskEventContext context = new RiskEventContext();
        context.setEventDesc(eventDesc);
        context.setEventName(inferEventName(eventDesc));
        context.setEventTime(inferTime(eventDesc));
        context.setEventType(inferEventType(eventDesc));
        context.setTrafficLevel(inferTrafficLevel(eventDesc));
        enrichModulesAndApis(context, eventDesc == null ? "" : eventDesc);
        return context;
    }

    private String inferEventName(String desc) {
        if (desc == null || desc.trim().isEmpty()) {
            return "未命名高并发活动";
        }
        if (desc.contains("618")) {
            return "618 活动";
        }
        if (desc.contains("秒杀")) {
            return "秒杀活动";
        }
        if (desc.contains("优惠券")) {
            return "优惠券活动";
        }
        return desc.length() > 30 ? desc.substring(0, 30) : desc;
    }

    private String inferTime(String desc) {
        if (desc == null) {
            return "UNKNOWN";
        }
        if (desc.contains("明晚")) {
            return "明晚";
        }
        if (desc.contains("今晚")) {
            return "今晚";
        }
        if (desc.contains("明天")) {
            return "明天";
        }
        return "未明确";
    }

    private String inferEventType(String desc) {
        if (desc == null) {
            return "GENERAL";
        }
        if (desc.contains("秒杀") || desc.contains("抢购")) {
            return "SECKILL";
        }
        if (desc.contains("618") || desc.contains("大促")) {
            return "PROMOTION";
        }
        if (desc.contains("DDL") || desc.toUpperCase().contains("ALTER TABLE")) {
            return "DDL";
        }
        return "GENERAL";
    }

    private String inferTrafficLevel(String desc) {
        if (desc == null) {
            return "MEDIUM";
        }
        if (desc.contains("大量") || desc.contains("高并发") || desc.contains("618") || desc.contains("秒杀")) {
            return "HIGH";
        }
        return "MEDIUM";
    }

    private void enrichModulesAndApis(RiskEventContext context, String desc) {
        if (desc.contains("商品") || desc.contains("店铺")) {
            context.getBizModules().add("商品/店铺详情");
            context.getCoreApis().add("/shop/{id}");
            context.getHighRiskResources().add("Redis");
        }
        if (desc.contains("优惠券") || desc.contains("秒杀") || desc.contains("抢购")) {
            context.getBizModules().add("优惠券秒杀");
            context.getCoreApis().add("/voucher-order/seckill/{id}");
            context.getHighRiskResources().add("Redis");
            context.getHighRiskResources().add("RabbitMQ");
        }
        if (desc.contains("订单") || desc.contains("下单")) {
            context.getBizModules().add("订单创建");
            context.getCoreApis().add("/voucher-order/seckill/{id}");
            context.getHighRiskResources().add("MySQL");
            context.getHighRiskResources().add("RabbitMQ");
        }
        if (context.getBizModules().isEmpty()) {
            context.getBizModules().add("通用业务链路");
        }
    }
}
