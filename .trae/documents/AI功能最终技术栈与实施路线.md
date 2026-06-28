# AI 智能求职辅导平台 — AI 功能最终技术栈与实施路线

> **文档定位**：本文档是 AI 功能实现的**最终技术栈决策书与实施路线**，基于逐项询问确认，作为后续开发的唯一执行依据。
>
> **适用约束**：
> - 3 人学生开发团队
> - 30 天敏捷开发周期
> - 已有 Docker Compose 单机部署（2 核 4G 云服务器）
> - 已有基线代码：Spring Boot 3.2.5 + Vue 3 + MySQL 8 + Redis
> - **关键约束**：技术栈需易于靠 AI 辅助从零上手实现
>
> **决策原则**：
> 1. **复用优先**：pom.xml 已有依赖不新增（POI/PDFBox）
> 2. **单体优先**：不拆微服务、不引入 Python AI 服务
> 3. **单库优先**：不新增 PostgreSQL/Milvus/ES 等中间件
> 4. **AI 友好**：选择 AI 训练数据丰富、生成代码无障碍的技术
> 5. **分两阶段**：第一阶段 MVP 交付，第二阶段按需增强
>
> **文档关系**：前序三份文档（诊断 / 选型对比 / 实现完善方向）作为决策过程留存，不再修改。**实施以本文档为准**。

---

## 一、最终技术栈总表

| 层 | 技术 | 版本 | 来源 | AI 从零上手难度 | 最终选择原因 |
|----|------|------|------|-----------------|--------------|
| **前端框架** | Vue 3 + Vite | 3.4 / 5.2 | 已有 | 低 | 组合式 API + TS，AI 训练数据极丰富 |
| **UI 组件库** | Element Plus | 2.7 | 已有 | 低 | 中文文档完善，AI 生成表单/表格无障碍 |
| **图表库** | ECharts | 6.1 | 已有 | 低 | 用于 AI 质量看板（P2），AI 熟悉配置项 |
| **后端框架** | Spring Boot | 3.2.5 | 已有 | 低 | Java 生态最主流，AI 最熟悉 |
| **ORM** | MyBatis-Plus | 3.5.5 | 已有 | 极低 | CRUD + Lambda 包装器，AI 生成 Mapper 零障碍 |
| **业务数据库** | MySQL | 8 | 已有 | 极低 | SQL 标准，单库承载业务+向量 JSON 字段 |
| **缓存** | Redis | — | 已有 | 低 | KV 模型，用于缓存+配额计数+面试会话 |
| **AI 模型** | DeepSeek API | Chat + Embedding | 新接入 | 低 | OpenAI 兼容协议，AI 最熟悉的调用方式 |
| **HTTP 客户端（同步）** | RestTemplate | — | 已有 | 低 | 存量同步 AI 接口零改动 |
| **HTTP 客户端（流式）** | WebClient | — | 新增 webflux | 中 | 仅用于 SSE 流式接口（客服+职业探索） |
| **文件解析** | Apache POI + PDFBox | 5.2.5 / 2.0.30 | pom.xml 已有 | 低 | API 直观，AI 熟悉，简历 PDF/DOCX 提取 |
| **稳定性** | Resilience4j | 2.2.0 | 新增 | 中 | 注解声明式，AI 熟悉 Spring AOP |
| **流式输出** | SSE | — | Spring 原生 | 中 | `TEXT_EVENT_STREAM_VALUE`，AI 可生成 |
| **Prompt 管理** | 分层混合 + 最简版本 | — | 自研 | 低 | MyBatis-Plus CRUD，AI 最擅长 |
| **RAG / 向量检索** | Embedding + MySQL JSON + 内存余弦 | — | 自研 | **极低** | 零新中间件，HTTP 调用+数学计算，AI 最易生成 |
| **多模型兜底** | DeepSeek 单一 + 规则兜底 | — | 已有 | 低 | OpenAI 兼容协议统一封装预留扩展 |
| **JWT 鉴权** | jjwt | 0.12.5 | 已有 | 低 | 已实现 |
| **密码加密** | Spring Security Crypto (BCrypt) | — | 已有 | 低 | 已实现 |

### 新增依赖清单（仅 2 个 + AOP）

```xml
<!-- pom.xml 新增 -->
<!-- 1. Resilience4j：AI 调用重试/限流/熔断 -->
<dependency>
    <groupId>io.github.resilience4j</groupId>
    <artifactId>resilience4j-spring-boot3</artifactId>
    <version>2.2.0</version>
</dependency>

<!-- 2. WebClient：仅用于 SSE 流式接口（客服+职业探索） -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-webflux</artifactId>
</dependency>

<!-- 3. AOP 支持（Resilience4j 注解生效需要） -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-aop</artifactId>
</dependency>
```

```json
// frontend/package.json 无需新增，原生 fetch + ReadableStream 即可
```

### docker-compose 无新增服务

```yaml
# 现有服务即可，不新增 PostgreSQL/Milvus/ES
services:
  mysql:     # 业务库 + 向量 JSON 字段（第二阶段）
  redis:     # 缓存 + 配额计数 + 面试会话
  backend:   # Spring Boot
  frontend:  # Vue 3
  nginx:     # 反向代理（SSE 需 proxy_buffering off）
```

---

## 二、11 项技术决策最终结果与选择原因

### 决策 1：AI 模型接入 — OpenAI 兼容协议统一封装（DeepSeek 单一）

| 维度 | 选择 |
|------|------|
| **最终选择** | OpenAI 兼容协议统一封装，当前仅接入 DeepSeek API |
| **新增依赖** | 无 |
| **工作量** | 封装约 0.5 天（P1 阶段） |

**选择原因**：
1. **当前阶段仅接入 DeepSeek**：不立即接入第二模型提供商，降低配置、鉴权、Prompt 适配、测试复杂度
2. **代码层统一封装**：封装 Chat Completion 请求/响应结构（model、messages、temperature、max_tokens、stream、response_format 等字段），业务层只调用统一 `AiModelClient`/`DeepSeekService`，不直接感知具体厂商
3. **扩展预留**：后续如需接入通义千问、智谱 GLM、OpenAI、硅基流动，只需新增 Provider 配置和适配器，不改业务代码
4. **兼顾交付与扩展**：适合 3 人小团队先完成 MVP，再逐步扩展多模型兜底

**启用备用模型的条件**：DeepSeek 调用稳定性长期不足、演示环境频繁失败、接口额度无法满足、或进入第二阶段需要更高可用性时，新增 Provider 配置和适配器即可。

**核心实现**：
```java
/**
 * 统一 AI 模型客户端接口（OpenAI 兼容协议）
 * 业务层只调用此接口，不感知具体厂商
 */
public interface AiModelClient {
    String chat(ChatRequest request);           // 同步调用
    Flux<String> chatStream(ChatRequest request); // 流式调用
}

/**
 * DeepSeek 实现（当前唯一实现，未来可新增通义千问等）
 */
@Service
public class DeepSeekClient implements AiModelClient {
    // 封装统一的请求构建（model/messages/temperature/max_tokens/stream/response_format）
    // 通过 RestTemplate 同步调用，WebClient 流式调用
}
```

---

### 决策 2：AI HTTP 客户端 — RestTemplate + WebClient 流式

| 维度 | 选择 |
|------|------|
| **最终选择** | RestTemplate（存量同步）+ WebClient（仅 SSE 流式） |
| **新增依赖** | spring-boot-starter-webflux |
| **工作量** | 0（共存配置） |

**选择原因**：
1. **存量零改动**：简历优化、企业推荐、差距分析、模拟面试评分等结构化 JSON 场景继续用 RestTemplate，降低改造成本
2. **流式按需引入**：仅智能客服、职业探索问答 2 个场景用 WebClient + SSE，实现边生成边返回
3. **不做全项目响应式改造**：避免 Service 层全改响应式，3 人团队风险大
4. **AI 友好**：RestTemplate 是 Spring 原生 AI 最熟悉；WebClient `Flux<ServerSentEvent>` AI 可生成

**核心实现**：
```java
// 存量同步调用（零改动，结构化 JSON 场景）
restTemplate.postForObject(apiUrl, request, String.class);

// 新增流式调用（仅客服+职业探索 2 个接口）
webClient.post().uri(apiUrl)
    .bodyValue(request)
    .retrieve()
    .bodyToFlux(String.class)
    .filter(line -> !line.equals("[DONE]"))
    .map(this::extractStreamContent);
```

---

### 决策 3：RAG / 向量检索 — Embedding + MySQL JSON + 内存余弦 ⭐

| 维度 | 选择 |
|------|------|
| **最终选择** | Embedding API + MySQL JSON 字段 + Java 内存余弦相似度 |
| **新增依赖** | 无（复用 MySQL + RestTemplate） |
| **新增服务** | 无（不部署 PostgreSQL/Milvus/ES/Redis Stack） |
| **工作量** | 第一阶段 0 天（MySQL 关键词），第二阶段约 3 天 |

**选择原因（关键决策，重点说明）**：
1. **零新中间件**：不部署 PostgreSQL/Milvus/Redis Stack/ES，部署和学习成本最低
2. **AI 最易从零生成代码**：核心逻辑仅 HTTP 调用 Embedding API + Java 数学计算余弦相似度，AI 训练数据极丰富，无生疏 API
3. **单库共存**：向量以 JSON 字段存入 MySQL，与业务数据同库，无双库同步问题
4. **数据量适配**：FAQ + 岗位 JD + 候选人画像摘要 < 5000 条，内存计算性能足够（< 50ms）
5. **平滑升级**：数据量扩大或性能不足时，可平滑升级到 PGVector（向量字段语义不变）

**不选其他方案的原因**：
- **PGVector**：需新增 PG 容器 + 双库同步 + HNSW 索引，AI 生成双库同步代码有门槛
- **Redis Stack**：RediSearch 向量语法 AI 训练数据少，生成代码需查文档
- **Milvus**：独立集群运维成本高，3 人团队无法承担
- **纯 MySQL 关键词不做向量**：无语义匹配，FAQ 同义词无法命中

**分阶段实施**：

| 阶段 | 检索方案 | 覆盖场景 | 说明 |
|------|----------|----------|------|
| **第一阶段** | MySQL 关键词 + 规则评分 | 全部 6 个 AI 场景 | 0 成本，保证无向量时也能运行 |
| **第二阶段** | MySQL 关键词召回 + Embedding 内存余弦重排 | **仅 2 场景**：客服 FAQ + 企业候选人 | 向量以 JSON 存 MySQL，Java 内存算余弦 |

**第二阶段核心实现**：

```sql
-- MySQL 扩展向量字段（JSON 存储，无需新表）
ALTER TABLE faq ADD COLUMN embedding_json JSON COMMENT 'FAQ 问题的 Embedding 向量（JSON 数组）';
ALTER TABLE resume ADD COLUMN embedding_json JSON COMMENT '候选人画像摘要的 Embedding 向量';
```

```java
/**
 * Embedding 服务（调用 DeepSeek Embedding API）
 */
@Service
public class EmbeddingService {
    
    private final RestTemplate restTemplate;
    private final String apiUrl = "https://api.deepseek.com/v1/embeddings";
    
    /**
     * 生成文本的 Embedding 向量
     */
    public float[] embed(String text) {
        Map<String, Object> body = Map.of("model", "text-embedding-v1", "input", text);
        Map<String, Object> resp = restTemplate.postForObject(apiUrl, body, Map.class);
        List<Number> vector = (List<Number>) ((List<Map>) resp.get("data")).get(0).get("embedding");
        float[] result = new float[vector.size()];
        for (int i = 0; i < vector.size(); i++) result[i] = vector.get(i).floatValue();
        return result;
    }
}
```

```java
/**
 * 混合检索：MySQL 关键词召回 + 内存余弦重排
 * 仅用于客服 FAQ 匹配、企业候选人推荐 2 个场景
 */
public List<Faq> hybridSearchFaq(String query, int topN) {
    // 阶段 1：MySQL 关键词召回 Top 50（快速粗筛）
    List<Faq> candidates = faqMapper.selectByKeywords(query, 50);
    if (candidates.isEmpty()) return Collections.emptyList();
    
    // 阶段 2：调 Embedding API 生成查询向量
    float[] queryVector = embeddingService.embed(query);
    
    // 阶段 3：内存计算余弦相似度，重排 Top N
    return candidates.stream()
        .map(faq -> Map.entry(faq, cosineSimilarity(queryVector, parseEmbedding(faq.getEmbeddingJson()))))
        .sorted((a, b) -> Float.compare(b.getValue(), a.getValue()))  // 降序
        .limit(topN)
        .map(Map.Entry::getKey)
        .toList();
}

/**
 * 余弦相似度计算（Java 基础数学，AI 最易生成）
 */
private float cosineSimilarity(float[] a, float[] b) {
    float dot = 0, normA = 0, normB = 0;
    for (int i = 0; i < a.length; i++) {
        dot += a[i] * b[i];
        normA += a[i] * a[i];
        normB += b[i] * b[i];
    }
    return (float) (dot / (Math.sqrt(normA) * Math.sqrt(normB)));
}
```

**第二阶段明确「不覆盖」的场景**：

| 场景 | 是否启用向量 | 理由 |
|------|--------------|------|
| R-014 客服 FAQ 匹配 | ✅ 启用 | 同义词/近义词匹配，提升命中率 |
| R-010 企业候选人推荐 | ✅ 启用 | 岗位 JD 与候选人画像语义重排 |
| R-008 简历优化 | ❌ 不启用 | 单简历 vs 单 JD，Prompt 塞全文即可 |
| R-004 职业探索 | ❌ 不启用 | DeepSeek 总结能力足够 |
| R-009 模拟面试 | ❌ 不启用 | 题库固定，无需语义召回 |
| R-006 差距分析 | ❌ 不启用 | 规则计算 + AI 建议，无需向量 |

**升级路径**：数据量 > 5000 条或查询延迟 > 200ms 时，平滑升级到 PGVector。

---

### 决策 4：AI 调用稳定性 — Resilience4j

| 维度 | 选择 |
|------|------|
| **最终选择** | Resilience4j 2.2.0 |
| **新增依赖** | resilience4j-spring-boot3 + spring-boot-starter-aop |
| **工作量** | 1 天（P1 阶段） |

**选择原因**：
1. **一站式解决**：Retry + RateLimiter + CircuitBreaker，统一解决 DeepSeek API 超时、429 限流、5xx 错误、网络异常
2. **声明式配置**：`@Retry/@RateLimiter/@CircuitBreaker` 注解 + yml 配置，改动小
3. **AI 友好**：注解声明式 AI 熟悉 Spring AOP，配置 yml AI 可生成
4. **熔断保护**：DeepSeek 故障时快速失败，避免线程堆积（当前痛点）

**重试策略细节**：
- 重试仅针对网络异常、超时、429 限流、5xx 服务端错误
- **4xx 参数错误不重试**（无意义）
- Retry 最多 3 次，采用 1s、2s、4s 指数退避

**核心配置**：
```yaml
resilience4j:
  retry:
    instances:
      deepseek:
        max-attempts: 3                    # 最多重试 3 次
        wait-duration: 1s
        exponential-backoff-multiplier: 2  # 指数退避：1s→2s→4s
        retry-exceptions:
          - org.springframework.web.client.RestClientException
  ratelimiter:
    instances:
      deepseek:
        limit-for-period: 5                # 5 QPS（DeepSeek 免费档安全值）
        limit-refresh-period: 1s
  circuitbreaker:
    instances:
      deepseek:
        failure-rate-threshold: 50         # 失败率 50% 触发熔断
        wait-duration-in-open-state: 30s   # 熔断 30 秒后半开
```

**fallback 兜底**：
- `fallbackMethod` 返回本地规则兜底结果或友好错误提示
- 写入 `ai_call_log`，记录 `FALLBACK`、`CIRCUIT_OPEN`、`RATE_LIMITED` 等状态

---

### 决策 5：简历文件文本提取 — POI + PDFBox

| 维度 | 选择 |
|------|------|
| **最终选择** | Apache POI 5.2.5 + PDFBox 2.0.30 |
| **新增依赖** | 无（pom.xml 已有） |
| **工作量** | 0.5 天（P0 阶段） |

**选择原因**：
1. **依赖已就绪**：pom.xml 已引入 POI 5.2.5 和 PDFBox 2.0.30，零新增成本
2. **覆盖需求**：需求仅要求 PDF/DOCX 两种格式，POI+PDFBox 完全覆盖
3. **AI 友好**：API 直观（`PDDocument.load()` → `PDFTextStripper().getText()`），AI 训练数据丰富

**实现边界**：
- PDF 使用 PDFBox 提取文本，DOCX 使用 POI `XWPFDocument` 提取段落和表格文本
- 上传仅支持 pdf/docx，限制文件大小 5MB
- 提取失败时返回明确错误提示，不调用 AI
- **扫描版 PDF 暂不做 OCR**，后续需要时再扩展

**核心实现**：
```java
/**
 * 文件文本提取工具（支持 PDF/DOCX）
 */
public class FileUtil {
    public static String extractText(MultipartFile file) throws IOException {
        String filename = file.getOriginalFilename();
        if (filename.endsWith(".pdf")) return extractPdfText(file);
        if (filename.endsWith(".docx")) return extractDocxText(file);
        throw new BusinessException("仅支持 PDF/DOCX 格式");
    }
    // PDFBox: PDDocument.load() → PDFTextStripper().getText()
    // POI: XWPFDocument 遍历段落提取文本
}
```

---

### 决策 6：Prompt 模板管理 — 分层混合 + 最简版本

| 维度 | 选择 |
|------|------|
| **最终选择** | 分层混合（基础文件 + 业务 DB）+ 最简版本管理 |
| **新增依赖** | 无（复用 MyBatis-Plus） |
| **工作量** | 1 天（P1 阶段） |

**选择原因**：
1. **基础层稳定**：角色定义、输出格式约束、核心安全边界等稳定内容走文件，避免被误改
2. **业务层灵活**：客服话术、职业探索、差距分析建议、企业推荐理由等需频繁调整的内容存入 MySQL，支持热更新
3. **最小化迁移**：DB 初始为空，需要调整哪个模板才录入
4. **最简版本管理**：每个场景只允许一个 active 版本，支持回滚上一版

**分层归属**：

| 模板文件 | 归属层 | 可后台编辑 |
|----------|--------|------------|
| `career_exploration.txt` | 业务层（DB） | ✅ |
| `customer_service.txt` | 业务层（DB） | ✅ |
| `resume_optimize.txt` | 基础层（文件） | ❌ |
| `mock_interview.txt` | 基础层（文件） | ❌ |
| `project_parse.txt` | 基础层（文件） | ❌ |
| `gap_analysis.txt`（新增） | 业务层（DB） | ✅ |

**版本管理表结构**：
```sql
CREATE TABLE prompt_template (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    name        VARCHAR(64) NOT NULL COMMENT '模板名（对应文件名）',
    scene       VARCHAR(32) NOT NULL COMMENT '场景标识',
    content     TEXT NOT NULL COMMENT '模板完整内容',
    version     INT NOT NULL DEFAULT 1 COMMENT '版本号',
    is_active   TINYINT NOT NULL DEFAULT 1 COMMENT '1=当前激活 0=历史版本',
    description VARCHAR(255) COMMENT '模板描述/修改说明',
    created_by  BIGINT COMMENT '创建人',
    updated_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_scene_version (scene, version)
);

-- ai_call_log 记录 Prompt 版本，便于排查效果
ALTER TABLE ai_call_log ADD COLUMN prompt_name VARCHAR(64) COMMENT '使用的 Prompt 名称';
ALTER TABLE ai_call_log ADD COLUMN prompt_version INT DEFAULT 0 COMMENT 'Prompt 版本（0=文件版本）';
```

**版本管理规则**：
- 每次编辑新建版本（`version = max+1`，`is_active=1`），旧版本 `is_active=0`
- 回滚：激活指定版本，当前版本停用
- 不做灰度分流（小团队够用）

---

### 决策 7：SSE 流式输出范围 — 仅客服 + 职业探索

| 维度 | 选择 |
|------|------|
| **最终选择** | 仅智能客服、职业探索 2 个场景接入 SSE |
| **新增依赖** | spring-boot-starter-webflux（与决策 2 共用） |
| **工作量** | 1.5 天（P2 阶段） |

**选择原因**：
1. **对话/长文本场景**：客服、职业探索属于对话/长文本生成，用户等待感最强，适合边生成边展示
2. **结构化 JSON 不做流式**：简历优化、企业推荐、差距分析、模拟面试评分主要返回结构化 JSON，同步接口更稳定，避免流式解析 JSON 增加复杂度
3. **改造范围小**：仅为客服和职业探索新增 stream 接口

**核心实现**：
```java
// 后端：仅为客服和职业探索新增 stream 接口
@GetMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
public Flux<ServerSentEvent<String>> chatStream(@RequestParam String question) {
    return customerServiceService.chatStream(question)
        .map(content -> ServerSentEvent.<String>builder().data(content).build());
}
```

```typescript
// 前端：fetch + ReadableStream 接收，兼容 JWT 请求头
const response = await fetch('/api/customer/chat/stream?question=' + encodeURIComponent(text), {
    headers: { 'Authorization': 'Bearer ' + token }
});
const reader = response.body!.getReader();
// 逐 chunk 拼接，实现打字机效果
```

```nginx
# Nginx 关键配置（SSE 必需）
location /api/ {
    proxy_buffering off;  # 关闭缓冲，否则 SSE 失效
}
```

---

### 决策 8：工程闭环第一阶段范围 — AI 额度控制 + Prompt 版本管理

| 闭环 | 是否纳入第一阶段 | 优先级 | 最简实现 | 工作量 |
|------|------------------|--------|----------|--------|
| **AI 调用额度控制** | ✅ 纳入 | P1 | Redis 计数器 | 0.5 天 |
| **Prompt 版本管理** | ✅ 纳入（见决策 6） | P1 | version+is_active+回滚 | 0.5 天 |
| AI 质量反馈闭环 | ❌ 暂列 P2 | P2 | `ai_feedback` 单表 | 0.5 天 |
| FAQ 知识库管理 | ❌ 暂列 P2 | P2 | FAQ CRUD + 未命中收集 | 1 天 |

**选择原因**：
1. **P1 纳入额度控制**：避免 DeepSeek API 被频繁调用或恶意刷接口，实现简单（Redis 计数器）
2. **P1 纳入 Prompt 版本管理**：支撑 Prompt 快速调整和回滚，避免越改越乱
3. **P2 暂缓反馈闭环和 FAQ 管理**：待核心 AI 功能稳定后再做，避免第一阶段范围过大

#### AI 调用额度控制实现

```yaml
# application.yml 配额规则
ai:
  quota:
    rules:
      STUDENT:RESUME: 3           # 学生简历优化 3 次/天
      STUDENT:INTERVIEW: 2        # 模拟面试 2 次/天
      STUDENT:CAREER_EXPLORE: 5   # 职业探索 5 次/天
      STUDENT:CUSTOMER_SERVICE: 20 # 客服 20 次/天
      ENTERPRISE:PROJECT_PARSE: 5 # HR 项目解析 5 次/天
      ENTERPRISE:RECOMMEND: 10    # HR 候选人推荐 10 次/天
```

```java
/**
 * AI 调用配额校验（Redis 计数器，24 小时 TTL）
 */
public boolean checkQuota(Long userId, String role, String scene) {
    // Redis Key: ai:quota:{userId}:{scene}:{yyyyMMdd}
    String key = String.format("ai:quota:%d:%s:%s", userId, scene, 
            LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE));
    Long count = redisTemplate.opsForValue().increment(key);
    if (count == 1) redisTemplate.expire(key, Duration.ofDays(1));
    return count <= quotaConfig.getLimit(role, scene);
}
```

**客服 FAQ 优先命中**：FAQ 命中不消耗 AI 配额，未命中才调 AI 并校验配额。

---

### 决策 9：第一阶段实施顺序 — P0 → P1 → P2

**选择原因**：
1. **P0 先修复核心演示闭环**：简历优化、企业推荐是当前最大缺口，不修复核心 AI 功能会失真或不可演示
2. **P1 再补齐需求完整性和稳定性**：模拟面试追问、差距分析 AI、Resilience4j、额度控制、Prompt 版本
3. **P2 最后做体验增强**：SSE 流式、质量反馈、FAQ 管理、监控看板
4. **不建议先做基础设施**：避免第一阶段前期投入过多但业务不可演示

详见第三章实施路线图。

---

### 决策 10：多模型兜底启用时机 — 第一阶段不启用备用

**选择原因**：
1. **第一阶段仅 DeepSeek + 规则兜底**：通过本地规则、缓存结果、预设模板兜底
2. **OpenAI 兼容协议只做统一封装**：不立即接入第二模型提供商
3. **避免增加复杂度**：双提供商增加配置、鉴权、模型输出差异、Prompt 适配、测试和成本控制复杂度

**启用备用模型的条件**：
- DeepSeek 调用稳定性长期不足
- 演示环境频繁失败
- 接口额度无法满足
- 进入第二阶段需要更高可用性

届时只需新增 Provider 配置和适配器，不改业务层代码。

---

### 决策 11：文档输出方式 — 新建独立最终文档

**选择原因**：
1. **不覆盖原有三份文档**：保留前期诊断、选型和实现方向的过程记录
2. **新建独立最终文档**：集中沉淀最终确认的技术栈、阶段范围、实施顺序
3. **避免多份历史文档互相冲突**：后续开发以本文档为准，原文档仅作参考

---

## 三、第一阶段实施路线图（P0 → P1 → P2）

### 第一阶段技术栈

```
Spring Boot 3.2.5 + Vue 3 + DeepSeek API + MySQL 8 + Redis 
+ POI/PDFBox + Resilience4j + SSE（仅客服+职业探索）
```

### P0：核心缺陷修复（约 3.5 天）

| 任务 | 涉及决策 | 工作量 | 验证方式 |
|------|----------|--------|----------|
| R-008 简历优化 PDF/DOCX 文本提取 | 决策 5 POI+PDFBox | 0.5 天 | 上传 PDF，验证 `resume_text` 为正文 |
| R-010 企业推荐真正接入 DeepSeek（项目解析 + 候选人推荐） | 决策 1+2 | 1.5 天 | 输入项目描述，验证岗位/技能与描述相关 |
| 企业推荐结果持久化（`recommendation_record` 表） | — | 0.5 天 | `getHistory` 接口可查询历史推荐 |
| 移除硬编码 API Key | — | 0.5 天 | application-dev.yml 无明文 Key |
| AI 输出 JSON 格式校验和兜底 | 决策 1 | 0.5 天 | AI 返回非法 JSON 时走兜底，不报 500 |

### P1：需求完整性与稳定性（约 6 天）

| 任务 | 涉及决策 | 工作量 | 验证方式 |
|------|----------|--------|----------|
| R-009 模拟面试 FOLLOW_UP 追问（状态机） | — | 1.5 天 | 回答后可能出现追问（非直接下一题） |
| R-006 差距分析接入 AI 个性化建议 | 决策 6 | 1 天 | `suggestions` 非固定文案，与差距内容相关 |
| DeepSeek 基础服务改造（Resilience4j 重试/限流/熔断 + OpenAI 兼容协议统一封装） | 决策 1+4 | 1.5 天 | `ai_call_log` 出现熔断/限流记录 |
| AI 调用额度控制（Redis 计数器） | 决策 8 | 0.5 天 | 超限用户收到友好提示 |
| Prompt 分层混合 + 最简版本管理 | 决策 6 | 1 天 | 后台编辑模板后热更新生效，可回滚 |
| 企业推荐可解释评分（结构化评分 + AI 推荐理由） | — | 0.5 天 | 候选人卡片展示匹配/缺失技能、推荐理由 |

### P2：体验增强（约 5.5 天）

| 任务 | 涉及决策 | 工作量 | 验证方式 |
|------|----------|--------|----------|
| R-014 智能客服 SSE 流式 | 决策 2+7 | 0.5 天 | 首字响应 2-3 秒 |
| R-004 职业探索 SSE 流式 | 决策 2+7 | 0.5 天 | 首字响应 2-3 秒 |
| 兜底质量提升（企业推荐/面试评分非随机数） | — | 1 天 | 兜底结果合理，非 Math.random() |
| AI 质量反馈闭环（`ai_feedback` 单表） | — | 0.5 天 | 用户可对 AI 结果点有帮助/没帮助 |
| FAQ 知识库管理（CRUD + 未命中收集） | — | 1 天 | 管理端可维护 FAQ，未命中问题可转 FAQ |
| AI 调用监控看板（ECharts） | — | 1 天 | 管理端可查看各场景调用数、降级率 |
| 配置层预留（D5/D6 backup 段、Embedding 配置） | 决策 3+10 | 1 天 | yml 中预留配置段，注释说明第二阶段启用 |

### 第一阶段合计

- **P0 + P1 + P2 = 约 15 天**，3 人协同约 5 天可完成
- 符合 30 天开发周期（含调试、联调、文档缓冲）

**第一阶段交付能力**：6 大 AI 功能全部可用 + 工程闭环最简版 + Resilience4j 稳定性保障。

---

## 四、第二阶段实施路线图（演示后迭代）

### 第二阶段技术栈

```
第一阶段 + Embedding API（向量检索）+ 通义千问（多模型容灾，按需）
```

### 第二阶段任务（约 3.5 天）

| 任务 | 涉及决策 | 工作量 | 验证方式 |
|------|----------|--------|----------|
| Embedding 服务接入 + MySQL JSON 向量字段 | 决策 3 | 1 天 | 文本可生成向量并存入 MySQL JSON |
| R-014 客服 FAQ 向量重排 | 决策 3 | 1 天 | 同义词/近义词 FAQ 命中率提升 |
| R-010 企业候选人向量重排 | 决策 3 | 1 天 | 候选人与岗位 JD 语义相关度提升 |
| D6 backup 启用（通义千问，主熔断自动切备） | 决策 10 | 0.5 天 | 主模型熔断后备用模型正常返回 |

**第二阶段交付能力**：FAQ/候选人语义匹配精度提升 + 多模型容灾。

### 明确不引入的技术

| 技术 | 是否引入 | 理由 |
|------|----------|------|
| PostgreSQL PGVector | ❌ 不引入（除非数据量 > 5000 或延迟 > 200ms） | Embedding+MySQL JSON 已满足当前数据量 |
| Milvus / Qdrant | ❌ 不引入 | 独立集群运维成本高 |
| Elasticsearch | ❌ 不引入 | 关键词检索 MySQL 已够用 |
| LangChain 服务化架构 | ❌ 不引入 | 不拆 Python AI 服务，保持单体 |
| 复杂知识库平台 | ❌ 不引入 | 仅 FAQ 表 + 未命中收集 |

---

## 五、最终技术栈「AI 从零上手」评估

| 技术 | AI 训练数据丰富度 | 代码生成难度 | 文档可查性 | 综合评估 |
|------|-------------------|--------------|------------|----------|
| Vue 3 + Element Plus | 极丰富 | 低 | 中文完善 | ✅ 极易上手 |
| Spring Boot + MyBatis-Plus | 极丰富 | 极低 | 中文完善 | ✅ 极易上手 |
| MySQL + SQL | 极丰富 | 极低 | 标准 | ✅ 极易上手 |
| Redis | 丰富 | 低 | 完善 | ✅ 易上手 |
| DeepSeek API（OpenAI 兼容） | 极丰富 | 低 | 完善 | ✅ 极易上手 |
| RestTemplate | 极丰富 | 极低 | Spring 原生 | ✅ 极易上手 |
| WebClient + Flux | 丰富 | 中 | Spring 文档 | ⚠️ 可上手 |
| POI + PDFBox | 丰富 | 低 | Apache 文档 | ✅ 易上手 |
| Resilience4j | 丰富 | 中 | 官方文档 | ⚠️ 可上手 |
| SSE（Spring 原生） | 丰富 | 中 | Spring 文档 | ⚠️ 可上手 |
| **Embedding + 内存余弦** | **极丰富** | **极低** | **数学公式** | **✅ 极易上手** |
| Prompt 管理（MyBatis CRUD） | 极丰富 | 极低 | — | ✅ 极易上手 |

**结论**：最终技术栈全部基于 Spring Boot 生态主流技术 + 标准 SQL + 基础数学，AI 辅助编码从零上手无障碍。唯一需要稍多关注的是 WebClient/Flux（响应式）和 Resilience4j（注解配置），但均有丰富的 AI 训练数据和官方文档支撑。

---

## 六、与现有文档的关系

| 文档 | 定位 | 状态 |
|------|------|------|
| [AI功能实现梳理与完善方向.md](file:///d:/HuaweiMoveData/Users/20403/Desktop/软件实训项目/.trae/documents/AI功能实现梳理与完善方向.md) | 基于代码现状的问答梳理（问题诊断） | 历史文档，不改，仅作参考 |
| [AI功能技术选型方案.md](file:///d:/HuaweiMoveData/Users/20403/Desktop/软件实训项目/.trae/documents/AI功能技术选型方案.md) | 技术选型多方案对比（决策依据） | 历史文档，不改，仅作参考 |
| [AI功能实现与完善方向_基于技术选型.md](file:///d:/HuaweiMoveData/Users/20403/Desktop/软件实训项目/.trae/documents/AI功能实现与完善方向_基于技术选型.md) | 实现与完善方向问答（执行指引） | 历史文档，不改，仅作参考 |
| **本文档** | **最终技术栈与实施路线（实施依据）** | **当前文档，实施以本文档为准** |

**四份文档关系**：诊断 → 选型对比 → 实现指引 → **最终选型与实施路线（本文档）**。本文档是实施阶段的唯一执行依据，前序三份文档作为决策过程留存。实施时以本文档为准。
