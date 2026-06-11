# HeiMaDianPin AI 服务扩展分析

## 项目概览

### 核心架构设计

HeiMaDianPin AI 改造采用 **Sidecar 架构**，将大模型能力从主业务服务中完全拆离，实现三层分离：

```
┌─────────────────────────────────────────┐
│   前端静态页面（Nginx 8080）             │
│  shop-detail.html / blog-edit.html       │
└──────────────┬──────────────────────────┘
               │
┌──────────────▼────────────────────────┐
│   主业务后端（Spring Boot 8081）       │
│   Spring Boot 2.3 + Java 8             │
│   - AiServiceImpl (业务编排)             │
│   - AiRemoteClient (RPC调用)            │
│   - Redis缓存/GEO搜索                   │
└──────────────┬────────────────────────┘
               │
┌──────────────▼────────────────────────┐
│   AI 子服务（Spring Boot 8090）        │
│   Spring Boot 3.3 + Java 17            │
│   - AiOrchestrationService (模型编排)  │
│   - 大模型提示词管理                    │
│   - 本地 Fallback 兜底                 │
└──────────────┬────────────────────────┘
               │
┌──────────────▼────────────────────────┐
│   DashScope 大模型 API                 │
│   (Alibaba Qwen Turbo Flash)           │
└────────────────────────────────────────┘
```

### 为什么要用 Sidecar 模式

1. **版本隔离**：主项目(JDK 8)和 AI 服务(JDK 17)版本差异大
2. **依赖隔离**：Spring AI SDK 与主项目依赖冲突
3. **独立扩展**：大模型供应商可自由替换而无需重新编译主项目
4. **容错机制**：主项目可独立运行，AI 服务故障不影响核心业务
5. **职责清晰**：业务编排 vs 模型编排职责分离

---

## 功能架构

### 提供的 6 个 API 接口

#### 1. **摘要类接口** - 口碑汇总
```
POST /internal/ai/summarize/chunk     分组摘要(单个分组)
POST /internal/ai/summarize/final     最终摘要(多分组聚合)
```

**应用场景**：店铺详情页展示 AI 口碑总结
- 将大量探店博客分组处理
- 每组提取高频信息、小众亮点、关键词
- 最后聚合多组数据得到整体总结

#### 2. **意图解析类接口** - 用户需求理解
```
POST /internal/ai/intent/parse        用户意图解析
```

**应用场景**：推荐系统前置
- 从用户查询文本提取意图
- 识别店铺类型偏好
- 提取排除关键词(用户表达"不要/不想/忌口")
- 支持模糊查询理解

#### 3. **推荐类接口** - 店铺排序和推荐
```
POST /internal/ai/recommend/reason    推荐理由生成
POST /internal/ai/recommend/rerank    候选集重排序
```

**应用场景**：店铺列表页 AI 助手推荐
- 根据用户需求对候选店铺重排
- 为每个店铺生成个性化推荐理由
- 支持硬约束(忌口项必须满足)

#### 4. **风控类接口** - 内容安全检测
```
POST /internal/ai/review/risk-check   笔记风控检查
```

**应用场景**：博客发布前的内容安全审核
- 检测违法违禁内容
- 检测广告引流信息
- 检测隐私泄露(电话/身份证)
- 检测人身攻击

---

## 详细功能分析

### 功能 1：店铺 AI 口碑总结

#### 数据流程
```
用户打开店铺详情页
  ↓
前端调用 GET /api/shop/detail/{id}?ai=true
  ↓
AiServiceImpl.getShopSummary()
  ↓
┌─ 读 Redis 缓存(ai:shop:summary:{shopId})
│  ├─ 有且 fingerprint 匹配 → 返回缓存
│  └─ 无或过期 → 继续
│
├─ 查 MySQL 获取该店铺的高赞博客
│  └─ ORDER BY liked DESC LIMIT 30 (可配置)
│
├─ 对博客内容进行分组(默认3篇一组)
│  └─ 对每组调用 summarizeChunk()
│
├─ 对所有分组调用 summarizeFinal() 进行聚合
│  └─ 生成: summary + advice + highFrequencyPoints + uniquePoints
│
└─ 缓存结果到 Redis (TTL: 24h)
   └─ 返回给前端
```

#### 关键算法

**分组策略**：
- 按博客赞数降序排列
- 将博客分成若干组(每组 3 篇)
- 分组是为了降低 Token 成本和提升响应速度

**缓存策略**：
- 生成"指纹"(基于博客列表的 MD5 hash)
- 对比缓存的指纹，确定数据是否过期
- 避免频繁调用 AI，降低成本

**分布式锁**：
```java
// 在 Redis 里设置锁，30 秒超时
String lockKey = "lock:ai:shop:summary:" + shopId;
if (!tryLock(lockKey, 30L)) {
    // 其他线程正在生成总结
    // 返回旧缓存或提示"正在生成"
}
```

#### DTO 定义
```java
// 输入
class ChunkSummaryRequest {
    String shopName;           // 店铺名
    Integer chunkIndex;        // 第几分组 (1-based)
    Integer totalChunks;       // 总分组数
    List<String> reviews;      // 这一组的博客内容
}

// 输出
class ChunkSummaryResponse {
    String summary;                  // 分组总结
    List<String> highFrequencyPoints;  // 高频信息 (≤4个)
    List<String> uniquePoints;         // 小众亮点 (≤3个)
    List<String> keywords;             // 关键词 (≤8个)
    String engine;                     // 使用引擎("LLM" 或 "FALLBACK")
}
```

#### 本地 Fallback 规则
```java
// 当 AI 调用失败或返回格式非法时
private ChunkSummaryResponse fallbackChunkSummary(ChunkSummaryRequest request) {
    // 返回空结果或预定义兜底
    return new ChunkSummaryResponse(
        "信息不足，建议查看原始评论",
        Collections.emptyList(),
        Collections.emptyList(),
        Collections.emptyList(),
        ENGINE_FALLBACK
    );
}
```

### 功能 2：用户意图解析

#### 应用场景
用户输入查询文本，系统需要理解用户的真实需求：
```
用户输入: "附近适合约会的地方，但我不吃辣"

系统理解:
  intentSummary: "用户希望找附近适合约会且不辣的店铺"
  typeKeywords: ["西餐", "酒吧", "咖啡厅"]       // 店铺类型
  includeKeywords: ["约会", "环境优"]              // 必须有
  excludeKeywords: ["辣", "重口味"]                // 必须没有
```

#### 处理流程
```
1. 大模型提取意图
2. 本地规则增强(token lexicon):
   - 从文本中提取"火锅、烧烤、奶茶"等领域词汇
   - 推断"不吃肉" → includeKeywords 加 "素食"
   - 推断"没胃口" → includeKeywords 加 "清淡"

3. 合并大模型结果和本地推断(去重)
4. 返回增强后的意图
```

#### 核心逻辑
```java
// 大模型输出的基础意图
IntentParseResponse response = callAiForJson(...);

// 本地规则增强
response.setTypeKeywords(
    mergeDistinct(
        response.getTypeKeywords(),
        inferTypeKeywords(request.getQuery())  // 本地推断
    )
);

response.setIncludeKeywords(
    mergeDistinct(
        response.getIncludeKeywords(),
        extractTokens(request.getQuery())  // token 提取
    )
);
```

### 功能 3：推荐排序和理由生成

#### 两个独立步骤

**步骤 3a：生成推荐理由**
```
输入: 用户需求 + 候选店铺列表(含店铺简介/服务)
处理: 大模型为每家店生成一句推荐理由
输出: { shopId1: "理由A", shopId2: "理由B", ... }

示例:
  { 
    "123": "这家火锅晚间营业时间长，聚餐首选",
    "456": "花园氛围西餐，适合约会拍照"
  }
```

**步骤 3b：候选集重排序(Rerank)**
```
输入: 
  - 用户意图(需求、排除项)
  - 候选店铺列表(≤20家)
  
处理: 大模型根据用户约束重新排序
  1. 必须满足排除关键词的约束
  2. 优先满足包含关键词
  3. 返回 Top-N 店铺(默认 5 家)
  
输出:
  {
    "rankedShopIds": [123, 456, 789],
    "reasonByShopId": { "123": "...", ... },
    "scoreByShopId": { "123": 95, ... }      // 0-100 分
  }
```

#### 关键特性：硬约束保证
```java
String user = "请根据用户需求对候选店铺做最终排序与筛选..."
    + "规则：必须优先满足用户硬约束与排除项，"
    + "忌口/不要项不可忽略。"
    + "排除关键词：" + request.getExcludeKeywords();
```
- AI 必须遵守用户的排除项
- 例如：用户说"不吃辣"，就不能推荐麻辣烫

### 功能 4：风控检测

#### 检测内容
```java
// 1. 违法违禁内容
Pattern ILLEGAL_PATTERN = Pattern.compile(
    "(赌博|赌钱|毒品|吸毒|枪支|办证|套现|洗钱|发票|违禁|色情)"
);

// 2. 广告引流
Pattern AD_LINK_PATTERN = Pattern.compile(
    "(https?://|www\\.|加微|微信|vx|v信|QQ|扣扣|私聊|引流|代理|返利|刷单|推广|代购|点击链接|二维码)"
);

// 3. 隐私泄露
Pattern PHONE_PATTERN = Pattern.compile("(?<!\\d)1\\d{10}(?!\\d)");
Pattern ID_CARD_PATTERN = Pattern.compile("(?<!\\d)\\d{17}[0-9Xx](?!\\d)");

// 4. 人身攻击
Pattern ABUSE_PATTERN = Pattern.compile(
    "(傻逼|脑残|滚开|去死|废物|垃圾人|骗子)"
);

// 5. 虚假宣传
Pattern EXTREME_PATTERN = Pattern.compile(
    "(最便宜|稳赚不赔|包过|百分百|绝对有效|一夜暴富)"
);
```

#### 检测流程
```
1. 本地规则快速检测 (Regex)
   └─ 快速拦截明显违规内容

2. 调用 AI 进行深度风控
   ├─ 识别隐晦的风控风险
   └─ 基于上下文进行判断

3. 融合两种结果
   ├─ 如果本地规则标记为"严格拦截"类型
   │  └─ 直接返回 blocked=true
   └─ 否则返回 AI 的判断结果

4. 返回结果
   {
     "blocked": false,
     "riskLevel": "low",
     "riskTags": ["..."],
     "engine": "LLM"
   }
```

#### 分级策略
```
严格拦截标签 (STRICT_BLOCK_TAGS):
  ├─ "违法违禁"
  ├─ "广告引流"
  ├─ "联系方式"
  ├─ "隐私泄露"
  └─ "人身攻击"
  
遇到任何一个 → 直接拒绝发布
```

---

## 核心技术实现

### 主项目 (AiServiceImpl) 的职责

#### 1. 数据查询和预处理
```java
// 查询高赞博客
List<Blog> blogs = blogService.query()
    .eq("shop_id", shopId)
    .orderByDesc("liked")
    .orderByDesc("create_time")
    .last("limit " + aiProperties.getSummaryMaxBlogs())
    .list();

// 清理噪声
String cleanedContent = removeNoise(blog.getContent());
```

#### 2. Redis 缓存管理
```java
String cacheKey = RedisConstants.AI_SHOP_SUMMARY_KEY + shopId;

// 读缓存
AiShopSummaryDTO cached = readSummary(cacheKey);
if (cached != null && fingerprint.equals(cached.getFingerprint())) {
    return cached;  // 命中
}

// 写缓存
stringRedisTemplate.opsForValue().set(
    cacheKey,
    jsonStr,
    Duration.ofHours(24)
);
```

#### 3. 分布式锁
```java
// 防止多个线程同时调用 AI 生成同一个店铺的总结
String lockKey = RedisConstants.LOCK_AI_SHOP_SUMMARY_KEY + shopId;

if (!tryLock(lockKey, 30L)) {
    // 等待中...
    AiShopSummaryDTO cached = readSummary(cacheKey);
    if (cached != null) return cached;
    return Result.fail("AI总结正在生成，请稍后重试");
}

try {
    // 生成总结
    AiShopSummaryDTO summary = buildSummary(...);
} finally {
    unlock(lockKey);
}
```

#### 4. GEO 地理搜索
```java
// 获取附近店铺
GeoResults<GeoResult<String>> results = stringRedisTemplate.opsForGeo().radius(
    RedisConstants.SHOP_GEO_KEY,
    GeoReference.fromCoordinate(longitude, latitude),
    new Distance(5000, Metrics.KILOMETERS)  // 5km 范围
);
```

#### 5. 业务规则应用
```java
// 在 AI 结果基础上再过滤一遍
List<Long> rerankIds = response.getRankedShopIds();

// 检查是否满足用户的硬约束
for (Long shopId : rerankIds) {
    if (!isValidForUserConstraints(shopId, excludeKeywords)) {
        rerankIds.remove(shopId);  // 移除不符合约束的
    }
}
```

### AI 服务 (AiOrchestrationService) 的职责

#### 1. 提示词工程
```java
String system = "你是店铺口碑分析助手。请严格输出JSON，不要输出markdown。";

String user = "请根据以下探店博客，返回JSON，字段必须为："
    + "summary(string),"
    + "highFrequencyPoints(string数组,<=4),"
    + "uniquePoints(string数组,<=3),"
    + "keywords(string数组,<=8)。"
    + "要求：禁止返回博客标题、编号、店铺编号、模板句；"
    + "只输出可执行的经营/服务/体验信息短句。"
    + "店铺名称：" + request.getShopName() 
    + "；分组：" + request.getChunkIndex() + "/" + request.getTotalChunks()
    + "；探店内容：" + toCompactReviews(request.getReviews());

ChatClient client = chatClientBuilder.build();
ChunkSummaryResponse response = client
    .prompt(new Prompt(new SystemMessage(system), new UserMessage(user)))
    .call()
    .getResult()
    .getOutput()
    .getContent();  // JSON 字符串
```

#### 2. 集成 DashScope 大模型
```xml
<!-- pom.xml -->
<dependency>
    <groupId>com.alibaba.cloud.ai</groupId>
    <artifactId>spring-ai-alibaba-starter-dashscope</artifactId>
    <version>1.0.0.4</version>
</dependency>
```

```yaml
# application.yaml
spring:
  ai:
    dashscope:
      api-key: ${DASHSCOPE_API_KEY}
      chat:
        options:
          model: qwen-turbo-flash
          temperature: 0.2  # 低温度 = 更确定的回答
```

#### 3. JSON 解析和验证
```java
private <T> T callAiForJson(String system, String user, Class<T> clazz) {
    // 调用 AI 获取 JSON 字符串
    String jsonStr = callAi(system, user);
    
    // 解析
    try {
        return objectMapper.readValue(jsonStr, clazz);
    } catch (JsonProcessingException e) {
        log.warn("Failed to parse response as JSON", e);
        return null;  // 返回 null，触发 fallback
    }
}
```

#### 4. 本地 Fallback 兜底
```java
// 当 AI 超时、返回格式错误、或不可用时
private ChunkSummaryResponse fallbackChunkSummary(ChunkSummaryRequest request) {
    ChunkSummaryResponse response = new ChunkSummaryResponse();
    
    // 提取前 3 个高频词作为 keywords
    List<String> keywords = extractTopKeywords(request.getReviews(), 3);
    
    response.setSummary("信息不足，建议查看原始评论");
    response.setHighFrequencyPoints(Collections.emptyList());
    response.setUniquePoints(Collections.emptyList());
    response.setKeywords(keywords);
    response.setEngine(ENGINE_FALLBACK);
    
    return response;
}
```

---

## 数据库扩展

### 新增字段
```sql
-- 在 tb_shop 表增加店铺简介字段
ALTER TABLE `tb_shop`
  ADD COLUMN `shop_desc` varchar(1024) 
  COMMENT '商铺简介，描述经营商品、服务与提示信息';

-- 示例数据
UPDATE `tb_shop` SET `shop_desc` = '主打平价茶餐厅和工作餐，适合朋友小聚与日常吃饭' 
WHERE `id` = 1;

UPDATE `tb_shop` SET `shop_desc` = '主营铜锅涮羊肉与烤肉，晚间营业时间长，适合聚餐' 
WHERE `id` = 2;
```

### 数据模型增强
- 博客表: 支持 AI 标签、风险等级等字段
- 推荐表: 记录 AI 推荐理由和排序分数
- 审核表: 记录风控决策和人工复审

---

## 配置管理

### 环境变量
```bash
# AI 服务
set DASHSCOPE_API_KEY=sk-xxxxx                  # 大模型 API Key
set DASHSCOPE_MODEL=qwen-turbo-flash            # 模型名称
set DASHSCOPE_TEMPERATURE=0.2                   # 温度参数

# 服务端口
set HMDP_AI_PORT=8090                           # AI 服务端口
set HMDP_AI_LOG_LEVEL=info                      # 日志级别

# 主项目
set HMDP_BASE_URL=http://localhost:8090         # AI 服务地址
```

### 配置文件结构
```yaml
# hmdp-ai-service/application.yaml
server:
  port: ${HMDP_AI_PORT:8090}

spring:
  application:
    name: hmdp-ai-service
  ai:
    dashscope:
      api-key: ${DASHSCOPE_API_KEY}
      chat:
        options:
          model: ${DASHSCOPE_MODEL:qwen-turbo-flash}
          temperature: ${DASHSCOPE_TEMPERATURE:0.2}

logging:
  level:
    com.hmdp.ai: ${HMDP_AI_LOG_LEVEL:info}
```

---

## 性能优化

### 1. Token 成本优化
```java
// 分组策略：每组 3 篇博客而不是全部
// 原因：
//   - 100 篇博客一次性处理 → 超级贵
//   - 分成 30+ 组，每组 3 篇 → Token 可控

private void chunkReviews(List<Blog> blogs, int chunkSize) {
    for (int i = 0; i < blogs.size(); i += chunkSize) {
        List<Blog> chunk = blogs.subList(i, Math.min(i + chunkSize, blogs.size()));
        ChunkSummaryResponse chunkResp = summarizeChunk(chunk);
        // ...
    }
}
```

### 2. 缓存策略
```
缓存命中率的关键：
  - 指纹策略：基于博客列表的 MD5 hash
  - TTL：24 小时
  - 如果新增了博客 → 指纹变化 → 自动更新缓存
```

### 3. 并发控制
```java
// 使用分布式锁防止缓存击穿
lock:ai:shop:summary:{shopId}  → 防止同时生成多个相同的总结

// 异步处理
WARMUP_EXECUTOR.submit(() -> {
    // 预热缓存、后台生成等
});
```

### 4. 模型参数优化
```yaml
# 降低 Token 消耗和成本
temperature: 0.2  # 低温度 = 更确定的回答，更快

# 模型选择
model: qwen-turbo-flash  # 轻量级模型，速度快、成本低
```

---

## 容错机制

### 三层容错

**第 1 层：本地规则检测（AI 服务内）**
```java
// 快速检测违规内容，不依赖 AI
if (PHONE_PATTERN.matcher(content).find()) {
    return new ReviewRiskCheckResponse(
        true,  // blocked
        "high",  // riskLevel
        ["隐私泄露"],  // tags
        ENGINE_FALLBACK
    );
}
```

**第 2 层：Fallback 兜底（AI 服务内）**
```java
try {
    ChunkSummaryResponse response = callAi(...);
    if (!isValidResponse(response)) {
        return fallbackChunkSummary(...);  // 返回兜底结果
    }
    return response;
} catch (Exception e) {
    log.error("AI call failed", e);
    return fallbackChunkSummary(...);  // 返回兜底结果
}
```

**第 3 层：主项目容错（AiServiceImpl）**
```java
// AI 服务完全不可用
if (aiRemoteClient.call(...) == null) {
    // 使用本地规则或旧缓存
    return fallbackFromLocalRules(...);
}
```

### 超时控制
```java
// AI 服务的 RestTemplate 配置
SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
factory.setConnectTimeout(5000);   // 连接超时 5 秒
factory.setReadTimeout(30000);     // 读取超时 30 秒
```

---

## 集成点总结

### 前端页面集成
| 页面 | 功能 | 调用端点 |
|------|------|--------|
| shop-detail.html | AI 口碑总结 | GET /api/shop/detail?ai=true |
| shop-list.html | AI 助手推荐 | POST /api/ai/recommend |
| blog-edit.html | 发布前风控 | POST /api/ai/risk-check |

### 后端服务集成
| 组件 | 职责 |
|------|------|
| AiServiceImpl | 业务编排、缓存、GEO搜索、规则应用 |
| AiRemoteClient | 远程调用 AI 服务 |
| AiOrchestrationService | 大模型调度、提示词管理、本地 Fallback |

### 存储集成
| 存储 | 用途 |
|------|------|
| MySQL | 存储博客、店铺、推荐结果 |
| Redis | 缓存总结、意图、推荐结果；GEO 搜索 |
| DashScope API | 大模型推理 |

---

## 开发建议

### 1. 本地测试
```bash
# 启动 AI 服务
cd hmdp-ai-service
set DASHSCOPE_API_KEY=你的Key
mvn spring-boot:run

# 启动主项目
cd dianping-nginx-1.18.0
mvn spring-boot:run

# 测试接口
curl -X POST http://localhost:8090/internal/ai/summarize/chunk \
  -H "Content-Type: application/json" \
  -d '{"shopName":"火锅店","chunkIndex":1,"totalChunks":2,"reviews":["..."]}'
```

### 2. 成本控制
- 使用轻量模型(qwen-turbo-flash) 而非重模型
- 分组处理降低单次 Token 成本
- 设置合理的 TTL，提高缓存命中率
- 本地规则快速拦截，避免不必要的 AI 调用

### 3. 质量保证
- 提示词需要反复优化测试
- 设置严格的 Fallback 规则
- 对风控结果进行人工复审
- 监控 AI 调用的成功率和耗时

### 4. 可观测性
- 记录每次 AI 调用的 Token 消耗
- 记录缓存命中率
- 记录 Fallback 触发次数
- 监控 AI 服务的响应时间

---

## 总结

HMDP AI 服务扩展的核心设计思想：

1. **架构分层**：业务编排 vs 模型编排，各司其职
2. **成本控制**：分组、缓存、本地规则优先
3. **容错机制**：三层容错保证可用性
4. **灵活扩展**：大模型供应商可自由替换
5. **业务赋能**：AI 与规则协同，而非单纯替代

这套方案既保留了 AI 的能力优势，又规避了 AI 的主要风险，是一个相对平衡和务实的选择。
