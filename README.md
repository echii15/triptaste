# triptaste

本项目基于黑马点评（本地生活服务）进行二次开发，在保留原有高并发业务能力的基础上，新增了 AI 服务与 Skill Agent 插件化编排能力。

---

## 一、项目定位

### 1）原生黑马点评能力（保留）
- 短信登录 / Token 登录态刷新
- 商铺查询（缓存、缓存穿透/击穿/雪崩治理）
- 商铺类型查询
- 探店笔记（发布、点赞、关注推送）
- 附近商铺（GEO）
- 优惠券秒杀（Lua 原子校验 + MQ 异步下单 + 分布式锁）

### 2）AI 增强能力（新增）
- AI 店铺推荐（自然语言意图 + 推荐理由）
- AI 店铺口碑总结
- AI 评论风险检查
- Skill Agent：将 AI/业务能力 Skill 化、可路由、可扩展、可反馈迭代

---

## 二、技术路线

### 1）总体路线
- 主业务服务继续使用稳定栈：`Spring Boot 2.3 + MyBatis-Plus + Redis + RabbitMQ + MySQL`
- AI 能力采用 Sidecar 思路解耦：主服务调用 AI 子服务（或本地降级）
- MCP 风格 Skill 插件化：Router + Registry + Executor + Profile

### 2）架构流程
用户请求 -> Controller -> 业务服务 ->（可选）AI/Skill 编排 -> Redis/MySQL/MQ -> 返回结果

对于高风险环节（如下单）：
- LLM/Skill 仅生成草稿
- 服务端二次校验库存、价格、资格
- 用户确认后才可进入支付流程

---

## 三、技术栈

### 主服务（`dianping-nginx-1.18.0`）
- Java 8
- Spring Boot 2.3.12
- MyBatis-Plus
- MySQL
- Redis + Redisson
- RabbitMQ
- Nginx（静态资源 + 反向代理）

### AI 子服务（`hmdp-ai-service`）
- Java 17
- Spring Boot 3.x
- Spring AI / 模型接入层

---

## 四、核心功能与技术实现

## 1）登录与会话
- 手机验证码登录
- 基于 Redis 存储用户会话
- `RefreshTokenInterceptor` 无感刷新 Token TTL
- `LoginInterceptor` 控制需要登录的接口访问

## 2）商铺查询与缓存治理
- Cache Aside 策略
- 缓存空值防穿透
- 互斥锁 / 逻辑过期防击穿
- TTL 与热点数据预热防雪崩

## 3）附近商铺（GEO）
- 基于 Redis GEO 存储商铺坐标
- 支持按距离排序的附近店铺查询

## 4）探店笔记与社交互动
- 发布笔记、点赞
- 关注关系与 Feed 流推送
- 热门笔记排序

## 5）秒杀优惠券（高并发核心）
- Lua 脚本做库存与一人一单原子校验
- 请求合法后投递 RabbitMQ 异步下单
- 消费端执行业务落库与幂等控制
- Redisson 锁避免并发重复下单

## 6）AI 推荐与总结（新增）
- `POST /ai/assistant/recommend`：自然语言店铺推荐
- `GET /ai/shop/{shopId}/summary`：店铺总结
- Redis 缓存 AI 结果，降低调用成本
- 外部 AI 不可用时主服务降级返回，避免整体失败

## 7）AI 评论风控（新增）
- `POST /ai/review/risk-check`
- 输出风险等级、原因、建议
- 失败时本地 fallback，保障主链路可用

## 8）Skill Agent 插件系统（新增）
路径：`src/main/java/com/hmdp/skill`

已实现组件：
- Skill Router：自然语言 -> 调用计划
- Skill Registry：Skill 定义/启停
- Skill Executor：执行与权限校验
- User Skill Profile：基于反馈更新偏好
- Skill Agent API：统一编排入口

内置 Skill：
- `shop_recommend_skill`
- `shop_summary_skill`
- `review_risk_check_skill`
- `order_draft_skill`（仅草稿，不自动支付）

---

## 五、接口设计（重点）

### A. 原生业务接口（示例）
- `POST /user/login`
- `GET /shop/{id}`
- `GET /shop-type/list`
- `GET /voucher-order/seckill/{id}`
- `POST /blog`

### B. AI 接口
- `POST /ai/assistant/recommend`
  - 入参：`query, x, y, currentTypeId`
  - 出参：`intentSummary, recommendShops, keywords`
- `GET /ai/shop/{shopId}/summary`
- `POST /ai/review/risk-check`

### C. Skill Agent 接口
- `GET /skill/registry`
- `POST /skill/execute`
- `POST /skill/agent/chat`
- `POST /skill/feedback`

---

## 六、当前项目亮点

1. **经典高并发链路完整保留**  
   秒杀链路（Lua + MQ + 锁）与缓存治理实践完整可运行。

2. **AI 与主业务低耦合**  
   通过 Sidecar/远程调用与本地降级机制，避免 AI 故障拖垮主业务。

3. **Skill MCP 化思路落地**  
   将消费推荐能力模块化为可注册 Skill，具备扩展与治理基础。

4. **安全边界清晰**  
   高风险能力（下单/支付）不交由 LLM 直接执行，必须服务端校验与用户确认。

5. **工程化可运维**  
   缓存、日志、降级、异步化都已落位，适合继续做生产级增强。

---

## 七、运行方式

1. 启动 MySQL / Redis / RabbitMQ  
2. 启动主服务：
```bash
cd dianping-nginx-1.18.0
mvn clean -DskipTests compile
mvn spring-boot:run
```
3. 启动 Nginx（前端静态页）并访问 `http://127.0.0.1:8080`

---

## 八、近期修复记录（与你当前环境相关）

1. 修复 `IAiService` 注入失败：`AiServiceImpl` 编译类型问题导致 Bean 丢失。  
2. 修复 Skill 推荐空结果：`tb_shop` 无 `shop_desc` 字段，已将 `Shop.shopDesc` 标记为 `@TableField(exist = false)`。  
3. 增强 `assistantRecommend` / `reviewRiskCheck` 外层异常兜底，避免直接 500。  

---

## 九、后续可继续增强

- Skill Registry 持久化表与版本治理
- 多 Skill 编排策略（并行、回退、权重路由）
- 推荐效果评估指标（点击率、转化率）
- AI sidecar 的观测与限流（熔断、重试、隔离）
- 风险预警助手与活动评审能力（你规划的高并发风险项目）并入同一 Agent 平台


