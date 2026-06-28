# AI 智能求职辅导平台 — AI 功能技术选型方案（多方案对比）

> **目标**：为项目中所有 AI 相关功能的实现与完善，提供多种候选技术方案对比，并结合「3 人小团队 + 30 天开发周期 + 已有 Docker Compose 单机部署」的约束给出推荐。
>
> **基线技术栈**：Spring Boot 3.2.5 + MyBatis-Plus 3.5.5 + Vue 3 + Element Plus + MySQL 8 + Redis + DeepSeek API
> **已存在但未使用的依赖**：Apache POI 5.2.5、PDFBox 2.0.30（pom.xml 已引入，代码未调用）

---

## 决策点总览（含最终推荐）

| 编号 | 决策点 | 影响 | 状态 | 候选方案 | 新增依赖 |
|------|--------|------|------|----------|----------|
| D1 | 简历文件文本提取 | R-008 简历优化 | ✅ 已确认 | A.POI+PDFBox（复用现有依赖） | 无（pom.xml 已有） |
| D2 | AI HTTP 客户端 | 所有 AI 调用 | ✅ 已确认 | A.RestTemplate（存量）+ WebClient（流式） | spring-boot-starter-webflux |
| D3 | 流式输出方式 | 用户体验 | ✅ 已确认 | A.SSE（仅客服+职业探索） | spring-boot-starter-webflux |
| D4 | AI 重试与限流 | 稳定性 | ✅ 已确认 | A.Resilience4j | resilience4j-spring-boot3 |
| D5 | 向量检索 / RAG | 推荐质量 | ✅ 已确认 | B.PGVector（MySQL 关键词过滤 + PG 向量语义重排） | postgresql 驱动 + pgvector 扩展 |
| D6 | 多模型兜底 | 可用性 | ✅ 已确认 | A.DeepSeek 单一+规则（OpenAI 兼容协议封装预留扩展） | 无 |
| D7 | Prompt 模板管理 | 可维护性 | ✅ 已确认 | C.分层混合（基础文件+业务DB+增量覆盖） | 无 |

> **全部决策已确认**。整体策略围绕「小团队轻量化、低运维、平衡推荐质量与交付成本」统一规划：
> - **D1/D2**：复用现有依赖，最小化新增包，存量接口零改动
> - **D5**：采用 MySQL 关键词过滤 + PG 向量语义重排的混合检索，规避 Redis 高内存、Milvus 复杂集群运维
> - **D6**：基于 OpenAI 兼容协议统一封装请求层，预留第二厂商扩展能力，分阶段提升可用性
> - **配套稳定性**：Resilience4j 统一实现重试/限流/熔断；Prompt 模板文件+DB 版本表支持热更新与回滚
> - **分两阶段迭代**：先保障简历优化 P0 上线，再迭代语义检索、多厂商容灾优化

---

## D1：简历文件文本提取

### 背景
[ResumeServiceImpl.java](file:///d:/HuaweiMoveData/Users/20403/Desktop/软件实训项目/软件实训项目-code/软件实训项目-code/软件实训项目-第一版/backend/src/main/java/com/xuelian/career/service/impl/ResumeServiceImpl.java) 当前只把文件 URL 字符串塞进 Prompt，AI 无法读到简历内容。pom.xml 中 POI 和 PDFBox 已就绪但未被使用。

### 方案对比

| 维度 | 方案 A：POI + PDFBox（已有依赖） | 方案 B：Apache Tika（统一抽象） | 方案 C：前端提取（mozilla/pdf.js） |
|------|----------------------------------|--------------------------------|-----------------------------------|
| **依赖** | 已在 pom.xml，零新增 | 需新增 tika-core + tika-parsers（~30MB） | 前端引入 pdf.js，后端零依赖 |
| **支持格式** | PDF / DOCX（需求要求） | PDF / DOC / DOCX / RTF / HTML 等数十种 | 仅 PDF，DOCX 需额外库 |
| **学习成本** | 低，API 直接 | 中，需理解 Tika Facade | 中，前端需额外开发 |
| **解析质量** | PDF 文本提取质量好；DOCX 完美 | 质量稳定，统一接口 | PDF 质量依赖前端库 |
| **部署影响** | 无 | JAR 体积增大 ~30MB | 前端 bundle 增大 |
| **开发工作量** | 0.5 天 | 1 天 | 1.5 天（前后端都改） |

### 各方案优缺点

**方案 A：POI + PDFBox**
- ✅ 依赖已在 pom.xml，零新增成本；需求仅要求 PDF/DOCX，完全覆盖；API 直接，学习成本最低；0.5 天即可完成
- ❌ 不支持 .doc/rtf 等旧格式；PDF 扫描件（图片）无法提取文本

**方案 B：Apache Tika**
- ✅ 统一接口支持数十种格式；解析质量稳定；未来扩展性强
- ❌ JAR 体积增大 ~30MB；学习成本中等；需求仅要求 PDF/DOCX，Tika 能力过剩

**方案 C：前端 pdf.js 提取**
- ✅ 后端零依赖；减轻服务器压力
- ❌ 需前后端都改；仅支持 PDF，DOCX 需额外库；前端 bundle 增大；1.5 天工作量最大

### 实施要点（如选方案 A）
- PDFBox：`PDDocument.load()` → `PDFTextStripper().getText()`
- POI：`XWPFDocument` 遍历段落提取文本
- 文件大小限制 5MB，格式校验 `.pdf` / `.docx`（需求 4.4）

---

## D2：AI HTTP 客户端

### 背景
当前使用 `RestTemplate`（同步阻塞）调用 DeepSeek，[AppConfig.java](file:///d:/HuaweiMoveData/Users/20403/Desktop/软件实训项目/软件实训项目-code/软件实训项目-第一版/backend/src/main/java/com/xuelian/career/config/AppConfig.java) 已配置超时。

### 方案对比

| 维度 | 方案 A：保留 RestTemplate | 方案 B：Spring WebClient（响应式） | 方案 C：LangChain4j |
|------|--------------------------|-----------------------------------|---------------------|
| **依赖** | 零新增 | spring-boot-starter-webflux（新增） | langchain4j-core + langchain4j-deepseek（新增） |
| **编程模型** | 同步阻塞 | 响应式 Mono/Flux | 同步 + 流式抽象 |
| **流式支持** | ❌ 不支持 SSE | ✅ 原生支持 | ✅ 内置 streaming |
| **学习成本** | 0（已用） | 中（响应式编程） | 中（新框架） |
| **代码改动** | 小 | 大（Service 层全改） | 中（重写 DeepSeekService） |
| **生态成熟度** | Spring 原生，稳定 | Spring 原生，稳定 | 0.x 版本，迭代快 |
| **社区文档** | 极丰富 | 丰富 | 中文文档少 |

### 各方案优缺点

**方案 A：保留 RestTemplate**
- ✅ 零新增依赖，已用；Spring 原生稳定；社区文档极丰富；代码改动小
- ❌ 不支持 SSE 流式输出；同步阻塞，线程占用高

**方案 B：Spring WebClient**
- ✅ 原生支持 SSE 流式；响应式非阻塞，并发性能好；Spring 原生稳定
- ❌ 需新增 webflux 依赖；响应式编程学习成本中等；Service 层需重写

**方案 C：LangChain4j**
- ✅ 内置流式和 AI 抽象（Prompt/Memory/Tool）；屏蔽底层 API 差异
- ❌ 新框架学习成本；0.x 版本迭代快，API 可能不稳；中文文档少；耦合框架风险

### 实施要点（如选方案 A + 流式用 WebClient）
- 保留 RestTemplate 用于同步调用（简历优化、企业推荐、差距分析等）
- 新增 `WebClient.Builder` Bean，仅在 `DeepSeekService.callAPIStream()` 中使用
- Controller 用 `SseEmitter` 或 `Flux<ServerSentEvent>` 返回

---

## D3：流式输出方式

### 背景
当前所有 AI 接口同步阻塞，用户等待 15-20 秒。需求 4.1 要求 AI 接口 15-20 秒内返回，体验差。

### 方案对比

| 维度 | 方案 A：SSE（Server-Sent Events） | 方案 B：WebSocket | 方案 C：轮询 |
|------|-----------------------------------|-------------------|--------------|
| **协议** | HTTP 长连接 | 双向全双工 | HTTP 短连接多次 |
| **方向** | 服务端 → 客户端单向 | 双向 | 客户端轮询 |
| **复杂度** | 低 | 中（需维护连接） | 低但低效 |
| **浏览器支持** | 原生 EventSource | 需 WS 库 | 原生 fetch |
| **Spring 支持** | SseEmitter / Flux<ServerSentEvent> | @MessageMapping | 普通 Controller |
| **适用场景** | AI 文本流式输出 | 实时聊天、游戏 | 简单状态查询 |
| **DeepSeek 支持** | ✅ stream=true 参数 | 需基于 SSE 封装 | 不适用 |

### 推荐：方案 A（SSE）

**理由**：
1. AI 文本生成是典型的「服务端单向推送」场景，SSE 完美匹配
2. DeepSeek API 原生支持 `stream=true`，直接对接
3. Spring Boot 3 用 `Flux<ServerSentEvent>` 实现极简
4. 前端用原生 `EventSource` 或 `fetch + ReadableStream`，无需额外库

**实施要点**：
- 后端：`DeepSeekService.callAPIStream()` 返回 `Flux<String>`
- Controller：`@GetMapping(value="/stream", produces=MediaType.TEXT_EVENT_STREAM_VALUE)`
- 前端：`const es = new EventSource('/api/.../stream')`，`es.onmessage` 拼接文本
- **优先改造**：智能客服、AI 职业探索（用户感知最明显）
- **暂不改造**：简历优化、企业推荐（结果是结构化 JSON，流式意义不大）

---

## D4：AI 重试与限流

### 背景
当前 [DeepSeekServiceImpl.java](file:///d:/HuaweiMoveData/Users/20403/Desktop/软件实训项目/软件实训项目-code/软件实训项目-第一版/backend/src/main/java/com/xuelian/career/service/impl/DeepSeekServiceImpl.java) 重试固定 1 次、退避 1 秒，无指数退避，无限流。

### 方案对比

| 维度 | 方案 A：Resilience4j | 方案 B：Spring Retry | 方案 C：手写增强 |
|------|----------------------|---------------------|-----------------|
| **依赖** | resilience4j-spring-boot3（~2MB） | spring-retry + spring-aop（已有） | 零新增 |
| **重试** | Retry（指数退避+随机抖动） | @Retryable（指数退避） | 需自己实现 |
| **限流** | RateLimiter（令牌桶） | ❌ 无 | 需自己实现 |
| **熔断** | CircuitBreaker | ❌ 无 | 需自己实现 |
| **超时** | TimeLimiter | ❌ 无 | 需自己实现 |
| **配置方式** | application.yml 声明式 | 注解 | 硬编码 |
| **学习成本** | 低-中 | 低 | 低 |
| **Spring Boot 3 适配** | ✅ 官方支持 | ✅ | ✅ |

### 推荐：方案 A（Resilience4j）

**理由**：
1. **一站式解决重试 + 限流 + 熔断**，是 Spring Boot 3 生态推荐方案
2. 声明式配置，改动小：`@RateLimiter(name="deepseek")` + `@Retry(name="deepseek")`
3. 熔断器可在 DeepSeek 故障时快速失败，避免线程堆积（当前痛点）
4. 项目评分报告 V1.1 已明确标注「P1 AI 调用重试+降级机制」待补

**实施要点**：
```yaml
resilience4j:
  retry:
    instances:
      deepseek:
        max-attempts: 3
        wait-duration: 1s
        exponential-backoff-multiplier: 2
  ratelimiter:
    instances:
      deepseek:
        limit-for-period: 5      # 每秒 5 次
        limit-refresh-period: 1s
  circuitbreaker:
    instances:
      deepseek:
        failure-rate-threshold: 50
        wait-duration-in-open-state: 30s
```

---

## D5：向量检索 / RAG

### 背景
当前采用「MySQL 结构化数据 + 关键词 contains 匹配 + DeepSeek 总结」的轻量 RAG。需求附录 9.3 建议后续引入向量数据库。

### 方案对比

| 维度 | 方案 A：关键词+规则（现状） | 方案 B：PGVector（PostgreSQL 扩展） | 方案 C：Redis Stack（RediSearch） | 方案 D：Milvus 独立部署 |
|------|----------------------------|-------------------------------------|-----------------------------------|-------------------------|
| **依赖** | 零新增 | 需换 PostgreSQL 或加扩展 | 需 Redis Stack 镜像 | 新增 Milvus 容器 |
| **部署复杂度** | 极低 | 中（迁移 DB） | 低（换镜像） | 高（独立集群） |
| **向量检索** | ❌ 无 | ✅ HNSW/IVFFlat | ✅ HNSW | ✅ 专业 |
| **SQL 兼容** | — | ✅ SQL 查询 | ❌ RediSearch 语法 | ❌ SDK |
| **Embedding 生成** | — | 需调 Embedding API | 需调 Embedding API | 需调 Embedding API |
| **运维成本** | 0 | 中 | 低 | 高 |
| **30 天可行性** | ✅ 已完成 | ❌ 风险高 | ⚠️ 可行但紧张 | ❌ 不现实 |

### 各方案优缺点

**方案 A：关键词+规则（现状）**
- ✅ 零新增依赖；已能演示核心闭环；符合 MVP 定位；DeepSeek 本身具备语义理解，岗位 JD 塞 Prompt 可达「准 RAG」效果
- ❌ 无语义匹配；关键词 contains 精度低；无法处理同义词/近义词

**方案 B：PGVector（PostgreSQL 扩展）**
- ✅ SQL 兼容，HNSW/IVFFlat 向量检索；可同时获得 pgvector + 更强全文检索
- ❌ 需迁移 MySQL→PostgreSQL，风险高；30 天内不现实；运维成本中等

**方案 C：Redis Stack（RediSearch）**
- ✅ 复用 Redis 部署；HNSW 向量检索；换镜像即可
- ❌ 需 Redis Stack 镜像；RediSearch 语法学习成本；30 天紧张

**方案 D：Milvus 独立部署**
- ✅ 专业向量库；检索性能强；支持大规模
- ❌ 独立容器集群；运维成本高；30 天完全不现实

### 当前可做的轻量优化（不引入新依赖，如选方案 A）
- 客服 FAQ 匹配：用 `LIKE` + 关键词权重打分替代 `contains`
- 岗位匹配：在 Prompt 中拼接 Top-N 岗位 JD（已有实现）
- 简历优化：将目标岗位 JD 全文塞入 Prompt（已有实现）

### ✅ 选定方案：B. PGVector（MySQL 关键词过滤 + PG 向量语义重排）

**选定理由**（修订原"30 天不现实"评估）：
1. **不迁移 MySQL**：MySQL 继续作为业务主库，**新增 PostgreSQL 实例**（仅承担向量检索），双库共存，避免全量迁移风险
2. **混合检索策略**：MySQL 关键词过滤（快速召回）+ PG 向量语义重排（精度提升），兼顾速度与质量
3. **规避其他方案运维负担**：相比 Redis Stack 高内存开销、Milvus 独立集群运维，PGVector 仅需一个 PG 容器
4. **小团队友好**：SQL 兼容，学习成本低；pgvector 扩展成熟，社区活跃

#### 实施架构（双库共存 + 混合检索）

```
┌──────────────────────────────────────────────────────────────┐
│                       应用层 (Spring Boot)                     │
├──────────────────────────────────────────────────────────────┤
│  MySQL（业务主库，存量不动）           PostgreSQL（向量检索库）  │
│  - user / resume / position           - embedding_store 表    │
│  - project / faq / skill              - 字段：id, biz_type,    │
│  - recommendation_record                biz_id, content,        │
│  - 关键词 LIKE/contains 过滤            embedding(vector)       │
│                                        - HNSW 索引             │
└──────────────────────────────────────────────────────────────┘
```

#### 检索流程（两阶段混合检索）

```java
/**
 * 混合检索：MySQL 关键词过滤召回 + PG 向量语义重排
 * 应用场景：企业候选人推荐、客服 FAQ 匹配、岗位推荐
 */
public List<Candidate> hybridSearch(String query, String bizType, int topN) {
    // 阶段 1：MySQL 关键词过滤，召回 Top 50 候选（快速粗筛）
    List<Long> candidateIds = mysqlMapper.selectByKeywords(query, bizType, 50);
    if (candidateIds.isEmpty()) return Collections.emptyList();
    
    // 阶段 2：PG 向量语义重排，从 50 中精选 Top N（精度提升）
    float[] queryVector = embeddingService.embed(query);  // 调 Embedding API
    List<VectorScore> scores = pgVectorMapper.rerank(candidateIds, queryVector, topN);
    
    // 阶段 3：按重排分数返回结果
    return mysqlMapper.selectByIds(scores.stream().map(VectorScore::getBizId).toList());
}
```

#### 依赖与部署

```xml
<!-- pom.xml 新增：PostgreSQL 驱动（pgvector 通过扩展安装，无独立 SDK） -->
<dependency>
    <groupId>org.postgresql</groupId>
    <artifactId>postgresql</artifactId>
    <scope>runtime</scope>
</dependency>
```

```yaml
# application.yml 新增第二数据源
spring:
  datasource:
    mysql:        # 主库（业务数据）
      url: jdbc:mysql://mysql:3306/career_platform
      ...
    postgresql:   # 向量库（仅检索）
      url: jdbc:postgresql://postgres:5432/vector_db
      ...
```

```sql
-- PostgreSQL 初始化（docker-compose 启动时自动执行）
CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE embedding_store (
    id          BIGINT PRIMARY KEY,
    biz_type    VARCHAR(32) NOT NULL,    -- RESUME / POSITION / FAQ
    biz_id      BIGINT NOT NULL,         -- 对应 MySQL 业务表 ID
    content     TEXT NOT NULL,           -- 原文（便于回显）
    embedding   vector(1536) NOT NULL,   -- OpenAI text-embedding-3-small 维度
    created_at  TIMESTAMPTZ DEFAULT NOW()
);

-- HNSW 索引（pgvector 推荐，查询性能优于 IVFFlat）
CREATE INDEX ON embedding_store USING hnsw (embedding vector_cosine_ops)
    WITH (m = 16, ef_construction = 64);
```

#### Embedding 生成策略

| 场景 | Embedding 来源 | 触发时机 |
|------|----------------|----------|
| 简历入库 | DeepSeek/硅基流动 Embedding API | 简历上传解析后异步生成 |
| 岗位发布 | 同上 | 企业发布项目需求时异步生成 |
| FAQ 维护 | 同上 | 管理员编辑 FAQ 时同步生成 |
| 用户查询 | 同上 | 实时生成（单次 API 调用，~100ms） |

#### 双库数据同步（异步）

```java
/**
 * 简历保存后异步同步向量到 PG
 * 通过 Spring 事件机制解耦，主流程不阻塞
 */
@Async
@EventListener
public void onResumeSaved(ResumeSavedEvent event) {
    Long resumeId = event.getResumeId();
    String content = resumeMapper.selectTextById(resumeId);
    float[] vector = embeddingService.embed(content);
    pgVectorMapper.upsertEmbedding(resumeId, "RESUME", content, vector);
}
```

#### 适用场景与不适用场景

| 场景 | 是否启用 PGVector | 理由 |
|------|-------------------|------|
| 企业候选人推荐（R-010） | ✅ 启用 | 关键词召回 + 向量重排，显著提升匹配精度 |
| 客服 FAQ 匹配（R-014） | ✅ 启用 | 同义词/近义词匹配，提升 FAQ 命中率 |
| 岗位推荐（R-004 职业探索） | ⚠️ 第二阶段 | 先用 DeepSeek 总结，量大后再启用 |
| 简历优化（R-008） | ❌ 不启用 | 单简历 vs 单 JD，无需向量检索 |

#### 风险与缓解

| 风险 | 缓解措施 |
|------|----------|
| 双库数据不一致 | 异步事件 + 定时补偿任务（每小时扫描最近 1 小时变更） |
| Embedding API 故障 | 降级为纯 MySQL 关键词检索，记录降级日志 |
| PG 容器资源占用 | 单独限制内存 512MB，向量库仅存必要字段 |
| 30 天内集成风险 | 列为第二阶段任务，第一阶段先纯 MySQL 关键词 |

---

## D6：多模型兜底

### 背景
当前仅依赖 DeepSeek，API 不可用时降级到规则/随机数兜底。

### 方案对比

| 维度 | 方案 A：DeepSeek 单一 + 规则兜底（现状） | 方案 B：DeepSeek + 通义千问双提供商 | 方案 C：统一通过 OpenAI 兼容协议 |
|------|------------------------------------------|-------------------------------------|-------------------------------|
| **依赖** | 零新增 | 需新增通义 SDK 或兼容客户端 | 零新增（DeepSeek 兼容 OpenAI 协议） |
| **可用性** | 中（DeepSeek 故障即降级） | 高（双提供商互备） | 高 |
| **成本** | 单一计费 | 双份计费 | 单一计费 |
| **代码改动** | 0 | 中（抽象 ModelProvider 接口） | 小（改 apiUrl） |
| **Key 管理** | 1 个 | 2 个 | 1 个 |
| **30 天可行性** | ✅ | ⚠️ 可行但增加复杂度 | ✅ |

### 各方案优缺点

**方案 A：DeepSeek 单一 + 规则兜底（现状）**
- ✅ 零新增依赖；零代码改动；DeepSeek 兼容 OpenAI 协议，未来切换模型只需改 apiUrl/apiKey；规则兜底已满足需求 4.4
- ❌ DeepSeek 故障即降级到规则，无 AI 备用；可用性中等

**方案 B：DeepSeek + 通义千问双提供商**
- ✅ 双提供商互备，可用性高；AI 能力不中断
- ❌ 双份计费；需抽象 ModelProvider 接口，增加复杂度和测试面；需管理 2 个 Key

**方案 C：统一通过 OpenAI 兼容协议**
- ✅ 零新增依赖（DeepSeek 兼容 OpenAI 协议）；切换模型仅需改 apiUrl；单一计费
- ❌ 仍单一提供商，DeepSeek 故障时切换的也是兼容协议的其他模型，需配置备用 Key

### 实施要点（如选方案 A + 配置预留 backup）
- `DeepSeekConfig` 增加 `backupApiUrl` / `backupApiKey` / `backupModel`
- `DeepSeekServiceImpl.callAPI()` 主 URL 熔断后，自动切换 backup 配置重试一次
- 不抽象 `ModelProvider` 接口，避免过度设计

### ✅ 选定方案：A. DeepSeek 单一 + 规则兜底（OpenAI 兼容协议统一封装预留扩展）

**选定理由**：
1. **短期最小化对接与测试工作量**：3 人 30 天周期，单一模型 + 规则兜底已满足需求 4.4
2. **基于 OpenAI 兼容协议统一封装请求层**：DeepSeek 兼容 OpenAI Chat Completions 协议，封装后未来新增第二厂商仅需改配置，无需改业务代码
3. **分阶段提升可用性**：第一阶段（30 天内）单一 DeepSeek + 规则；第二阶段（演示后）按需接入第二厂商
4. **避免过度设计**：不抽象 `ModelProvider` 接口，仅通过配置预留扩展点

#### 配置层扩展（application.yml）

```yaml
deepseek:
  # 主模型（第一阶段启用）
  api-url: https://api.deepseek.com/v1/chat/completions
  api-key: ${DEEPSEEK_API_KEY}
  model: deepseek-chat
  
  # 备用模型（第一阶段不启用，第二阶段按需开启）
  backup:
    enabled: false                  # 默认关闭，第二阶段设为 true
    api-url: https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions
    api-key: ${BACKUP_API_KEY}
    model: qwen-turbo
  
  # Embedding 配置（用于 D5 PGVector）
  embedding:
    api-url: https://api.deepseek.com/v1/embeddings
    api-key: ${DEEPSEEK_API_KEY}
    model: text-embedding-v1
    dimensions: 1536
```

#### 请求层统一封装（DeepSeekServiceImpl 改造）

```java
/**
 * 统一请求构建（OpenAI 兼容协议格式）
 * 兼容 DeepSeek、通义千问、Moonshot、智谱等厂商
 */
private Map<String, Object> buildRequestBody(String systemPrompt, String userPrompt, 
                                              boolean stream, Double temperature, 
                                              Integer maxTokens, boolean jsonMode) {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("model", deepSeekConfig.getModel());
    body.put("stream", stream);
    if (temperature != null) body.put("temperature", temperature);
    if (maxTokens != null) body.put("max_tokens", maxTokens);
    if (jsonMode) body.put("response_format", Map.of("type", "json_object"));
    
    List<Map<String, String>> messages = new ArrayList<>();
    messages.add(Map.of("role", "system", "content", systemPrompt));
    messages.add(Map.of("role", "user", "content", userPrompt));
    body.put("messages", messages);
    return body;
}

/**
 * 主备切换调用（第二阶段启用 backup 后生效）
 * 主 URL 熔断后，自动切换 backup 配置重试一次
 */
public String callAPIWithFallback(String systemPrompt, String userPrompt) {
    try {
        return callAPI(deepSeekConfig.getApiUrl(), deepSeekConfig.getApiKey(), 
                       deepSeekConfig.getModel(), systemPrompt, userPrompt);
    } catch (CircuitBreakerOpenException e) {
        if (!deepSeekConfig.getBackup().isEnabled()) {
            throw new BusinessException("AI 服务暂时不可用，已触发熔断保护");
        }
        log.warn("主模型熔断，切换备用模型: {}", deepSeekConfig.getBackup().getModel());
        return callAPI(deepSeekConfig.getBackup().getApiUrl(), 
                       deepSeekConfig.getBackup().getApiKey(),
                       deepSeekConfig.getBackup().getModel(), 
                       systemPrompt, userPrompt);
    }
}
```

#### 扩展点设计（第二阶段按需启用）

| 扩展点 | 启用方式 | 影响范围 |
|--------|----------|----------|
| 新增第二厂商 | 配置 `backup.enabled=true` + 填入备用 Key | 仅 `DeepSeekServiceImpl`，业务层零改动 |
| 切换主模型 | 修改 `deepseek.model` 配置项 | 仅配置，零代码改动 |
| 启用 Embedding | 配置 `embedding` 段 | 用于 D5 PGVector，独立于 Chat |
| 流式输出 | 调用 `callAPIStream()` 而非 `callAPI()` | 仅 SSE 接口，业务层零改动 |

#### 分阶段演进路线

| 阶段 | 时间 | 模型策略 | 备用策略 |
|------|------|----------|----------|
| 第一阶段（30 天内） | MVP 交付 | DeepSeek 单一 | 规则兜底（已满足需求 4.4） |
| 第二阶段（演示后） | 演示反馈迭代 | DeepSeek 主 + 通义千问备 | 主熔断自动切备 |
| 第三阶段（长期） | 视成本与稳定性 | 按场景路由（解析类用低温度模型，对话类用高温度模型） | 多模型负载均衡 |

---

## D7：Prompt 模板管理（分层混合方案）

### 背景
当前用 [PromptTemplateUtil.java](file:///d:/HuaweiMoveData/Users/20403/Desktop/软件实训项目/软件实训项目-code/软件实训项目-第一版/backend/src/main/java/com/xuelian/career/util/PromptTemplateUtil.java) 从 classpath 文件加载，`{{placeholder}}` 替换，ConcurrentHashMap 缓存。共 5 个 .txt 模板。

### 选定方案：分层混合（基础文件 + 业务 DB + 增量覆盖）

将 Prompt 模板按「稳定性」分两层管理，**最小化迁移改造工作量**：

| 层级 | 存储位置 | 内容 | 修改方式 | 热更新 |
|------|----------|------|----------|--------|
| **基础层** | classpath 文件（`prompts/*.txt`） | 通用角色定义、系统级 Prompt（如 `"你是一位资深的职业规划导师"`、`"你是一位资深技术面试官"`） | 走发布流程（Git + 重启） | ❌ |
| **业务层** | DB `prompt_template` 表 | 频繁迭代的业务模板（如 `career_exploration.txt`、`customer_service.txt` 的完整内容） | 管理端后台编辑 | ✅ |

**关键设计：DB 仅存增量覆盖内容，复用文件基准文本**
- DB 表中 `content` 字段存储的是「完整覆盖版本」而非 diff，但仅对**需要修改的模板**才入库
- 未入库的模板（如 `project_parse.txt`、`resume_optimize.txt`、`mock_interview.txt`）继续走文件，零迁移
- 这样 DB 初始可为空，**无需编写数据导入脚本**，改造工作量最小

### 方案对比（保留原对比供参考）

| 维度 | 方案 A：文件 + DB 版本表 | 方案 B：纯文件（现状） | 方案 C：分层混合（已选） | 方案 D：配置中心（Nacos） |
|------|--------------------------|------------------------|--------------------------|---------------------------|
| **热更新** | ✅ 全部模板 | ❌ | ✅ 仅业务模板 | ✅ |
| **版本管理** | ✅ 全部 | ❌ | ✅ 仅业务模板 | ✅ |
| **迁移成本** | 中（需导入全部模板） | 0 | **低（DB 初始为空）** | 高 |
| **DB 存储压力** | 中（全量内容） | 0 | **低（仅增量）** | 0 |
| **基础模板稳定性** | ⚠️ 可能被误改 | ✅ | ✅ 走发布流程 | ⚠️ |
| **30 天可行性** | ✅ | ✅ | ✅ | ❌ |

### 选定理由

1. **基础模板（角色定义）稳定**：`"你是一位资深技术面试官"` 这类 system prompt 极少修改，走发布流程反而更安全，避免管理端误改导致全站 AI 行为漂移
2. **业务模板迭代频繁**：客服话术、职业探索推荐逻辑需要根据演示反馈快速调整，DB 存储支持热更新
3. **最小化迁移**：DB 初始为空，无需写脚本导入现有 5 个模板；需要调整哪个模板，管理员在后台录入该模板的覆盖版本即可
4. **DB 存储压力小**：仅业务模板入库，且只存当前激活版本，不存历史版本（历史版本靠 Git 管理）

### 实施要点

#### 1. DB 表结构
```sql
CREATE TABLE prompt_template (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    name        VARCHAR(64)  NOT NULL COMMENT '模板名（对应文件名，如 career_exploration）',
    content     TEXT         NOT NULL COMMENT '模板完整内容（覆盖文件版本）',
    description VARCHAR(255) COMMENT '模板描述/修改说明',
    is_active   TINYINT      NOT NULL DEFAULT 1 COMMENT '1=激活 0=停用',
    updated_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_name_active (name, is_active)
);
```

#### 2. PromptTemplateUtil 改造逻辑
```
loadTemplate(name):
    1. 查 DB: SELECT content FROM prompt_template WHERE name=? AND is_active=1
    2. 若 DB 命中 → 返回 DB 内容（业务层覆盖）
    3. 若 DB 未命中 → 回退 classpath:prompts/{name}.txt（基础层）
    4. 结果存入 ConcurrentHashMap 缓存
```

#### 3. 热更新接口
- `POST /api/admin/prompt/refresh` — 清空 ConcurrentHashMap 缓存，下次调用重新查 DB
- `GET /api/admin/prompt/list` — 列出 DB 中已覆盖的模板
- `POST /api/admin/prompt/save` — 新增/更新业务模板覆盖内容
- `DELETE /api/admin/prompt/{name}` — 删除 DB 覆盖，回退到文件版本

#### 4. 管理端页面（简易）
- 仅管理「业务类模板」，基础模板不在此页面暴露
- 列表展示：模板名、描述、最后更新时间、是否激活
- 编辑器：纯文本 textarea（无需 Markdown 编辑器，Prompt 本身是纯文本）
- 操作：保存、激活/停用、恢复文件默认版本（删除 DB 记录）

#### 5. 分层归属清单

| 模板文件 | 归属层 | 是否可后台编辑 |
|----------|--------|----------------|
| `career_exploration.txt` | 业务层 | ✅ |
| `customer_service.txt` | 业务层 | ✅ |
| `resume_optimize.txt` | 基础层 | ❌（走发布流程） |
| `mock_interview.txt` | 基础层 | ❌（走发布流程） |
| `project_parse.txt` | 基础层 | ❌（走发布流程） |
| 新增 `gap_analysis.txt`（P1 计划） | 业务层 | ✅ |

> 说明：客服和职业探索的推荐逻辑、话术风格在演示前可能需要根据反馈快速调整，故归业务层；简历优化、模拟面试、项目解析的 Prompt 结构稳定，归基础层。后续如需调整归属，仅需在 DB 中新增覆盖记录即可，无需改代码。

---

## 综合推荐方案总览

### 全部已确认决策（D1-D7）

| 决策 | 选定方案 | 新增依赖 | 工作量 |
|------|----------|----------|--------|
| D1 简历提取 | A. POI+PDFBox（复用现有依赖） | 无（pom.xml 已有） | 0.5 天 |
| D2 HTTP 客户端 | A. RestTemplate（存量）+ WebClient（仅流式） | spring-boot-starter-webflux | 0（共存配置） |
| D3 流式输出 | A. SSE（仅客服+职业探索） | spring-boot-starter-webflux | 1.5 天 |
| D4 重试限流 | A. Resilience4j | resilience4j-spring-boot3 | 1 天 |
| D5 向量检索 | B. PGVector（MySQL 关键词过滤 + PG 向量语义重排） | postgresql 驱动 + pgvector 扩展 | 第二阶段 |
| D6 多模型兜底 | A. DeepSeek 单一+规则（OpenAI 兼容协议封装预留扩展） | 无 | 0（第一阶段） |
| D7 Prompt 管理 | C. 分层混合（基础文件+业务 DB+增量覆盖） | 无 | 1 天 |

### 新增依赖清单（最终）

```xml
<!-- pom.xml 新增 -->
<!-- Resilience4j：重试/限流/熔断（D4） -->
<dependency>
    <groupId>io.github.resilience4j</groupId>
    <artifactId>resilience4j-spring-boot3</artifactId>
    <version>2.2.0</version>
</dependency>

<!-- WebClient：仅用于 SSE 流式接口（D2/D3，客服+职业探索） -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-webflux</artifactId>
</dependency>

<!-- PostgreSQL 驱动：向量检索库（D5，第二阶段启用） -->
<dependency>
    <groupId>org.postgresql</groupId>
    <artifactId>postgresql</artifactId>
    <scope>runtime</scope>
</dependency>

<!-- D1 POI+PDFBox：pom.xml 已有，无需新增 -->
<!-- D6 DeepSeek 单一：无新增依赖，OpenAI 兼容协议封装在现有 DeepSeekServiceImpl 内 -->
<!-- D7 Prompt 分层混合：无新增依赖，复用 MyBatis-Plus -->
```

```json
// frontend/package.json 无需新增，原生 EventSource 即可
```

### docker-compose 新增服务（D5，第二阶段）

```yaml
# docker-compose.yml 新增 PostgreSQL 向量库
postgres-vector:
  image: pgvector/pgvector:pg16  # 内置 pgvector 扩展
  container_name: career-postgres-vector
  environment:
    POSTGRES_DB: vector_db
    POSTGRES_USER: career
    POSTGRES_PASSWORD: ${PG_PASSWORD}
  ports:
    - "5432:5432"
  volumes:
    - postgres_data:/var/lib/postgresql/data
    - ./init-pgvector.sql:/docker-entrypoint-initdb.d/init.sql  # 自动建表建索引
  mem_limit: 512m
  networks:
    - career-net
```

### 分阶段实施策略

| 阶段 | 时间 | 范围 | 目标 |
|------|------|------|------|
| **第一阶段** | 30 天内 | D1/D2/D3/D4/D6/D7 + D5 暂用 MySQL 关键词 | MVP 交付，核心闭环可演示 |
| **第二阶段** | 演示后迭代 | D5 PGVector 启用（R-010/R-014）+ D6 backup 启用 | 推荐精度提升，可用性提升 |
| **第三阶段** | 长期演进 | D5 全场景启用 + D6 多模型路由 | 生产级可用性 |

### 风险与缓解

| 风险 | 缓解措施 |
|------|----------|
| Resilience4j 配置不当导致误熔断 | 初始阈值宽松（失败率 50%、开放 30s），观察后调优 |
| WebClient 与 RestTemplate 共存冲突 | WebClient 仅在流式接口使用，不替换现有 RestTemplate |
| POI/PDFBox 解析大文件 OOM | 限制文件 5MB，流式读取，提取后立即释放文档对象 |
| SSE 在 Nginx 反向代理下被缓冲 | nginx.conf 加 `proxy_buffering off` + `X-Accel-Buffering: no` 响应头 |
| D5 双库数据不一致 | 异步事件 + 定时补偿任务（每小时扫描最近 1 小时变更） |
| D5 Embedding API 故障 | 降级为纯 MySQL 关键词检索，记录降级日志 |
| D5 PG 容器资源占用 | 单独限制内存 512MB，向量库仅存必要字段 |
| D6 主备切换数据一致性 | 备用模型仅用于查询类（解析、对话），写入类（评分记录）仍走主模型 |
| D7 分层混合：DB 覆盖内容与文件版本不一致 | 后台编辑页展示「文件基准版本」与「DB 覆盖版本」对比；提供「恢复文件默认」按钮一键删除 DB 记录 |
| D7 分层混合：缓存未及时刷新 | `prompt_template` 表变更后通过 `/api/admin/prompt/refresh` 主动清缓存；编辑保存接口内部自动触发清缓存 |
| D7 分层混合：业务/基础层归属误判 | 归属清单写入文档并在后台页面提示；仅业务层模板在后台可见可编辑 |

---

## 八、AI 功能全覆盖矩阵（学生端 + 企业端 + 公共）

### 8.1 需求中明确的 AI 功能清单

依据《需求规格说明书 V1.0》逐条核对，项目共 **6 个 AI 功能**（标题或处理逻辑明确涉及 DeepSeek/AI）：

| 端 | 需求编号 | 功能模块 | 需求章节 | AI 调用方式 |
|----|----------|----------|----------|-------------|
| 学生端 | R-004 | AI 职业方向探索 | 3.2.3 | DeepSeek 推荐岗位方向 |
| 学生端 | R-006 | AI 岗位能力差距分析 | 3.2.5 | 标题含 AI，需求描述为规则对比（附录 9.2 兜底也是规则） |
| 学生端 | R-008 | AI 简历智能优化 | 3.2.7 | DeepSeek 分析简历 |
| 学生端 | R-009 | AI 模拟面试 | 3.2.8 | DeepSeek 出题 + 追问 + 评估 |
| 企业端 | R-010 | 企业项目需求解析与候选人推荐 | 3.3.1 | DeepSeek 解析项目 |
| 公共 | R-014 | 智能客服文字问答 | 3.5 | FAQ + DeepSeek 兜底 |

> **说明**：R-005 岗位匹配（3.2.4 标题含"智能"）和 R-007 学习路径（3.2.6）需求明确为规则算法，不属于 AI 功能，但可选 AI 增强（见 8.3）。

### 8.2 AI 功能 × 技术选型 覆盖矩阵

| AI 功能 | 当前状态 | 涉及决策点 | 实现方案 | 工作量 | 优先级 |
|---------|----------|------------|----------|--------|--------|
| **R-004 职业探索**（学生） | ✅ 已接入 AI | D2、D3、D4、D7 | SSE 流式输出 + Resilience4j 重试 + 业务 Prompt 走 DB | 0.5 天（SSE 改造） | P2 |
| **R-006 差距分析**（学生） | ❌ 未接入 AI | D2、D4、D7 | 规则计算差距后，调 DeepSeek 生成个性化提升建议；新增 `gap_analysis.txt` 模板（业务层） | 1 天 | P1 |
| **R-008 简历优化**（学生） | ⚠️ AI 调用但输入无效 | D1、D2、D4、D7 | POI+PDFBox 提取文本 + 填入 `resume_text` 参数 + 文件大小格式校验 | 0.5 天 | **P0** |
| **R-009 模拟面试**（学生） | ⚠️ 缺追问逻辑 | D2、D3、D4、D7 | 实现 FOLLOW_UP 阶段调用，每题最多追问 2 轮；模板已就绪 | 1 天 | P1 |
| **R-010 企业推荐**（企业） | ❌ 完全未接入 AI | D2、D4、D7 | 注入 DeepSeekService，使用 `project_parse.txt` 解析项目→岗位→技能→候选人匹配 | 1.5 天 | **P0** |
| **R-014 智能客服**（公共） | ✅ 已接入 AI | D2、D3、D4、D7 | SSE 流式输出 + Resilience4j 重试 + 业务 Prompt 走 DB | 0.5 天（SSE 改造） | P2 |

### 8.3 可选 AI 增强（非需求强制，按余量推进）

| 功能 | 需求编号 | 当前实现 | AI 增强方案 | 优先级 |
|------|----------|----------|-------------|--------|
| 岗位匹配推荐 | R-005 | 规则加权评分（技能50%+测评30%+城市20%） | AI 生成岗位推荐理由（当前仅展示匹配分，无文字说明） | P3 |
| 个性化学习路径 | R-007 | 规则排序 + 资源匹配 | AI 生成个性化学习建议和资源推荐理由 | P3 |
| 基础数据看板 | R-013 | 统计展示 | 基于 `ai_call_log` 表新增 AI 调用监控（成功率、耗时、降级率） | P2 |

### 8.4 企业端 AI 功能专项检查

企业端是用户特别关注的端，其 AI 功能覆盖情况：

| 企业端功能 | 需求来源 | 当前实现 | 技术选型覆盖 | 完整度 |
|------------|----------|----------|--------------|--------|
| 项目需求解析 | 3.3.1 | ❌ 未调用 DeepSeek | D2（RestTemplate 调用）+ D4（Resilience4j 兜底）+ D7（`project_parse.txt` 基础层） | **P0 待实现** |
| 岗位建议 | 3.3.1 | ❌ 固定取前 3 个 | 由项目解析结果驱动 | **P0 待实现** |
| 技能标签推荐 | 3.3.1 | ❌ 硬编码 Java-熟练 | 由 `project_parse.txt` 模板输出 | **P0 待实现** |
| 候选人推荐 | 3.3.1 | ❌ 随机数评分 | AI 解析技能后，按技能标签检索候选人画像 + 规则评分 | **P0 待实现** |
| 推荐理由展示 | 3.3.1 | ❌ 固定文案 | AI 生成个性化推荐理由 | **P0 待实现** |
| 推荐历史 | 3.3.1 | ❌ getHistory 返回空 | 持久化到 `recommendation_record` 表 | **P0 待实现** |

**企业端结论**：R-010 是企业端唯一 AI 功能，但当前完全未实现 AI 调用，是 6 个 AI 功能中差距最大的，必须列为 P0 优先修复。技术选型已全覆盖（D2 调用方式 + D4 稳定性 + D7 模板管理）。

### 8.5 学生端 AI 功能专项检查

| 学生端 AI 功能 | 当前完整度 | 技术选型覆盖 | 待修复项 |
|----------------|------------|--------------|----------|
| R-004 职业探索 | ✅ 较好 | D3 SSE + D4 重试 + D7 Prompt | 缓存键优化（P2） |
| R-006 差距分析 | ⚠️ 未用 AI | D2 + D4 + D7（新增模板） | 接入 AI 生成建议（P1） |
| R-008 简历优化 | ❌ 缺陷 | D1 文本提取 + D4 重试 | 提取简历文本（P0） |
| R-009 模拟面试 | ⚠️ 缺追问 | D2 + D3（可选 SSE）+ D4 | 实现 FOLLOW_UP 追问（P1） |

**学生端结论**：4 个 AI 功能中，1 个较好、1 个有缺陷、1 个缺功能、1 个未接入。技术选型已全覆盖所有待修复项。

### 8.6 覆盖结论

| 检查项 | 结果 |
|--------|------|
| 需求中 6 个 AI 功能是否全部识别 | ✅ 全部识别 |
| 每个 AI 功能是否有对应技术选型方案 | ✅ 全部覆盖 |
| 学生端 AI 功能是否全覆盖 | ✅ 4/4（R-004、R-006、R-008、R-009） |
| 企业端 AI 功能是否全覆盖 | ✅ 1/1（R-010），但实现差距最大 |
| 公共 AI 功能是否全覆盖 | ✅ 1/1（R-014） |
| 通用基础设施是否覆盖所有 AI 调用 | ✅ D2/D4/D7 覆盖全部，D3 覆盖对话类 |
| 可选 AI 增强点是否识别 | ✅ R-005、R-007、R-013 标注为 P2/P3 |

**无遗漏**。下一步将基于此矩阵修订执行计划，按 P0→P1→P2 顺序推进实现。

---

## 九、与现有方案文档的关系

本文档是对 [AI功能实现梳理与完善方向.md](file:///d:/HuaweiMoveData/Users/20403/Desktop/软件实训项目/.trae/documents/AI功能实现梳理与完善方向.md) 中 P0/P1/P2 完善方向的技术选型细化。确定选型后，将据此修订执行计划。
