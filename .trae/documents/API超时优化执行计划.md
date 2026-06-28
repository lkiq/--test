# DeepSeek API 超时优化执行计划

> **文档定位**：本文档是 API 超时问题的**执行计划书**，基于排查报告 + 团队确认的「三步走」路线，明确每一步要改什么文件、改什么内容、如何验证。**本文档仅生成计划，不直接修改代码，待用户确认后执行。**
>
> **目标**：API 调用 95% 在 5 秒内返回，超时率从 ~70% 降到 ~5%。
>
> **关联文档**：本计划是 [AI功能最终技术栈与实施路线.md](file:///d:/HuaweiMoveData/Users/20403/Desktop/软件实训项目/.trae/documents/AI功能最终技术栈与实施路线.md) 中 P1 阶段「DeepSeek 基础服务改造」的细化执行方案。

---

## 一、问题根因回顾（3 个层面）

| # | 根因 | 现状代码 | 影响 |
|---|------|----------|------|
| 1 | 硬超时 3 秒太短，max_tokens 1024 生成需 5-8 秒 | [DeepSeekServiceImpl.java#L52](file:///d:/HuaweiMoveData/Users/20403/Desktop/软件实训项目/软件实训项目-code/backend/src/main/java/com/xuelian/career/service/impl/DeepSeekServiceImpl.java#L52) `3000L` + [L89](file:///d:/HuaweiMoveData/Users/20403/Desktop/软件实训项目/软件实训项目-code/backend/src/main/java/com/xuelian/career/service/impl/DeepSeekServiceImpl.java#L89) `max_tokens=1024` | 所有调用必然超时，全走兜底 |
| 2 | 每次 `new RestTemplate`，无连接池，TCP+TLS 重复握手 | [L99-L102](file:///d:/HuaweiMoveData/Users/20403/Desktop/软件实训项目/软件实训项目-code/backend/src/main/java/com/xuelian/career/service/impl/DeepSeekServiceImpl.java#L99-L102) | 每次多耗 300-800ms |
| 3 | CompletableFuture 用 ForkJoinPool，2 核服务器仅 1 线程 | [L97](file:///d:/HuaweiMoveData/Users/20403/Desktop/软件实训项目/软件实训项目-code/backend/src/main/java/com/xuelian/career/service/impl/DeepSeekServiceImpl.java#L97) | 并发调用排队，雪崩风险 |

---

## 二、两阶段实施计划（第一+二阶段合并）

> **合并依据**：第一阶段（改超时+改线程池）和第二阶段（换 JdkClientHttpRequestFactory）都属于基础设施层改动，合并执行可一次性压测最优网络 I/O 性能，避免分两次改动 DeepSeekServiceImpl 造成代码冲突。第三阶段（缓存与 Prompt）属业务逻辑层优化，作为独立迭代。

### 第一+二阶段合并：基础设施层改造（一个 Git 分支提交）

**目标**：超时率从 ~70% 降到 ~10%，每次调用省 300-800ms 握手开销。

#### 改动 1.1：放宽硬超时 + 精简 max_tokens

**文件**：[DeepSeekServiceImpl.java](file:///d:/HuaweiMoveData/Users/20403/Desktop/软件实训项目/软件实训项目-code/backend/src/main/java/com/xuelian/career/service/impl/DeepSeekServiceImpl.java)

**改动内容**：

```java
// L51-L53：默认超时 3000 → 5000
@Override
public String callAPI(String systemPrompt, String userPrompt) {
    return callAPI(systemPrompt, userPrompt, 5000L);  // 3秒 → 5秒
}

// L89：max_tokens 改为差异化配置（见下方调用方表）
requestBody.put("temperature", 0.6);
// max_tokens 由调用方通过新增重载方法传入，不再硬编码 1024
```

**差异化 max_tokens 与超时配置**（采纳建议：512 用于结构化输出，768 用于面试评价）：

| 场景 | max_tokens | 硬超时 | 理由 |
|------|------------|--------|------|
| 测评建议 | 512 | 5000ms | 结构化核心评价，512 足够 |
| 简历优化 | 512 | 5000ms | 结构化建议，512 足够 |
| 模拟面试评价 | **768** | 6000ms | 优点/不足/改进三维度，512 可能截断 JSON |
| 模拟面试出题 | 256 | 6000ms | 单道题，256 够用 |
| 职业探索 | 768 | 8000ms（走缓存） | 长文本推荐，768 平衡速度与完整度 |
| 智能客服 | 512 | 5000ms | 结构化回答，512 足够 |

**业务调用方改造**（需新增带 max_tokens 的重载方法）：

| 文件 | 行号 | 当前 | 改为 | 场景 |
|------|------|------|------|------|
| [AssessmentServiceImpl.java](file:///d:/HuaweiMoveData/Users/20403/Desktop/软件实训项目/软件实训项目-code/backend/src/main/java/com/xuelian/career/service/impl/AssessmentServiceImpl.java) | L259 | `callAPI(sys, prompt, 3000L)` | `callAPI(sys, prompt, 5000L, 512)` | 测评建议 |
| [CareerExplorationServiceImpl.java](file:///d:/HuaweiMoveData/Users/20403/Desktop/软件实训项目/软件实训项目-code/backend/src/main/java/com/xuelian/career/service/impl/CareerExplorationServiceImpl.java) | L61 | `callAPIWithCache(...)` | `callAPIWithCache(..., 8000L, 768)` | 职业探索 |
| [InterviewServiceImpl.java](file:///d:/HuaweiMoveData/Users/20403/Desktop/软件实训项目/软件实训项目-code/backend/src/main/java/com/xuelian/career/service/impl/InterviewServiceImpl.java) | L235 | `callAPI(sys, prompt)` | `callAPI(sys, prompt, 6000L, 256)` | 面试出题 |
| [InterviewServiceImpl.java](file:///d:/HuaweiMoveData/Users/20403/Desktop/软件实训项目/软件实训项目-code/backend/src/main/java/com/xuelian/career/service/impl/InterviewServiceImpl.java) | L277 | `callAPI(sys, prompt)` | `callAPI(sys, prompt, 6000L, 768)` | 面试评价 |
| [ResumeServiceImpl.java](file:///d:/HuaweiMoveData/Users/20403/Desktop/软件实训项目/软件实训项目-code/backend/src/main/java/com/xuelian/career/service/impl/ResumeServiceImpl.java) | L56 | `callAPI(sys, prompt)` | `callAPI(sys, prompt, 5000L, 512)` | 简历优化 |
| [CustomerServiceServiceImpl.java](file:///d:/HuaweiMoveData/Users/20403/Desktop/软件实训项目/软件实训项目-code/backend/src/main/java/com/xuelian/career/service/impl/CustomerServiceServiceImpl.java) | L98 | `callAPIWithCache(..., 1800L)` | `callAPIWithCache(..., 3600L, 512)` | 智能客服 |

#### 改动 1.2：隔离 AI 调用线程池（10 线程）

**文件**：[DeepSeekServiceImpl.java](file:///d:/HuaweiMoveData/Users/20403/Desktop/软件实训项目/软件实训项目-code/backend/src/main/java/com/xuelian/career/service/impl/DeepSeekServiceImpl.java)

**改动内容**：

```java
// 新增字段（类顶部，L40 附近）
// 10 线程：I/O 密集型甜点位，2 核服务器支持 2 QPS 持续吞吐
private final ExecutorService aiCallExecutor = Executors.newFixedThreadPool(10, r -> {
    Thread t = new Thread(r, "ai-call-worker");
    t.setDaemon(true);
    return t;
});

// L97：supplyAsync 传入独立线程池
CompletableFuture<String> future = CompletableFuture.supplyAsync(() -> {
    // 直接复用注入的连接池 RestTemplate（不再 new）
    ResponseEntity<Map> response = restTemplate.exchange(
            deepSeekConfig.getApiUrl(),
            HttpMethod.POST,
            entity,
            Map.class
    );
    return extractContent(response.getBody());
}, aiCallExecutor);  // ← 独立线程池 + 复用注入的 RestTemplate

// 新增 @PreDestroy 销毁方法（类底部）
@PreDestroy
public void shutdown() {
    aiCallExecutor.shutdown();
    try {
        if (!aiCallExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
            aiCallExecutor.shutdownNow();
        }
    } catch (InterruptedException e) {
        aiCallExecutor.shutdownNow();
        Thread.currentThread().interrupt();
    }
}
```

**关键改动**（合并第一阶段线程池 + 第二阶段连接池）：
- 新增 `aiCallExecutor`（10 线程，daemon）
- `CompletableFuture.supplyAsync` 传入 `aiCallExecutor`
- **删除** L99-L102 的 `new SimpleClientHttpRequestFactory` + `new RestTemplate`
- 改用类顶部已注入的 `private final RestTemplate restTemplate`（来自 AppConfig 的连接池 Bean）

#### 改动 1.3：AppConfig 改造 RestTemplate Bean（JdkClientHttpRequestFactory）

**文件**：[AppConfig.java](file:///d:/HuaweiMoveData/Users/20403/Desktop/软件实训项目/软件实训项目-code/backend/src/main/java/com/xuelian/career/config/AppConfig.java)

**改动内容**：

```java
package com.xuelian.career.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.concurrent.Executors;

/**
 * 通用配置 - REST 客户端等
 */
@Configuration
public class AppConfig {

    /**
     * RestTemplate Bean - 使用 Java 17 原生 HttpClient，自带连接复用
     * 全局唯一实例，业务层通过 @Autowired 注入，禁止 new
     */
    @Bean
    public RestTemplate restTemplate() {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3))      // 连接超时 3 秒
                .executor(Executors.newFixedThreadPool(10)) // 底层异步线程池
                .build();

        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(Duration.ofSeconds(6));      // 读取超时 6 秒（略大于业务 5 秒硬超时）

        return new RestTemplate(factory);
    }
}
```

#### 第一+二阶段验证方式

```bash
# 1. 启动后端，连续触发 10 次简历优化
# 2. 查看日志，确认 duration 在 5000ms 内
# 3. 查 ai_call_log 表：
SELECT status, COUNT(*), AVG(duration_ms) 
FROM ai_call_log 
WHERE created_at > NOW() - INTERVAL 10 MINUTE 
GROUP BY status;
# 预期：SUCCESS 占比 > 85%，FAILED（超时）< 15%
# 4. 压测：ab -n 20 -c 5 http://localhost:8080/api/resume/analyze
#    预期：95% 请求 5 秒内返回
# 5. 对比改造前，确认每次调用 duration 减少 300-800ms（连接复用生效）
```

#### 第一+二阶段风险

| 风险 | 缓解 |
|------|------|
| max_tokens 512 导致面试评价 JSON 截断 | 面试评价单独用 768，其余结构化场景 512 够用 |
| 线程池 10 个线程不够 | 2 核服务器 + 5 秒超时，10 线程支持 2 QPS 持续吞吐，I/O 密集型甜点位 |
| JdkClientHttpRequestFactory 在 Spring 6.1+ 才有 | 项目 Spring Boot 3.2.5 对应 Spring 6.1，✅ 支持 |
| HttpClient 默认无 keep-alive 缓存 | DeepSeek API 响应头已支持 keep-alive，HttpClient 自动复用 |
| readTimeout 6 秒与硬超时 5 秒冲突 | 不冲突，硬超时是兜底，HttpClient readTimeout 是传输层超时 |

---

### 第三阶段：缓存防御与 Prompt 瘦身（长期稳定）

**目标**：减少真实 AI 调用次数，缓存命中率提升到 40%+，进一步降低超时风险。

#### 改动 3.1：延长缓存 TTL

**文件**：[DeepSeekServiceImpl.java](file:///d:/HuaweiMoveData/Users/20403/Desktop/软件实训项目/软件实训项目-code/backend/src/main/java/com/xuelian/career/service/impl/DeepSeekServiceImpl.java) + 业务调用方

**改动内容**：

```java
// DeepSeekConfig.java：默认 cacheTtl 从 3600 → 21600（6 小时）
private long cacheTtl = 21600;

// CustomerServiceServiceImpl.java L98：客服 TTL 1800 → 3600（1 小时）
String response = deepSeekService.callAPIWithCache(cacheKey, systemPrompt, prompt, 3600L);

// CareerExplorationServiceImpl.java L61：职业探索走默认 TTL（6 小时）
String response = deepSeekService.callAPIWithCache(cacheKey, systemPrompt, prompt, null);
```

#### 改动 3.2：Prompt 瘦身工具类（采纳建议：独立工具类，符合 OCP）

**新建文件**：`backend/src/main/java/com/xuelian/career/util/PromptOptimizer.java`

**采纳理由**：在各 Service 内直接截断会导致代码严重耦合，且后期极难维护。提取为独立工具类虽然目前是简单的 `String.contains` 逻辑，但在 30 天敏捷周期内是性价比最高、最符合开闭原则（OCP）的做法。

```java
package com.xuelian.career.util;

/**
 * Prompt 瘦身工具类
 * 截断过长输入，降低首 token 延迟
 */
public class PromptOptimizer {

    /** 最大输入字符数（约 1000 字，对应 ~1500 token） */
    private static final int MAX_INPUT_LENGTH = 1000;

    /**
     * 截断输入到最大长度，保留关键段落
     */
    public static String truncate(String input) {
        return truncate(input, MAX_INPUT_LENGTH);
    }

    /**
     * 截断输入到指定长度
     */
    public static String truncate(String input, int maxLength) {
        if (input == null || input.length() <= maxLength) return input;
        return input.substring(0, maxLength) + "\n[内容已截断]";
    }

    /**
     * 提取简历关键段落（技能 + 经历），不传个人信息
     */
    public static String extractResumeKeySections(String resumeText) {
        // 简单实现：按段落分割，保留包含"技能""经历""项目""经验"关键词的段落
        String[] lines = resumeText.split("\n");
        StringBuilder sb = new StringBuilder();
        for (String line : lines) {
            if (line.contains("技能") || line.contains("经历") || 
                line.contains("项目") || line.contains("经验") ||
                line.contains("教育")) {
                sb.append(line).append("\n");
            }
        }
        return sb.length() > 0 ? sb.toString() : truncate(resumeText);
    }
}
```

**业务调用方改造**：

```java
// ResumeServiceImpl.java L56 区域
String optimizedPrompt = PromptOptimizer.extractResumeKeySections(prompt);
String response = deepSeekService.callAPI("你是一位简历优化专家", optimizedPrompt, 5000L);

// InterviewServiceImpl.java L235 区域
String optimizedPrompt = PromptOptimizer.truncate(prompt);
String response = deepSeekService.callAPI("你是一位资深技术面试官", optimizedPrompt, 6000L);
```

#### 第三阶段验证方式

```bash
# 1. 触发同一用户的简历优化 2 次，第 2 次应命中缓存（日志出现"AI 缓存命中"）
# 2. 查 ai_call_log：
SELECT response_source, COUNT(*) 
FROM ai_call_log 
WHERE created_at > NOW() - INTERVAL 1 HOUR 
GROUP BY response_source;
# 预期：CACHE 占比 > 30%
# 3. Prompt 长度对比：优化前后 log.info 的 promptLength 应明显减少
```

---

## 三、进阶：SSE 流式输出（UI 终极优化，可选）

**目标**：用户首字响应 1-2 秒，心理等待时间大幅缩短。**总耗时不变，但体验质变**。

**前置条件**：第一、二阶段完成，且时间充裕（对应最终技术栈文档 P2 阶段）。

**覆盖范围**：仅智能客服、职业探索 2 个场景（结构化 JSON 场景不做流式）。

**改动清单**（详见 [AI功能最终技术栈与实施路线.md](file:///d:/HuaweiMoveData/Users/20403/Desktop/软件实训项目/.trae/documents/AI功能最终技术栈与实施路线.md) 决策 7）：

| 文件 | 改动 |
|------|------|
| pom.xml | 新增 `spring-boot-starter-webflux` |
| DeepSeekService.java | 新增 `Flux<String> callAPIStream(String, String)` 方法 |
| DeepSeekServiceImpl.java | 实现 `callAPIStream`，用 WebClient `stream=true` |
| AppConfig.java | 新增 `WebClient.Builder` Bean |
| CustomerServiceController.java | 新增 `/chat/stream` SSE 端点 |
| CareerExplorationController.java | 新增 `/explore/stream` SSE 端点 |
| 前端 CustomerServiceView.vue | `fetch + ReadableStream` 接收 |
| 前端 CareerExplorationView.vue | `fetch + ReadableStream` 接收 |
| nginx.conf | `proxy_buffering off` |

**本阶段不在本次优化计划执行范围内，仅作规划记录。**

---

## 四、执行计划总览

| 阶段 | 任务 | 改动文件数 | 改动量 | 预期效果 | 是否本次执行 |
|------|------|------------|--------|----------|--------------|
| **第一+二阶段合并** | 放宽超时 + 差异化 max_tokens + 独立线程池 + JdkClientHttpRequestFactory 连接池 | 7 | ~50 行 | 超时率 70%→10% | ✅ 执行 |
| **第三阶段** | 延长 TTL + PromptOptimizer 瘦身 | 4 + 1 新建 | ~40 行 | 缓存命中 40%+ | ✅ 执行 |
| **进阶** | SSE 流式输出 | 8 | ~150 行 | 首字 1-2 秒 | ❌ 本次不做 |

### 合并后总改动

- **第一+二阶段**：约 7 个文件（DeepSeekService 接口 + DeepSeekServiceImpl + AppConfig + 4 个业务调用方）
- **第三阶段**：4 个文件 + 1 个新建（PromptOptimizer）
- **新增依赖**：0（JdkClientHttpRequestFactory 是 Spring 6.1 原生）
- **预期总效果**：API 调用 95% 在 5 秒内返回，超时率 < 5%

---

## 五、执行顺序与回滚策略

### 执行顺序

```
第一+二阶段（基础设施层，一个 Git 分支提交）→ 验证 → 第三阶段（业务逻辑层，独立迭代）→ 验证
```

第一+二阶段合并提交，验证通过后进入第三阶段。每阶段独立验证，确认效果后再进入下一阶段。

### 回滚策略

| 阶段 | 回滚方式 |
|------|----------|
| 第一+二阶段 | Git revert 单个 commit，恢复 3000ms + max_tokens 1024 + ForkJoinPool + SimpleClientHttpRequestFactory |
| 第三阶段 | Git revert，恢复原 TTL + 删除 PromptOptimizer |

**建议**：第一+二阶段合并为 1 个 commit，第三阶段单独 1 个 commit，便于精准回滚。

---

## 六、用户确认结果（已全部确认）

| # | 事项 | 确认结果 |
|---|------|----------|
| 1 | 执行范围 | ✅ 第一+二阶段合并执行（一个 Git 分支），第三阶段独立迭代 |
| 2 | max_tokens 取值 | ✅ 差异化配置：512（测评/简历/客服）+ 768（面试评价/职业探索）+ 256（面试出题） |
| 3 | 差异化超时 | ✅ 采纳：客服 5s / 职业探索 8s / 面试 6s / 简历 5s / 测评 5s |
| 4 | 线程池大小 | ✅ 10 线程（I/O 密集型甜点位） |
| 5 | Prompt 瘦身 | ✅ 新建独立 `PromptOptimizer` 工具类（符合 OCP） |

**全部 5 项已确认，计划可执行。**

---

## 七、与最终技术栈文档对齐

> **对齐目标**：本优化计划是 [AI功能最终技术栈与实施路线.md](file:///d:/HuaweiMoveData/Users/20403/Desktop/软件实训项目/.trae/documents/AI功能最终技术栈与实施路线.md) 的子任务，需明确归属阶段、决策关联、避免与技术栈文档冲突。

### 7.1 优化任务在技术栈文档中的归属

| 本计划任务 | 技术栈文档对应位置 | 归属阶段 | 关联决策 |
|------------|-------------------|----------|----------|
| 放宽超时 + 差异化 max_tokens | 第三章 P1「DeepSeek 基础服务改造」 | 第一阶段 P1 | 决策 1（OpenAI 兼容协议统一封装） |
| 独立线程池（10 线程） | 第三章 P1「DeepSeek 基础服务改造」 | 第一阶段 P1 | 决策 1 |
| JdkClientHttpRequestFactory 连接池 | 第三章 P1「DeepSeek 基础服务改造」 | 第一阶段 P1 | 决策 2（RestTemplate + WebClient 流式） |
| 缓存 TTL 延长 | 第三章 P2「兜底质量提升」 | 第一阶段 P2 | 决策 3（RAG 分阶段边界） |
| PromptOptimizer 瘦身 | 第三章 P2「兜底质量提升」 | 第一阶段 P2 | 决策 6（Prompt 分层混合） |
| SSE 流式（进阶，本次不做） | 第三章 P2「客服/职业探索 SSE」 | 第一阶段 P2 | 决策 2+7（SSE 范围） |

### 7.2 与技术栈文档决策的一致性校验

| 技术栈决策 | 本计划是否一致 | 说明 |
|------------|----------------|------|
| **决策 1** OpenAI 兼容协议统一封装 | ✅ 一致 | 本计划改 `DeepSeekServiceImpl` 属于该封装的内部实现优化，不影响对外接口 |
| **决策 2** RestTemplate + WebClient 流式 | ✅ 一致 | 本计划优化 RestTemplate（同步），WebClient 流式属 P2 进阶项，本次不动 |
| **决策 3** Embedding + MySQL JSON | ✅ 一致 | 本计划第三阶段缓存优化为第二阶段向量重排打基础（缓存减少 Embedding 调用） |
| **决策 4** Resilience4j | ⚠️ 本计划未涉及 | Resilience4j 重试/限流/熔断是 P1 独立任务，本计划聚焦超时与连接池，两者互补 |
| **决策 5** POI + PDFBox | ✅ 无冲突 | 本计划不动简历提取逻辑 |
| **决策 6** Prompt 分层混合 | ✅ 一致 | 本计划 PromptOptimizer 是 Prompt 输入侧瘦身，与模板管理（输出侧）正交 |
| **决策 7** SSE 仅客服+职业探索 | ✅ 一致 | 本计划进阶项标注「本次不做」，对应技术栈 P2 阶段 |
| **决策 8** 工程闭环（额度控制+Prompt 版本） | ✅ 互补 | 本计划第三阶段延长 TTL 与额度控制无冲突；PromptOptimizer 与版本管理正交 |
| **决策 10** 多模型兜底第一阶段不启用 | ✅ 一致 | 本计划仅优化 DeepSeek 调用链路，不涉及备用模型 |

### 7.3 与 Resilience4j（决策 4）的协同关系

本计划与 Resilience4j 改造**互补不冲突**，分工明确：

| 关注点 | 本计划（超时+连接池） | Resilience4j（决策 4） |
|--------|----------------------|------------------------|
| **层次** | 传输层 + 线程层 | 业务治理层 |
| **超时** | HttpClient readTimeout 6s + 硬超时 5s | 不涉及（Resilience4j TimeLimiter 可选） |
| **重试** | 不重试（超时即降级） | @Retry 最多 3 次，指数退避 1s/2s/4s |
| **限流** | 不限流 | @RateLimiter 5 QPS |
| **熔断** | 不熔断 | @CircuitBreaker 失败率 50% 开放 30s |
| **降级** | 硬超时抛异常，业务层兜底 | fallbackMethod 返回规则兜底 |

**协同执行建议**：
1. 本计划（第一+二阶段）先落地，解决「必然超时」的根因
2. Resilience4j 在技术栈 P1 阶段叠加，增加重试/限流/熔断能力
3. 两者叠加后：超时→硬超时兜底 + Resilience4j 重试 + 熔断快速失败

### 7.4 与 AI 额度控制（决策 8）的协同关系

本计划第三阶段延长缓存 TTL 与 AI 额度控制互补：

| 关注点 | 本计划（缓存 TTL） | AI 额度控制（决策 8） |
|--------|---------------------|----------------------|
| **目标** | 减少真实 AI 调用 | 限制用户调用次数 |
| **机制** | Redis 缓存命中跳过 AI | Redis 计数器超额拒绝 |
| **协同** | 缓存命中不消耗配额（客服 FAQ 优先命中） | 配额校验在 AI 调用前 |

**执行顺序**：本计划第三阶段（缓存 TTL）可与 AI 额度控制同步实施，均属 P1 阶段。

### 7.5 执行节奏对齐技术栈路线图

```
技术栈第一阶段 P0（3.5 天）
  └─ R-008 简历优化 + R-010 企业推荐 P0 修复
     （本计划不涉及，独立推进）

技术栈第一阶段 P1（6 天）
  ├─ 本计划第一+二阶段（超时+连接池，约 0.5 天）← 本次执行
  ├─ Resilience4j 改造（决策 4，约 1 天）
  ├─ AI 额度控制（决策 8，约 0.5 天）
  ├─ Prompt 版本管理（决策 6，约 1 天）
  └─ 面试状态机 + 差距分析 AI + 企业推荐可解释评分

技术栈第一阶段 P2（5.5 天）
  ├─ 本计划第三阶段（缓存 TTL + PromptOptimizer，约 0.5 天）← 本次执行
  ├─ 客服/职业探索 SSE（决策 7，约 1 天）
  └─ AI 质量反馈 + FAQ 管理 + 监控看板
```

**关键依赖**：本计划第一+二阶段是 P1 其他任务的前置条件。超时问题不解决，Resilience4j 的重试会加剧超时，额度控制也失去意义（用户调用都走兜底）。建议**优先执行本计划第一+二阶段**。

### 7.6 本计划产出对技术栈文档的反馈

本计划执行后，以下技术栈文档内容需同步更新（待执行完成后）：

| 技术栈文档位置 | 更新内容 |
|----------------|----------|
| 决策 1 实现细节 | 补充「差异化 max_tokens（512/768/256）+ 差异化超时（5s/6s/8s）」 |
| 决策 2 实现细节 | 补充「JdkClientHttpRequestFactory 连接池替代 SimpleClientHttpRequestFactory」 |
| 第三章 P1 路线图 | 补充「独立线程池 10 线程 + @PreDestroy 销毁」 |
| 第三章 P2 路线图 | 补充「PromptOptimizer 工具类 + 缓存 TTL 6 小时」 |
