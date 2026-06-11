package com.hmdp.risk.collector;

import com.hmdp.risk.domain.model.RiskEventContext;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

@Component
public class RedisMetricCollector implements RiskDataCollector {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public String supportType() {
        return "REDIS";
    }

    @Override
    public RiskCollectedData collect(RiskEventContext context) {
        RiskCollectedData data = new RiskCollectedData();
        String desc = context.getEventDesc() == null ? "" : context.getEventDesc();
        if (containsAny(desc, "秒杀", "优惠券", "库存", "抢购")) {
            data.getRedisSignals().add("活动涉及库存/秒杀语义，需重点检查 seckill:stock:* 热 Key 和库存分桶");
        }
        try {
            Long dbSize = stringRedisTemplate.execute((RedisCallback<Long>) connection -> connection.dbSize());
            data.getRedisSignals().add("Redis 当前 key 数约为 " + (dbSize == null ? 0 : dbSize));
        } catch (Exception e) {
            data.getRedisSignals().add("Redis 指标暂不可用，需人工确认连接、内存和热点 key");
        }
        return data;
    }

    private boolean containsAny(String text, String... words) {
        for (String word : words) {
            if (text.contains(word)) {
                return true;
            }
        }
        return false;
    }
}
