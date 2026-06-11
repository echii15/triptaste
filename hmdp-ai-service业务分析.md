# hmdp-ai-service 业务细节和实现分析

## 一、项目概述

**项目名称**: hmdp-ai-service  
**版本**: 0.0.1-SNAPSHOT  
**描述**: AI sidecar service for hm-dianping（黑马点评的AI侧车服务）  
**主要技术栈**:
- Java 17 (LTS)
- Spring Boot 3.3.5
- Spring AI 1.0.0
- Alibaba Cloud AI (DashScope) 1.0.0.4

---

## 二、核心业务功能

该服务是一个**AI编排服务**，为黑马点评平台提供6大类AI能力：

### 1. **口碑总结 (Summarization)**

#### 1.1 分块总结 - `summarizeChunk()`
- **功能**: 对多条探店评论进行分组总结
- **输入**: 
  - `shopId`, `shopName`: 店铺识别
  - `chunkIndex`, `totalChunks`: 分块位置信息
  - `reviews`: 评论片段列表 (ReviewSnippet)
  
- **处理流程**:
  ```
  输入评论 → AI模型(LLM)或 → 特征提取(FALLBACK)
  ↓
  返回JSON:
  - summary: 总结文本
  - highFrequencyPoints: 高频评点 (≤4项)
  - uniquePoints: 独特评点 (≤3项)
  - keywords: 关键词 (≤8项)
  ```

- **Fallback策略** (无API密钥时):
  - 按标点符号/换行符分割
  - 统计词频，提取高频句子（4条）
  - 提取出现1次的独特评点（3条）
  - 清理AI虚构信息（移除"AI探店-编号"模板）

#### 1.2 最终总结 - `summarizeFinal()`
- **功能**: 将多个分块总结聚合为店铺最终口碑
- **输入**:
  - `shopName`: 店铺名
  - `reviewCount`: 探店总数
  - `chunkSummaries`: 分块总结列表
  
- **输出**:
  ```json
  {
    "summary": "店铺整体口碑描述",
    "advice": "消费建议",
    "highFrequencyPoints": ["高频特征1", "高频特征2"],
    "uniquePoints": ["独特评点1", "独特评点2"]
  }
  ```

---

### 2. **用户意图解析 (Intent Parsing)**

#### 功能: `parseIntent()`
- **场景**: 用户输入搜索条件时，解析其真实需求
- **输入**:
  ```java
  {
    "query": "我想吃点清淡的，不要辣的烧烤",
    "availableTypes": ["火锅", "烧烤", "粥店", "...]
  }
  ```

- **处理流程**:
  1. **LLM模式**: 调用大模型提取意图
  2. **Fallback模式**: 
     - 按店铺类型关键词匹配类型
     - 从TOKEN词库提取美食偏好 (火锅、烧烤、奶茶等)
     - 识别排除项 (不要、忌口、避免等)

- **输出**:
  ```json
  {
    "intentSummary": "用户希望获取附近推荐",
    "typeKeywords": ["粥店"],           // 点评类型
    "includeKeywords": ["清淡"],        // 偏好特征
    "excludeKeywords": ["辣", "烧烤"]   // 排除项
  }
  ```

- **关键逻辑**:
  ```
  用户输入 → 意图识别
  ↓
  - 包含关键词(include): 正向匹配
  - 排除关键词(exclude): 黑名单过滤
  - 类型关键词: 店铺分类
  ```

---

### 3. **推荐理由生成 (Reason Generation)**

#### 功能: `recommendReason()`
- **场景**: 为推荐的候选店铺生成推荐理由
- **输入**:
  ```java
  {
    "query": "清淡的粥店",
    "shops": [
      {
        "id": 123,
        "name": "健康粥铺",
        "distance": 500,      // 距离(米)
        "score": 92,          // 评分(满分100)
        "shopDesc": "专营清粥..."
      }
    ]
  }
  ```

- **输出**:
  ```json
  {
    "reasonByShopId": {
      "123": "距离500m，评分9.2分，简介提到：专营清粥..., 与\"清淡的粥店\"匹配度较高。"
    }
  }
  ```

---

### 4. **推荐重排 (Recommendation Reranking)**

#### 功能: `recommendRerank()`
- **核心**：根据用户需求对候选店铺重新排序和筛选
- **输入**:
  ```java
  {
    "query": "便宜的清淡烤肉",
    "topN": 5,
    "includeKeywords": ["清淡"],
    "excludeKeywords": ["辣"],
    "shops": [
      {
        "id": 1,
        "name": "烤肉店A",
        "distance": 1200,
        "avgPrice": 85,
        "baseRankScore": 60,
        "typeName": "烤肉",
        "shopDesc": "..."
      }
    ]
  }
  ```

- **评分算法** (Fallback模式):
  ```
  baseScore = baseRankScore + 50
  
  // 关键词匹配加分
  includeHit * 2.4分
  excludeHit * -5.2分
  
  // 价格策略
  IF 用户说"便宜/平价":
    IF 人均≤80元: +1.8分
    IF 人均≥140元: -1.4分
  
  // 美食类型策略
  IF 用户说"清淡/吃素":
    IF 店铺含"火锅/烤肉/肉": -8分
    IF 店铺含"粥/清淡/蔬菜": +2分
  
  // 距离评分
  score += max(0, (5000 - distance) / 1200)
  
  最终分数 = clamp(50 + score * 6, 0, 100)
  ```

- **输出**:
  ```json
  {
    "rankedShopIds": [1, 3, 2],
    "reasonByShopId": {
      "1": "距离1.2km，人均约85元，匹配点2项，综合匹配分74。..."
    },
    "scoreByShopId": {
      "1": 74
    },
    "engine": "LLM|FALLBACK"
  }
  ```

---

### 5. **内容风控检查 (Review Risk Check)**

#### 功能: `reviewRiskCheck()`
- **场景**: 检测用户点评中的违规内容
- **输入**:
  ```java
  {
    "scene": "review",
    "title": "不错的烤肉",
    "content": "今天和朋友去吃烤肉，很满意...",
    "shopName": "烤肉店A",
    "shopDesc": "专业烤肉"
  }
  ```

- **风险检测类别** (Regex + 规则):

| 风险类别 | 检测模式 | 风险等级 | 基础分数 |
|---------|---------|---------|---------|
| **违法违禁** | 赌博、毒品、枪支、色情等 | BLOCK | +80 |
| **广告引流** | 微信、QQ、二维码、链接、推广 | BLOCK | +65 |
| **联系方式** | 电话号码+联系词 | BLOCK | +55 |
| **隐私泄露** | 身份证、住址、真实姓名 | BLOCK | +60 |
| **人身攻击** | 傻逼、脑残、滚开等辱骂词 | BLOCK | +45 |
| **夸大营销** | 最便宜、稳赚不赔、百分百、绝对有效 | REVIEW | +20 |
| **刷屏噪声** | 连续感叹号/问号/符号 | REVIEW | +10 |

- **风险级别**:
  ```
  - SAFE (0-30分): 安全通过
  - REVIEW (31-70分): 需要人工审核
  - BLOCK (71-100分): 直接拦截
  ```

- **输出**:
  ```json
  {
    "pass": false,
    "riskLevel": "BLOCK",
    "riskScore": 85,
    "riskTags": ["违法违禁", "广告引流"],
    "reasons": [
      "文本包含疑似违法违禁关键词。",
      "文本疑似包含广告推广或引流词。"
    ],
    "suggestion": "请删除违规内容后重新提交",
    "engine": "LLM|FALLBACK"
  }
  ```

---

## 三、技术架构

### 3.1 整体架构流程

```
HTTP Request
    ↓
[InternalAiController] - REST入口
    ↓
[AiOrchestrationService] - 业务编排核心
    ├─ LLM Engine (Spring AI + DashScope)
    │  └─ 调用通义千问(Qwen Turbo Flash)
    └─ Fallback Engine (规则引擎)
       ├─ 正则表达式匹配
       ├─ 关键词统计
       └─ 启发式算法
    ↓
Response
```

### 3.2 依赖关系

```
hmdp-ai-service (port 8090)
├── Spring Boot 3.3.5
├── Spring AI 1.0.0 (LLM框架)
├── spring-ai-alibaba-starter-dashscope (通义千问)
├── Jackson (JSON序列化)
└── Lombok (代码生成)
```

### 3.3 配置项 (application.yaml)

```yaml
server.port: 8090 (HMDP_AI_PORT)

spring.ai.dashscope:
  api-key: ${DASHSCOPE_API_KEY}        # 通义千问API密钥
  chat.options:
    model: qwen-turbo-flash             # 模型选择
    temperature: 0.2                    # 创意度(0.0-2.0)

logging.level:
  com.hmdp.ai: info (HMDP_AI_LOG_LEVEL)
```

---

## 四、数据模型设计

### 4.1 请求/响应DTO

| 功能 | 请求类 | 响应类 |
|-----|-------|-------|
| 口碑分块总结 | `ChunkSummaryRequest` | `ChunkSummaryResponse` |
| 口碑最终总结 | `FinalSummaryRequest` | `FinalSummaryResponse` |
| 意图解析 | `IntentParseRequest` | `IntentParseResponse` |
| 推荐理由 | `RecommendReasonRequest` | `RecommendReasonResponse` |
| 推荐重排 | `RecommendRerankRequest` | `RecommendRerankResponse` |
| 风控检查 | `ReviewRiskCheckRequest` | `ReviewRiskCheckResponse` |

### 4.2 核心数据模型

#### ReviewSnippet - 评论片段
```java
{
  "title": "很好吃",
  "content": "环境舒适，服务热情...",
  "shortSummary": "推荐！"
}
```

#### RecommendRerankShop - 推荐候选店铺
```java
{
  "id": 123L,
  "name": "烤肉店A",
  "typeName": "烤肉",
  "address": "朝阳区xxx街",
  "distance": 1200.0,        // 米
  "score": 92,               // 0-100
  "avgPrice": 85,            // 元
  "baseRankScore": 60.0,
  "shopDesc": "专业烤肉"
}
```

---

## 五、核心算法详解

### 5.1 Fallback模式 - 分块总结

```java
输入: reviews (List<ReviewSnippet>)
↓
1. 文本提取 (buildSummaryReviewText)
   - 合并title + content + shortSummary
   
2. 分割处理
   - 按"，。！？!?.;；\n\r"分割为句子
   - 过滤长度(4-60字符)的有效句子
   
3. 频率统计
   - 标准化句子(normalize)
   - 构建频率Map<String, Integer>
   
4. 排序提取
   - 高频句: freq.entrySet()
           .sorted(by value DESC)
           .limit(4)
   
   - 独特句: freq.entrySet()
           .filter(count == 1)
           .limit(3)
           
5. 清理
   - 移除噪声(AI虚构的"AI探店-编号")
   - 去重(distinct)
```

### 5.2 Fallback模式 - 推荐重排评分

```
初始分数 = baseRankScore + 50

正向加分:
  For each include_keyword matched:
    score += 2.4
  If "便宜" in query AND avgPrice ≤ 80:
    score += 1.8
  If "清淡" in query AND (contains "粥|汤|素食"):
    score += 2.0
  distance_score = max(0, (5000 - distance) / 1200)
  score += distance_score

负向减分:
  For each exclude_keyword matched:
    score -= 5.2
  If "清淡" in query AND (contains "火锅|烤肉|肉"):
    score -= 8.0
  If "便宜" NOT in query AND avgPrice ≥ 140:
    score -= 1.4

最终得分 = clamp(50 + score * 6, 0, 100)
```

### 5.3 风控检查 - 多层次检测

```
扫描1: 违规关键词 (正则表达式)
  - 违禁词: 赌博|毒品|枪支|...
  - 广告词: 微信|QQ|引流|...
  - 隐私词: 身份证|住址|...
  - 辱骂词: 傻逼|脑残|...

扫描2: 模式识别
  - 电话号码: \d{6,} 或 1\d{10}
  - 邮箱: \w+@\w+
  - 证件号: \d{17}[0-9Xx]

扫描3: 内容特征
  - 连续标点: !!!|???|￥￥￥
  - 不合理组合: "联系词" + "电话号"

风险评分 += 相应分值
```

---

## 六、关键特性

### 6.1 双引擎架构

| 引擎 | 优势 | 劣势 | 触发条件 |
|-----|-----|-----|--------|
| **LLM** | 语义理解强，质量高 | 成本高，速度慢 | API密钥存在 |
| **Fallback** | 无依赖，快速可靠 | 质量一般 | 无API密钥 或 LLM返回无效 |

### 6.2 智能清理

- **博客标题噪声**: 移除"AI探店-123-456"格式
- **模板句过滤**: 避免返回通用模板文案
- **重复去重**: distinct过滤

### 6.3 安全防护

```java
// 严格风险标签集合
private static final Set<String> STRICT_BLOCK_TAGS = 
  {"违法违禁", "广告引流", "联系方式", "隐私泄露", "人身攻击"}

// 包含任一标签 → 直接BLOCK
```

---

## 七、REST API 端点

```
POST /internal/ai/summarize/chunk          - 分块总结
POST /internal/ai/summarize/final          - 最终总结
POST /internal/ai/intent/parse             - 意图解析
POST /internal/ai/recommend/reason         - 生成推荐理由
POST /internal/ai/recommend/rerank         - 重排推荐
POST /internal/ai/review/risk-check        - 风控检查
```

---

## 八、工作流示例

### 场景：用户搜索"便宜的清淡粥店"

```
[1] 意图解析
    Input: query = "便宜的清淡粥店"
    ↓
    Output: {
      intentSummary: "用户寻求便宜、清淡的粥类餐饮",
      typeKeywords: ["粥店"],
      includeKeywords: ["清淡", "便宜"],
      excludeKeywords: []
    }

[2] 候选检索
    根据includeKeywords检索店铺
    → [粥店A(5km), 粥店B(2km), 粥店C(3km), ...]

[3] 推荐重排
    Input: 候选店铺 + includeKeywords + excludeKeywords
    ↓
    评分算法:
      粥店B(2km, 人均68元): baseScore=60 → 距离+2.2 → 便宜+1.8 → 清淡+2 → 最终74分 ✓
      粥店A(5km, 人均120元): baseScore=60 → 距离+0.8 → 人均≥140评估... → 最终50分
      粥店C(3km, 含火锅): baseScore=60 → 距离+1.4 → 清淡冲突-8 → 最终47分
    ↓
    Output: [粥店B, 粥店A, 粥店C]

[4] 生成理由
    粥店B → "距离2km，人均68元，匹配点2项，综合匹配分74。
             店铺信息：主营清粥，提供清淡选项。与\"便宜的清淡粥店\"较匹配。"

[5] 返回用户
    推荐顺序: 粥店B > 粥店A > 粥店C
    理由展示: 显示各店匹配理由和评分
```

---

## 九、性能考虑

1. **缓存**: 推荐模型可缓存热门查询的排序结果
2. **批处理**: 风控检查支持批量提交
3. **异步**: 可改造为异步LLM调用
4. **降级**: 无API时自动Fallback到规则引擎

---

## 十、扩展建议

1. **多模型支持**: 支持更多LLM供应商
2. **A/B测试**: 不同评分权重的对比
3. **用户反馈**: 收集点击反馈优化排序
4. **实时学习**: 根据转化数据调整算法参数
5. **多语言**: 支持英文等其他语言的意图识别

---

## 总结

**hmdp-ai-service** 是一个典型的**AI编排服务**，核心特点：

✅ **双引擎设计**: LLM + Fallback规则引擎  
✅ **多维度智能**: 从理解意图→推荐排序→内容风控  
✅ **生产级风控**: 多层次检测违规内容  
✅ **容错机制**: 无API密钥时优雅降级  
✅ **可配置**: 支持模型切换、温度调节等  

这是一个成熟的本地生活推荐系统的AI能力中枢。

