# checkpointv2.0-skill-agent

## 本次目标

将项目原有 AI 能力 Skill 化 / MCP 化，使 AI 能力从固定接口调用升级为：

- 可注册
- 可路由
- 可执行
- 可权限控制
- 可根据用户反馈迭代 Profile

## 完成内容

新增主模块：

```text
dianping-nginx-1.18.0/src/main/java/com/hmdp/skill
```

新增核心能力：

1. `Skill` 统一接口
2. `SkillRegistryService` 注册中心
3. `SkillExecutor` 执行器
4. `SkillRouter` 路由器
5. `SkillAgentService` Agent 编排服务
6. `UserSkillProfileService` 用户偏好更新服务
7. `SkillAgentController` 对外接口

## 已 Skill 化的原有 AI 能力

### shop_summary_skill

对应原能力：

```text
IAiService.getShopSummary
```

用途：

根据店铺 ID 生成店铺口碑总结、亮点和消费建议。

### shop_recommend_skill

对应原能力：

```text
IAiService.assistantRecommend
```

用途：

根据用户自然语言需求、位置、品类偏好推荐店铺。

### review_risk_check_skill

对应原能力：

```text
IAiService.checkReviewRisk
```

用途：

对点评、笔记、评论草稿进行风控质检。

### order_draft_skill

新增安全 Skill。

用途：

只生成订单草稿，不自动支付、不自动确认订单。

权限：

```text
HIGH
```

调用要求：

```json
{
  "confirmed": true
}
```

## 新增接口

### 查看注册表

```http
GET /api/skill/registry
```

### 直接执行 Skill

```http
POST /api/skill/execute
```

### Agent 自动路由

```http
POST /api/skill/agent/chat
```

### 提交反馈

```http
POST /api/skill/feedback
```

## 新增数据表

SQL 文件：

```text
dianping-nginx-1.18.0/src/main/resources/db/migration_20260513_skill_agent.sql
```

包含：

```sql
ai_skill_registry
user_skill_profile
```

## 安全设计

1. Skill 必须注册后才能调用。
2. Skill 支持启停状态。
3. Skill 有权限等级。
4. 高风险 Skill 需要显式确认。
5. 下单类 Skill 只能生成草稿。
6. LLM 不直接执行支付、确认订单、修改数据库等操作。
7. 交易相关数据以服务端校验为准。

## 当前 Router 规则

当前第一版 `SkillRouter` 使用本地关键词规则：

- 包含风控、质检、违规、广告、隐私等词：调用 `review_risk_check_skill`
- 包含总结、口碑、这家店怎么样、评价如何等词：调用 `shop_summary_skill`
- 包含下单、订单、锁定、购买等词：调用 `order_draft_skill`
- 其他默认走 `shop_recommend_skill`

后续可替换为 LLM Router。

## 验证结果

执行：

```bash
cd dianping-nginx-1.18.0
mvn -q -DskipTests compile
```

结果：

```text
compile passed
```

## 后续建议

下一步可以继续实现：

1. 优惠券 Skill
   - `coupon_search_skill`
   - `coupon_match_skill`

2. 达人贴 Skill
   - `post_recommend_skill`
   - `post_summary_skill`

3. 评论生成 Skill
   - `comment_draft_skill`

4. LLM Router
   - 让 AI 根据注册表 Schema 生成结构化调用计划
   - 后端继续做 Schema、权限和业务规则校验

5. Profile 深度迭代
   - 根据点击、领券、下单、忽略、编辑评论等行为更新长期偏好
