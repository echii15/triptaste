CREATE TABLE IF NOT EXISTS risk_event (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  event_name VARCHAR(128) NOT NULL,
  event_desc TEXT,
  event_type VARCHAR(64),
  risk_level VARCHAR(32),
  risk_score INT,
  status VARCHAR(32) DEFAULT 'GENERATED',
  report TEXT,
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS risk_knowledge (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  scene VARCHAR(64) NOT NULL,
  risk_type VARCHAR(64) NOT NULL,
  component VARCHAR(64) NOT NULL,
  symptom TEXT,
  check_items TEXT,
  solutions TEXT,
  enabled TINYINT DEFAULT 1,
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS metadata_table_info (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  table_name VARCHAR(128) NOT NULL,
  table_rows BIGINT DEFAULT 0,
  data_size BIGINT DEFAULT 0,
  index_info TEXT,
  slow_sql_count INT DEFAULT 0,
  write_qps DOUBLE DEFAULT 0,
  update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS risk_report_snapshot (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  event_id BIGINT,
  rule_result TEXT,
  ai_result TEXT,
  final_report TEXT,
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO risk_knowledge(scene, risk_type, component, symptom, check_items, solutions)
SELECT '秒杀活动', 'Redis 热 Key', 'Redis', '库存 Key 被大量请求集中访问', '检查库存 Key QPS、分桶、预热、TTL', '使用库存分桶、Lua 原子扣减、本地热点缓存和限流'
WHERE NOT EXISTS (SELECT 1 FROM risk_knowledge WHERE scene = '秒杀活动' AND risk_type = 'Redis 热 Key');

INSERT INTO risk_knowledge(scene, risk_type, component, symptom, check_items, solutions)
SELECT '秒杀活动', '库存超卖', 'MySQL/Redis', '库存扣减链路缺少原子性或幂等', '检查 Lua 脚本、一人一单、DB stock > 0', 'Redis 预扣库存，DB 条件扣减，订单唯一约束兜底'
WHERE NOT EXISTS (SELECT 1 FROM risk_knowledge WHERE scene = '秒杀活动' AND risk_type = '库存超卖');

INSERT INTO risk_knowledge(scene, risk_type, component, symptom, check_items, solutions)
SELECT '大促活动', 'MQ 堆积', 'RabbitMQ', '订单创建依赖异步消费，消费者不足或失败重试造成堆积', '检查消费者数量、ready 消息数、死信队列', '消费者水平扩容、幂等消费、失败补偿和死信巡检'
WHERE NOT EXISTS (SELECT 1 FROM risk_knowledge WHERE scene = '大促活动' AND risk_type = 'MQ 堆积');

INSERT INTO risk_knowledge(scene, risk_type, component, symptom, check_items, solutions)
SELECT 'DDL 变更', '锁表风险', 'MySQL', '大表 ALTER 可能触发表重建或长时间 MDL 锁', '检查表行数、DDL 类型、执行窗口', '低峰执行、online schema change、nullable 字段、分批回填'
WHERE NOT EXISTS (SELECT 1 FROM risk_knowledge WHERE scene = 'DDL 变更' AND risk_type = '锁表风险');
