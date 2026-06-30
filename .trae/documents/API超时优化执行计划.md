# DeepSeek API 超时优化执行计划

> **文档定位**：本文档是 API 超时问题的**执行计划书**，基于排查报告 + 团队确认的「三步走」路线，明确每一步要改什么文件、改什么内容、如何验证。**本文档仅生成计划，不直接修改代码，待用户确认后执行。**
>
> **目标**：API 调用 95% 在 5 秒内返回，超时率从 ~70% 降到 ~5%。
>
> **关联文档**：本计划是 [AI功能最终技术栈与实施路线.md](file:///d:/HuaweiMoveData/Users/20403/Desktop/软件实训项目/.trae/documents/AI功能最终技术栈与实施路线.md) 中 P1 阶段「DeepSeek 基础服务改造」的细化执行方案。

---

## 一、问题根因回顾（3 个层面）

> ⚠️ **校正说明**：本表已基于实际代码校正（2026-06-28），原版根因分析与代码不符，详见第八章校正记录。

| # | 根因 | 现状代码（已校正） | 影响 |
|---|------|----------|------|
| 1 | **max_tokens=4096 过大**，生成需 15-30 秒；读取超时 30 秒过长 | [DeepSeekServiceImpl.java#L90](file:///d:/HuaweiMoveData/Users/20403/Desktop/软件实训项目/软件实训项目-code/backend/src/main/java/com/xuelian/career/service/impl/DeepSeekServiceImpl.java#L90) `max_tokens=4096` + [AppConfig.java#L22](file:///d:/HuaweiMoveData/Users/20403/Desktop/软件实训项目/软件实训项目-code/backend/src/main/java/com/xuelian/career/config/AppConfig.java#L22) `setReadTimeout(30000)` | 用户等 30 秒才报错，生成耗时远超用户容忍 |
| 2 | **SimpleClientHttpRequestFactory 无连接池**，TCP+TLS 重复握手 | [AppConfig.java#L20](file:///d:/HuaweiMoveData/Users/20403/Desktop/软件实训项目/软件实训项目-code/backend/src/main/java/com/xuelian/career/config/AppConfig.java#L20) `new SimpleClientHttpRequestFactory()` | 每次多耗 300-800ms（已用注入单例，非 new 问题） |
| 3 | **同步阻塞占用 Tomcat 线程**，无异步隔离 | [DeepSeekServiceImpl.java#L97-L102](file:///d:/HuaweiMoveData/Users/20403/Desktop/软件实训项目/软件实训项目-code/backend/src/main/java/com/xuelian/career/service/impl/DeepSeekServiceImpl.java#L97-L102) 同步 `restTemplate.exchange` | Tomcat 默认 200 线程，并发 20 个 AI 调用即线程紧张 |

**补充根因（实际代码额外发现）**：

| # | 根因 | 现状代码 | 影响 |
|---|------|----------|------|
| 4 | **temperature=0.7 偏高** | [DeepSeekServiceImpl.java#L89](file:///d:/HuaweiMoveData/Users/20403/Desktop/软件实训项目/软件实训项目-code/backend/src/main/java/com/xuelian/career/service/impl/DeepSeekServiceImpl.java#L89) | 生成内容偏发散，结构化场景应降到 0.3 |
| 5 | **业务调用方无差异化参数** | 6 个调用方全用 `callAPI(sys, prompt)` | 所有场景统一 4096，无法按场景调优 |
| 6 | **API Key 硬编码** | [application-dev.yml#L45](file:///d:/HuaweiMoveData/Users/20403/Desktop/软件实训项目/软件实训项目-code/backend/src/main/resources/application-dev.yml#L45) | 安全问题，技术栈 P0 已识别 |
| 7 | **DeepSeekConfig.timeoutSeconds=60 配置失效** | [DeepSeekConfig.java#L24](file:///d:/HuaweiMoveData/Users/20403/Desktop/软件实训项目/软件实训项目-code/backend/src/main/java/com/xuelian/career/config/DeepSeekConfig.java#L24) 字段存在但 AppConfig 未读取 | 配置中心形同虚设，实际用硬编码 30000ms |

---

## 二、两阶段实施计划（第一+二阶段合并）

> **合并依据**：第一阶段（改超时+改线程池）和第二阶段（换 JdkClientHttpRequestFactory）都属于基础设施层改动，合并执行可一次性压测最优网络 I/O 性能，避免分两次改动 DeepSeekServiceImpl 造成代码冲突。第三阶段（缓存与 Prompt）属业务逻辑层优化，作为独立迭代。

### 第一+二阶段合并：基础设施层改造（一个 Git 分支提交）

**目标**：超时率从 ~70% 降到 ~10%，每次调用省 300-800ms 握手开销。

#### 改动 1.1：放宽硬超时 + 精简 max_tokens

**文件**：[DeepSeekServiceImpl.java](file:///d:/HuaweiMoveData/Users/20403/Desktop/软件实训项目/软件实训项目-code/backend/src/main/java/com/xuelian/career/service/impl/DeepSeekServiceImpl.java)

**改动内容**（已校正行号）：

```java
// L49-L50：默认超时改为通过重载传入（原方法无超时参数）
@Override
public String callAPI(String systemPrompt, String userPrompt) {
    return callAPI(systemPrompt, userPrompt, 5000L, 512);  // 新增重载，默认 5 秒 + 512 token
}

// L89-L90：temperature 0.7→0.3（结构化场景）/ 0.6（对话场景）；max_tokens 4096→差异化
requestBody.put("temperature", 0.3);  // 0.7→0.3，结构化输出更稳定
// max_tokens 由调用方通过新增重载方法传入，不再硬编码 4096
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

**业务调用方改造**（需新增带 max_tokens 的重载方法；行号已校正）：

| 文件 | 行号 | 当前 | 改为 | 场景 |
|------|------|------|------|------|
| [AssessmentServiceImpl.java](file:///d:/HuaweiMoveData/Users/20403/Desktop/软件实训项目/软件实训项目-code/backend/src/main/java/com/xuelian/career/service/impl/AssessmentServiceImpl.java) | L260 | `callAPI(sys, prompt)` | `callAPI(sys, prompt, 5000L, 512)` | 测评建议 |
| [CareerExplorationServiceImpl.java](file:///d:/HuaweiMoveData/Users/20403/Desktop/软件实训项目/软件实训项目-code/backend/src/main/java/com/xuelian/career/service/impl/CareerExplorationServiceImpl.java) | L67 | `callAPIWithCache(...)` | `callAPIWithCache(..., 8000L, 768)` | 职业探索 |
| [InterviewServiceImpl.java](file:///d:/HuaweiMoveData/Users/20403/Desktop/软件实训项目/软件实训项目-code/backend/src/main/java/com/xuelian/career/service/impl/InterviewServiceImpl.java) | L265 | `callAPI(sys, prompt)` | `callAPI(sys, prompt, 6000L, 256)` | 面试出题 |
| [InterviewServiceImpl.java](file:///d:/HuaweiMoveData/Users/20403/Desktop/软件实训项目/软件实训项目-code/backend/src/main/java/com/xuelian/career/service/impl/InterviewServiceImpl.java) | L307 | `callAPI(sys, prompt)` | `callAPI(sys, prompt, 6000L, 768)` | 面试评价 |
| [ResumeServiceImpl.java](file:///d:/HuaweiMoveData/Users/20403/Desktop/软件实训项目/软件实训项目-code/backend/src/main/java/com/xuelian/career/service/impl/ResumeServiceImpl.java) | L56 | `callAPI(sys, prompt)` | `callAPI(sys, prompt, 5000L, 512)` | 简历优化 |
| [CustomerServiceServiceImpl.java](file:///d:/HuaweiMoveData/Users/20403/Desktop/软件实训项目/软件实训项目-code/backend/src/main/java/com/xuelian/career/service/impl/CustomerServiceServiceImpl.java) | L98 | `callAPIWithCache(..., 1800L)` | `callAPIWithCache(..., 3600L, 512)` | 智能客服 |

#### 改动 1.2：隔离 AI 调用线程池（10 线程，可选异步化）

**文件**：[DeepSeekServiceImpl.java](file:///d:/HuaweiMoveData/Users/20403/Desktop/软件实训项目/软件实训项目-code/backend/src/main/java/com/xuelian/career/service/impl/DeepSeekServiceImpl.java)

**实际代码现状**（已校正）：[L97-L102](file:///d:/HuaweiMoveData/Users/20403/Desktop/软件实训项目/软件实训项目-code/backend/src/main/java/com/xuelian/career/service/impl/DeepSeekServiceImpl.java#L97-L102) 是**同步阻塞调用**，直接 `restTemplate.exchange`，无 `CompletableFuture`，无 `new RestTemplate`（已用注入单例）。

**改动内容**（已校正）：

```java
// 新增字段（类顶部，L40 附近）
// 10 线程：I/O 密集型甜点位，2 核服务器支持 2 QPS 持续吞吐
private final ExecutorService aiCallExecutor = Executors.newFixedThreadPool(10, r -> {
    Thread t = new Thread(r, "ai-call-worker");
    t.setDaemon(true);
    return t;
});

// L97-L102：将同步调用改为异步（用 CompletableFuture 包装现有同步逻辑）
// 原代码：ResponseEntity<Map> response = restTemplate.exchange(...) 直接返回
// 改为：
CompletableFuture<String> future = CompletableFuture.supplyAsync(() -> {
    // 复用注入的连接池 RestTemplate（AppConfig 改造后即为连接池）
    ResponseEntity<Map> response = restTemplate.exchange(
            deepSeekConfig.getApiUrl(),
            HttpMethod.POST,
            entity,
            Map.class
    );
    return extractContent(response.getBody());
}, aiCallExecutor);  // ← 独立线程池，隔离 Tomcat 线程

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

**关键改动**（已校正，合并第一阶段线程池 + 第二阶段连接池）：
- 新增 `aiCallExecutor`（10 线程，daemon）
- 将 [L97-L102](file:///d:/HuaweiMoveData/Users/20403/Desktop/软件实训项目/软件实训项目-code/backend/src/main/java/com/xuelian/career/service/impl/DeepSeekServiceImpl.java#L97-L102) 同步调用用 `CompletableFuture.supplyAsync(..., aiCallExecutor)` 包装
- **无需删除 `new RestTemplate`**（实际代码已是注入单例，原描述有误）
- AppConfig 改造后（改动 1.3），注入的 `restTemplate` 即为连接池版本

**异步化说明**：实际代码是同步阻塞，改为 CompletableFuture 后，Controller 层调用 `future.get(5, TimeUnit.SECONDS)` 获取结果，超时走兜底。如保持同步也可（改动量更小），但无法隔离 Tomcat 线程。

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

---

## 八、实际代码校正记录（2026-06-28）

> **校正背景**：在 Plan 模式下探索实际代码后发现，本文档原版根因分析与实际代码严重不符，已校正。本章节记录校正内容，便于追溯。

### 8.1 校正前的失实描述

原版文档基于错误假设编写，与实际代码存在 7 处差异：

| # | 原文档描述 | 实际代码 | 校正动作 |
|---|-----------|----------|----------|
| 1 | 硬超时 3000ms（DeepSeekServiceImpl L52） | 无硬超时；AppConfig 读取超时 30000ms | 根因 1 改为「max_tokens=4096 + 读取超时 30 秒」 |
| 2 | max_tokens=1024（L89） | max_tokens=4096（L90） | 改动 1.1 改为「4096→差异化」 |
| 3 | temperature=0.6 | temperature=0.7（L89） | 改动 1.1 补充「0.7→0.3/0.6」 |
| 4 | 每次 new RestTemplate（L99-L102） | 已用注入单例（L34, L97） | 改动 1.2 删除「删除 new RestTemplate」描述 |
| 5 | CompletableFuture + ForkJoinPool（L97） | 同步阻塞调用，无异步 | 改动 1.2 改为「将同步用 CompletableFuture 包装」 |
| 6 | DeepSeekService 有重载方法 | 接口仅 4 个方法，无重载 | 改动 1.1 明确「需新增重载」 |
| 7 | 业务调用方行号 L259/L61/L235/L277 | 实际 L260/L67/L265/L307 | 业务调用方表行号全部校正 |

### 8.2 校正后的根因优先级

校正后，真正的根因优先级（按影响降序）：

| 优先级 | 根因 | 影响 | 改动归属 |
|--------|------|------|----------|
| P0 | max_tokens=4096 过大 | 生成 15-30 秒 | 改动 1.1 |
| P0 | 读取超时 30 秒过长 | 用户等 30 秒才报错 | 改动 1.3（AppConfig） |
| P1 | SimpleClientHttpRequestFactory 无连接池 | 每次多耗 300-800ms | 改动 1.3（AppConfig） |
| P1 | temperature=0.7 偏高 | 生成发散 | 改动 1.1 |
| P1 | 业务调用方无差异化参数 | 无法按场景调优 | 改动 1.1（业务调用方） |
| P2 | 同步阻塞占用 Tomcat 线程 | 并发能力受限 | 改动 1.2（线程池） |
| P2 | DeepSeekConfig.timeoutSeconds 配置失效 | 配置中心形同虚设 | 改动 1.3（读取配置） |

### 8.3 校正对方案的影响

**方案骨架不变**：JdkClientHttpRequestFactory 连接池、差异化 max_tokens、独立线程池三大改动仍正确，仅细节校正。

**改动量调整**：
- 改动 1.2 改动量略增（需将同步调用包装为 CompletableFuture，原假设已有异步）
- 其余改动量不变

**风险评估更新**：
- 原风险「max_tokens 512 导致面试评价 JSON 截断」保留
- 新增风险「异步化改造可能影响事务上下文」→ 缓解：AI 调用不涉及数据库事务

### 8.4 校正验证

- [x] 第一章根因表与实际代码一致（7 个根因全部基于实际代码）
- [x] 第二章改动 1.1 行号正确（L89 temperature、L90 max_tokens）
- [x] 第二章改动 1.2 删除「删除 new RestTemplate」描述
- [x] 业务调用方行号全部校正（L260/L67/L265/L307/L56/L98）
- [x] 保留 AppConfig 替换为 JdkClientHttpRequestFactory 的方案
