# AI 智能求职辅导平台 — 基于技术选型的 AI 功能实现与完善方向（问答式）

> **文档定位**：本文档基于已完成的 [AI功能技术选型方案.md](file:///d:/HuaweiMoveData/Users/20403/Desktop/软件实训项目/.trae/documents/AI功能技术选型方案.md)，将技术选型决策落地到每个 AI 功能的具体实现和完善方向上，以问答形式呈现，作为后续执行的指引。
>
> **技术选型决策状态（全部已确认）**：
> - ✅ D1 简历提取：POI+PDFBox（复用现有依赖，零新增）
> - ✅ D2 HTTP 客户端：RestTemplate（存量同步）+ WebClient（仅 SSE 流式）
> - ✅ D3 流式输出：SSE（仅客服+职业探索）
> - ✅ D4 重试限流：Resilience4j
> - ✅ D5 向量检索：PGVector（MySQL 关键词过滤 + PG 向量语义重排，第二阶段启用）
> - ✅ D6 多模型兜底：DeepSeek 单一+规则（OpenAI 兼容协议封装预留扩展）
> - ✅ D7 Prompt 管理：分层混合（基础文件+业务 DB+增量覆盖）
>
> **整体策略**：围绕「小团队轻量化、低运维、平衡推荐质量与交付成本」统一规划，分两阶段迭代——第一阶段（30 天内）保障简历优化 P0 上线，D5 暂用 MySQL 关键词；第二阶段（演示后）启用 PGVector 与多厂商容灾。

---

## 一、通用 AI 基础设施实现

### Q1：DeepSeek 基础服务如何改造？

**现状**：[DeepSeekServiceImpl.java](file:///d:/HuaweiMoveData/Users/20403/Desktop/软件实训项目/软件实训项目-code/软件实训项目-第一版/backend/src/main/java/com/xuelian/career/service/impl/DeepSeekServiceImpl.java) 重试固定 1 次、退避 1 秒，无限流熔断，同步阻塞。

**基于技术选型（D4 Resilience4j）的改造方案**：

#### 1. 新增依赖（pom.xml）
```xml
<dependency>
    <groupId>io.github.resilience4j</groupId>
    <artifactId>resilience4j-spring-boot3</artifactId>
    <version>2.2.0</version>
</dependency>
<!-- AOP 支持（注解生效需要） -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-aop</artifactId>
</dependency>
```

**依赖说明**：`resilience4j-spring-boot3` 是 Resilience4j 针对 Spring Boot 3 的官方 starter，自动配置 Retry/RateLimiter/CircuitBreaker 实例，通过 `@Retry`/`@RateLimiter`/`@CircuitBreaker` 注解声明式使用，底层基于 AOP 代理。

#### 2. 配置（application.yml）
```yaml
resilience4j:
  retry:
    instances:
      deepseek:
        max-attempts: 3                    # 最多重试 3 次（含首次）
        wait-duration: 1s                  # 首次重试等待 1 秒
        exponential-backoff-multiplier: 2  # 指数退避倍数：1s → 2s → 4s
        retry-exceptions:
          - org.springframework.web.client.RestClientException  # 仅对网络异常重试
  ratelimiter:
    instances:
      deepseek:
        limit-for-period: 5                # 每周期允许 5 次调用
        limit-refresh-period: 1s           # 周期 1 秒（即 5 QPS）
        timeout-duration: 0                # 超出限流立即失败，不排队等待
  circuitbreaker:
    instances:
      deepseek:
        failure-rate-threshold: 50         # 失败率 50% 触发熔断
        slow-call-rate-threshold: 80       # 慢调用占比 80% 触发熔断
        slow-call-duration-threshold: 10s  # 慢调用阈值 10 秒
        wait-duration-in-open-state: 30s   # 熔断开放 30 秒后进入半开
        sliding-window-size: 10            # 滑动窗口 10 次调用
        sliding-window-type: COUNT_BASED   # 按调用次数统计
        minimum-number-of-calls: 5         # 至少 5 次调用后才计算失败率
```

**配置解释**：
- **重试**：仅对 `RestClientException`（网络异常）重试，避免对 4xx 参数错误重试（无意义）。指数退避避免短时间内重复打失败请求。
- **限流**：5 QPS 是 DeepSeek 免费档位的安全值，避免触发 429 限流。`timeout-duration: 0` 表示超限立即失败，走兜底逻辑而非阻塞等待。
- **熔断**：滑动窗口 10 次，失败率 50% 开放熔断 30 秒，半开状态放 1 个请求探测，成功则恢复。这避免 DeepSeek 故障期持续打失败请求导致线程堆积。

#### 3. 改造 `callAPI` 方法
```java
@Override
@Retry(name = "deepseek")
@RateLimiter(name = "deepseek")
@CircuitBreaker(name = "deepseek", fallbackMethod = "callAPIFallback")
public String callAPI(String systemPrompt, String userPrompt) {
    // 原有调用逻辑保持不变
    // Resilience4j 通过 AOP 代理自动织入重试/限流/熔断逻辑
    // ...
}

/**
 * 熔断器开放时执行的兜底方法
 * 方法签名必须与原方法一致，多一个 Throwable 参数
 */
private String callAPIFallback(String systemPrompt, String userPrompt, Throwable t) {
    log.warn("DeepSeek 熔断降级，执行兜底: {}", t.getMessage());
    // 记录降级日志
    AiCallLog callLog = new AiCallLog();
    callLog.setScene("AI_CALL");
    callLog.setResponseSource("FALLBACK");
    callLog.setStatus("CIRCUIT_OPEN");
    callLog.setFallbackReason("熔断器开放: " + t.getMessage());
    callLog.setCreatedAt(LocalDateTime.now());
    saveCallLog(callLog);
    // 抛出异常，由业务层各自走兜底
    throw new BusinessException("AI 服务暂时不可用，已触发熔断保护");
}
```

**改造说明**：
- 注解生效前提：类需由 Spring 管理（已是 `@Service`），方法需 `public`（已是）
- `fallbackMethod` 指定熔断时的兜底方法，签名须与原方法一致 + 末尾加 `Throwable` 参数
- 兜底方法抛 `BusinessException`，由各业务 Service 的 `try-catch` 捕获后走各自兜底逻辑

#### 4. `isAvailable` 优化
```java
@Override
public boolean isAvailable() {
    // 优先查 Redis 熔断状态标记（60 秒 TTL）
    Object available = redisTemplate.opsForValue().get(AI_AVAILABLE_KEY);
    if (available instanceof Boolean) {
        return (Boolean) available;
    }
    // 未缓存时，查 Resilience4j 熔断器实时状态
    CircuitBreaker circuitBreaker = CircuitBreakerRegistry.ofDefaults().circuitBreaker("deepseek");
    CircuitBreaker.State state = circuitBreaker.getState();
    return state != CircuitBreaker.State.OPEN;
}
```

**优化解释**：原实现未缓存命中时默认返回 `true`，在 API 故障期持续打失败请求。改为查询 Resilience4j 熔断器实时状态，熔断开放时返回 `false`，业务层直接走兜底，避免无效请求。

#### 5. 保留的能力
- `callAPIWithCache`：Redis 缓存机制保持不变
- `parseJSONResponse`：JSON 解析保持不变
- `ai_call_log` 日志记录：保持不变，新增 `CIRCUIT_OPEN` 状态

**完善方向**：
- 熔断器开放时，各业务 Service 应快速走兜底逻辑，避免线程阻塞
- `ai_call_log` 表增加 `circuit_state` 字段，记录熔断器状态便于监控

---

### Q2：流式输出如何实现？

**现状**：所有 AI 接口同步阻塞，用户等待 15-20 秒。DeepSeek API 原生支持 `stream=true` 参数，可逐 token 返回。

**基于技术选型（D3 SSE 仅客服+职业探索）的改造方案**：

#### 1. 新增依赖（pom.xml）
```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-webflux</artifactId>
</dependency>
```

**依赖说明**：WebFlux 提供 `WebClient`（非阻塞 HTTP 客户端）和 `Flux`/`Mono`（响应式流）。Spring Boot 3 允许 WebMVC 和 WebFlux 共存，WebMVC 处理同步接口，WebFlux 仅用于 SSE 流式接口的 `Flux<ServerSentEvent>` 返回。

#### 2. DeepSeekService 新增流式方法
```java
/**
 * 流式调用 DeepSeek API
 * @param systemPrompt 系统提示词
 * @param userPrompt   用户提示词
 * @return 文本片段的响应式流
 */
Flux<String> callAPIStream(String systemPrompt, String userPrompt);
```

#### 3. DeepSeekServiceImpl 实现流式方法
```java
@Override
public Flux<String> callAPIStream(String systemPrompt, String userPrompt) {
    // 构建请求体
    Map<String, Object> requestBody = new LinkedHashMap<>();
    requestBody.put("model", deepSeekConfig.getModel());
    requestBody.put("stream", true);  // 开启流式
    requestBody.put("temperature", 0.7);
    requestBody.put("max_tokens", 4096);

    List<Map<String, String>> messages = new ArrayList<>();
    messages.add(Map.of("role", "system", "content", systemPrompt));
    messages.add(Map.of("role", "user", "content", userPrompt));
    requestBody.put("messages", messages);

    // 用 WebClient 发起流式请求
    return webClient.post()
            .uri(deepSeekConfig.getApiUrl())
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + deepSeekConfig.getApiKey())
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(requestBody)
            .retrieve()
            .bodyToFlux(String.class)              // 按行接收 SSE 数据
            .filter(line -> !line.equals("[DONE]")) // 过滤结束标记
            .map(this::extractStreamContent)        // 解析每行的 JSON，提取 content
            .filter(Objects::nonNull)
            .doOnComplete(() -> log.info("流式调用完成"))
            .doOnError(e -> log.warn("流式调用异常: {}", e.getMessage()))
            .onErrorResume(e -> Flux.just("【AI 服务暂时不可用，请稍后重试】"));
}

/**
 * 从流式响应的单行 JSON 中提取 content 字段
 * DeepSeek 流式返回格式：{"choices":[{"delta":{"content":"文本片段"}}]}
 */
private String extractStreamContent(String json) {
    try {
        Map<String, Object> chunk = objectMapper.readValue(json, new TypeReference<>() {});
        List<Map<String, Object>> choices = (List<Map<String, Object>>) chunk.get("choices");
        if (choices != null && !choices.isEmpty()) {
            Map<String, Object> delta = (Map<String, Object>) choices.get(0).get("delta");
            if (delta != null) {
                return (String) delta.get("content");
            }
        }
    } catch (Exception e) {
        log.debug("解析流式 chunk 失败: {}", e.getMessage());
    }
    return null;
}
```

**实现解释**：
- `stream=true` 让 DeepSeek 逐 token 返回，每个 chunk 是 `{"choices":[{"delta":{"content":"片段"}}]}`
- `bodyToFlux(String.class)` 按 SSE 协议逐行接收
- `[DONE]` 是 DeepSeek 流结束标记，过滤掉
- `onErrorResume` 确保流式失败时返回友好提示，而非中断连接

#### 4. WebClient Bean 配置（AppConfig.java 新增）
```java
@Bean
public WebClient webClient() {
    int timeoutMs = deepSeekConfig.getTimeoutSeconds() * 1000;
    HttpClient httpClient = HttpClient.create()
            .responseTimeout(Duration.ofMillis(timeoutMs));
    return WebClient.builder()
            .clientConnector(new ReactorClientHttpConnector(httpClient))
            .build();
}
```

**配置说明**：WebClient 的超时通过 `HttpClient.responseTimeout` 设置，与 RestTemplate 保持一致。WebClient 是非阻塞的，不会占用 Tomcat 线程。

#### 5. Controller 改造（仅 2 个接口）
```java
// CustomerServiceController.java 新增
@GetMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
public Flux<ServerSentEvent<String>> chatStream(@RequestParam String question,
                                                 @RequestParam(required = false) String userRole,
                                                 HttpServletRequest httpRequest) {
    String role = userRole != null ? userRole : "STUDENT";
    // 先尝试 FAQ 匹配，命中则直接返回完整答案（不流式）
    // 未命中则流式调用 AI
    return customerServiceService.chatStream(question, role)
            .map(content -> ServerSentEvent.<String>builder()
                    .data(content)
                    .build());
}
```

**Controller 说明**：
- `produces = TEXT_EVENT_STREAM_VALUE` 声明 SSE 协议
- `Flux<ServerSentEvent<String>>` 是 Spring 对 SSE 的标准支持
- 前端收到的数据格式：`data:文本片段\n\n`

#### 6. 前端改造（CustomerServiceView.vue / CareerExplorationView.vue）
```typescript
async function handleSendStream(text: string) {
  messages.value.push({ role: 'user', content: text })
  const assistantMsg = ref({ role: 'assistant', content: '' })
  messages.value.push(assistantMsg.value)
  
  // 用 fetch + ReadableStream 接收 SSE
  const response = await fetch('/api/customer/chat/stream?question=' + encodeURIComponent(text), {
    headers: { 'Authorization': 'Bearer ' + token }
  })
  const reader = response.body!.getReader()
  const decoder = new TextDecoder()
  
  while (true) {
    const { done, value } = await reader.read()
    if (done) break
    const chunk = decoder.decode(value)
    // 解析 SSE 格式：data:xxx\n\n
    const lines = chunk.split('\n')
    for (const line of lines) {
      if (line.startsWith('data:')) {
        assistantMsg.value.content += line.slice(5)
      }
    }
  }
}
```

**前端说明**：
- 用 `fetch + ReadableStream` 而非 `EventSource`，因为 `EventSource` 不支持自定义 Header（JWT 鉴权需要）
- 逐 chunk 拼接 `assistantMsg.content`，实现打字机效果
- 首字响应 2-3 秒（DeepSeek 首 token 延迟），全文完成约 10-15 秒

#### 7. Nginx 配置（nginx.conf）
```nginx
location /api/ {
    proxy_pass http://backend:8080;
    proxy_buffering off;              # 关闭缓冲，关键配置
    proxy_cache off;
    proxy_set_header X-Accel-Buffering no;  # 响应头，告知上游不缓冲
    proxy_read_timeout 60s;
}
```

**Nginx 说明**：默认 Nginx 会缓冲后端响应，导致 SSE 流式失效（用户等全部生成完才看到）。`proxy_buffering off` 是 SSE 在 Nginx 反向代理下的必需配置。

**不改造的接口**：简历优化、企业推荐、差距分析、模拟面试（结果是结构化 JSON，流式意义不大）

**完善方向**：
- 流式过程中显示「正在思考...」动画
- 流式失败时自动降级为同步兜底

---

### Q3：Prompt 模板管理如何改造？

**现状**：[PromptTemplateUtil.java](file:///d:/HuaweiMoveData/Users/20403/Desktop/软件实训项目/软件实训项目-code/软件实训项目-第一版/backend/src/main/java/com/xuelian/career/util/PromptTemplateUtil.java) 从 classpath 文件加载，ConcurrentHashMap 缓存，无热更新。共 5 个 .txt 模板。

**基于技术选型（D7 分层混合）的改造方案**：

#### 1. 新增 DB 表（init.sql）
```sql
CREATE TABLE prompt_template (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    name        VARCHAR(64)  NOT NULL COMMENT '模板名（对应文件名，如 career_exploration）',
    content     TEXT         NOT NULL COMMENT '模板完整内容（覆盖文件版本）',
    description VARCHAR(255) COMMENT '模板描述/修改说明',
    is_active   TINYINT      NOT NULL DEFAULT 1 COMMENT '1=激活 0=停用',
    updated_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_name_active (name, is_active)
) COMMENT='Prompt 模板表（业务层覆盖）';
```

**表设计说明**：
- `name` 对应 classpath 文件名（如 `career_exploration`），便于关联
- `content` 存储完整覆盖版本（非 diff），避免 diff 合并复杂度
- 唯一键 `uk_name_active` 确保同一模板只有一个激活版本
- 初始可为空，无需写数据导入脚本，需要调整哪个模板再录入

#### 2. 新增 Entity 和 Mapper
```java
// PromptTemplate.java
@Data
@TableName("prompt_template")
public class PromptTemplate {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private String content;
    private String description;
    private Integer isActive;
    private LocalDateTime updatedAt;
}

// PromptTemplateMapper.java
@Mapper
public interface PromptTemplateMapper extends BaseMapper<PromptTemplate> {
}
```

#### 3. 改造 `PromptTemplateUtil.loadTemplate(name)` 逻辑
```java
private final PromptTemplateMapper promptTemplateMapper;

/**
 * 加载模板：DB 激活版本 > classpath 文件 > 抛异常
 */
public String loadTemplate(String name) {
    // 1. 先查缓存
    String cached = templateCache.get(name);
    if (cached != null) return cached;
    
    // 2. 查 DB（业务层覆盖）
    PromptTemplate dbTemplate = promptTemplateMapper.selectOne(
        new LambdaQueryWrapper<PromptTemplate>()
            .eq(PromptTemplate::getName, name)
            .eq(PromptTemplate::getIsActive, 1)
            .last("LIMIT 1")
    );
    
    String content;
    if (dbTemplate != null) {
        // DB 命中 → 使用 DB 内容（业务层覆盖）
        content = dbTemplate.getContent();
        log.debug("加载 DB 覆盖模板: {} (更新于 {})", name, dbTemplate.getUpdatedAt());
    } else {
        // DB 未命中 → 回退 classpath 文件（基础层）
        content = loadFromClasspath(name);
        log.debug("加载文件基础模板: {}", name);
    }
    
    // 3. 存入缓存
    templateCache.put(name, content);
    return content;
}

/**
 * 清空缓存（管理端调用 save/refresh 时触发）
 */
public void clearCache() {
    templateCache.clear();
    log.info("Prompt 模板缓存已清空");
}
```

**改造说明**：
- 读取优先级：缓存 > DB 激活版本 > classpath 文件 > 抛异常
- DB 命中说明管理员已设置业务层覆盖，使用 DB 内容
- DB 未命中回退到文件版本，保证基础模板始终可用
- 缓存减少 DB 查询，但保存/删除时需主动清缓存

#### 4. 新增管理端 Controller
```java
@RestController
@RequestMapping("/api/admin/prompt")
public class PromptAdminController {
    
    @Autowired private PromptTemplateMapper mapper;
    @Autowired private PromptTemplateUtil promptTemplateUtil;
    
    /** 列出 DB 中已覆盖的模板 */
    @GetMapping("/list")
    public R list() {
        return R.ok(mapper.selectList(
            new LambdaQueryWrapper<PromptTemplate>().eq(PromptTemplate::getIsActive, 1)
        ));
    }
    
    /** 新增/更新业务模板覆盖内容 */
    @PostMapping("/save")
    public R save(@RequestBody PromptTemplate dto) {
        PromptTemplate existing = mapper.selectOne(
            new LambdaQueryWrapper<PromptTemplate>()
                .eq(PromptTemplate::getName, dto.getName())
                .eq(PromptTemplate::getIsActive, 1)
        );
        if (existing != null) {
            existing.setContent(dto.getContent());
            existing.setDescription(dto.getDescription());
            mapper.updateById(existing);
        } else {
            dto.setIsActive(1);
            mapper.insert(dto);
        }
        // 清缓存，下次调用重新查 DB
        promptTemplateUtil.clearCache();
        return R.ok("保存成功");
    }
    
    /** 删除 DB 覆盖，回退到文件版本 */
    @DeleteMapping("/{name}")
    public R delete(@PathVariable String name) {
        mapper.delete(new LambdaQueryWrapper<PromptTemplate>().eq(PromptTemplate::getName, name));
        promptTemplateUtil.clearCache();
        return R.ok("已恢复文件默认版本");
    }
    
    /** 手动清缓存 */
    @PostMapping("/refresh")
    public R refresh() {
        promptTemplateUtil.clearCache();
        return R.ok("缓存已刷新");
    }
}
```

#### 5. 新增管理端页面（PromptManageView.vue）
- 列表展示：模板名、描述、最后更新时间、是否激活
- 编辑器：纯文本 textarea（Prompt 本身是纯文本，无需 Markdown 编辑器）
- 操作：保存、激活/停用、恢复文件默认版本（删除 DB 记录）
- 仅展示业务层模板（`career_exploration`、`customer_service`、`gap_analysis`），基础模板不暴露

**分层归属**：
| 模板文件 | 归属层 | 是否可后台编辑 |
|----------|--------|----------------|
| `career_exploration.txt` | 业务层 | ✅ |
| `customer_service.txt` | 业务层 | ✅ |
| `gap_analysis.txt`（新增） | 业务层 | ✅ |
| `resume_optimize.txt` | 基础层 | ❌（走发布流程） |
| `mock_interview.txt` | 基础层 | ❌（走发布流程） |
| `project_parse.txt` | 基础层 | ❌（走发布流程） |

**分层设计理由**：
- 业务层（客服话术、职业探索推荐逻辑）需要根据演示反馈快速调整，走 DB 热更新
- 基础层（简历优化、面试、项目解析）Prompt 结构稳定，走 Git + 重启发布流程，避免管理端误改导致全站 AI 行为漂移

**完善方向**：
- 后台编辑页展示「文件基准版本」与「DB 覆盖版本」对比
- 提供「恢复文件默认」按钮一键删除 DB 记录

---

### Q3.5：如何确保 AI 回答与项目数据紧密关联且具备认知边界？

**背景**：AI 大模型是通用的，如果不加约束，可能回答与求职辅导平台无关的问题，或给出脱离项目实际数据的建议。需要实现两层约束：
1. **认知边界**：限定 AI 只回答求职辅导、岗位介绍、学习建议等平台相关领域问题
2. **数据上下文关联**：调用 AI 前，从项目数据库查询相关数据拼接到 Prompt，让 AI 基于真实数据回答

#### 1. 认知边界的实现（System Prompt 限定）

为每个 AI 功能设计专属的 System Prompt，明确角色定位和回答边界：

```java
// 各功能的 System Prompt（认知边界声明）

// 职业探索
private static final String SYSTEM_CAREER_EXPLORATION =
    "你是「AI 智能求职辅导平台」的职业规划导师。" +
    "认知边界：仅回答与大学生求职、互联网岗位方向、职业规划相关的问题。" +
    "禁止回答：政治、宗教、医疗、法律建议、与求职无关的闲聊。" +
    "若用户提问超出边界，礼貌引导回求职话题：「我是求职辅导助手，主要帮您探索职业方向，其他问题建议咨询专业人士。」";

// 简历优化
private static final String SYSTEM_RESUME_OPTIMIZE =
    "你是「AI 智能求职辅导平台」的简历优化专家。" +
    "认知边界：仅针对用户上传的简历内容进行分析和优化建议。" +
    "禁止回答：非简历相关的问题。若用户提问偏离，引导：「请上传简历，我将为您分析优化。」";

// 模拟面试
private static final String SYSTEM_MOCK_INTERVIEW =
    "你是「AI 智能求职辅导平台」的技术面试官。" +
    "认知边界：仅围绕目标岗位的技术问题进行面试提问和评估。" +
    "禁止回答：与面试无关的问题。面试中若用户偏离话题，引导：「让我们继续面试，下一题是...」";

// 企业推荐
private static final String SYSTEM_PROJECT_PARSE =
    "你是「AI 智能求职辅导平台」的技术架构师。" +
    "认知边界：仅根据企业输入的项目需求，解析所需岗位、技能标签和人数建议。" +
    "技能标签必须从平台技能词典中选择，不得自行编造。" +
    "禁止回答：项目需求解析以外的内容。";

// 差距分析
private static final String SYSTEM_GAP_ANALYSIS =
    "你是「AI 智能求职辅导平台」的能力提升顾问。" +
    "认知边界：仅根据用户的技能差距报告，生成个性化的提升建议。" +
    "建议需基于平台岗位技能词典和学习资源库，不得推荐外部付费课程。";

// 智能客服
private static final String SYSTEM_CUSTOMER_SERVICE =
    "你是「AI 智能求职辅导平台」的客服助手。" +
    "认知边界：仅回答平台功能使用、岗位介绍、学习路径、简历面试准备相关问题。" +
    "禁止回答：平台以外的技术问题、个人隐私问题。" +
    "若无法回答，提示：「建议联系平台管理员」。";
```

**认知边界设计说明**：
- 每个功能的 System Prompt 明确「角色定位 + 认知边界 + 禁止事项 + 超界引导」
- AI 回答前先判断是否在边界内，超界则礼貌引导
- 技能标签约束：企业推荐要求 AI 从平台技能词典选择，不编造

#### 2. 数据上下文关联策略

每个 AI 功能调用前，从数据库查询相关数据拼接到 Prompt：

| AI 功能 | 数据库查询内容 | 拼接到 Prompt 的位置 | 目的 |
|---------|----------------|---------------------|------|
| 职业探索 | 用户画像、测评结果、岗位列表、技能词典 | `profile_json`、`positions_list` | 让 AI 基于真实画像和岗位推荐 |
| 简历优化 | 用户目标岗位的技能要求、岗位 JD | `skill_requirements`、`job_jd` | 让 AI 按岗位要求评估简历 |
| 模拟面试 | 岗位技能要求、题库模板 | `position_skills`、`question_bank` | 让 AI 按岗位技能出题 |
| 企业推荐 | 技能词典、候选人画像 | `skill_dictionary`、`candidate_profiles` | 让 AI 输出标准技能标签 |
| 差距分析 | 用户技能、岗位技能要求 | `user_skills`、`job_skills` | 让 AI 基于真实差距给建议 |
| 智能客服 | FAQ 知识库 | `faq_context` | 让 AI 基于 FAQ 回答（RAG） |

#### 3. 数据上下文关联的代码实现（通用模式）

```java
/**
 * AI 调用前置：构建数据上下文
 * 每个功能调用 AI 前，先从数据库查询相关数据，拼接到 Prompt
 */
public class AiContextBuilder {
    
    @Autowired private UserProfileMapper profileMapper;
    @Autowired private JobPositionMapper jobPositionMapper;
    @Autowired private SkillMapper skillMapper;
    @Autowired private UserSkillMapper userSkillMapper;
    @Autowired private FaqMapper faqMapper;
    @Autowired private AssessmentResultMapper assessmentMapper;
    
    /**
     * 职业探索：构建用户画像 + 岗位列表上下文
     */
    public Map<String, String> buildCareerExplorationContext(Long userId) {
        Map<String, String> params = new HashMap<>();
        
        // 1. 用户画像
        UserProfile profile = profileMapper.selectByUserId(userId);
        params.put("profile_json", JSON.toJSONString(profile));
        
        // 2. 测评结果（结构化摘要）
        AssessmentResult assessment = assessmentMapper.selectLatestByUserId(userId);
        params.put("assessment_summary", assessment != null ? assessment.getStructuredSummary() : "暂无测评");
        
        // 3. 平台岗位列表（让 AI 从中选择推荐，不编造岗位）
        List<JobPosition> positions = jobPositionMapper.selectAll();
        params.put("positions_list", positions.stream()
            .map(p -> p.getTitle() + "(" + p.getDirection() + ")")
            .collect(Collectors.joining("、")));
        
        // 4. 兴趣偏好
        params.put("interests", profile.getInterests() != null ? profile.getInterests() : "未填写");
        
        return params;
    }
    
    /**
     * 简历优化：构建目标岗位技能要求上下文
     */
    public Map<String, String> buildResumeContext(Long userId, String resumeText) {
        Map<String, String> params = new HashMap<>();
        
        // 1. 简历文本（已提取）
        params.put("resume_text", resumeText);
        
        // 2. 用户目标岗位
        UserProfile profile = profileMapper.selectByUserId(userId);
        String targetJob = profile.getTargetJobTitle() != null ? profile.getTargetJobTitle() : "软件开发工程师";
        params.put("job_category", targetJob);
        
        // 3. 目标岗位的技能要求（从岗位技能要求表查）
        List<String> skillRequirements = skillMapper.selectRequirementsByJobTitle(targetJob);
        params.put("skill_requirements", String.join("、", skillRequirements));
        
        // 4. 目标岗位 JD（让 AI 按 JD 评估简历匹配度）
        JobPosition position = jobPositionMapper.selectByTitle(targetJob);
        params.put("job_jd", position != null ? position.getDescription() : "暂无 JD");
        
        return params;
    }
    
    /**
     * 企业推荐：构建技能词典 + 候选人画像上下文
     */
    public Map<String, String> buildProjectParseContext(String projectDescription) {
        Map<String, String> params = new HashMap<>();
        
        // 1. 项目描述
        params.put("project_description", projectDescription);
        
        // 2. 平台技能词典（约束 AI 输出标准技能标签，不编造）
        List<Skill> skills = skillMapper.selectAll();
        params.put("skill_dictionary", skills.stream()
            .map(s -> s.getName() + "(" + s.getCategory() + ")")
            .collect(Collectors.joining("、")));
        
        // 3. 平台岗位模板（让 AI 从中选择，不编造岗位）
        List<JobPosition> positionTemplates = jobPositionMapper.selectAll();
        params.put("position_templates", positionTemplates.stream()
            .map(p -> p.getTitle() + "[" + p.getDirection() + "]")
            .collect(Collectors.joining("、")));
        
        return params;
    }
    
    /**
     * 差距分析：构建用户技能 + 岗位技能要求上下文
     */
    public Map<String, String> buildGapAnalysisContext(Long userId, Long jobId) {
        Map<String, String> params = new HashMap<>();
        
        // 1. 用户画像
        UserProfile profile = profileMapper.selectByUserId(userId);
        params.put("profile_json", JSON.toJSONString(profile));
        
        // 2. 用户当前技能
        List<UserSkill> userSkills = userSkillMapper.selectByUserId(userId);
        params.put("user_skills", userSkills.stream()
            .map(s -> s.getSkillName() + ":" + s.getLevel())
            .collect(Collectors.joining("、")));
        
        // 3. 目标岗位技能要求
        List<String> jobRequirements = skillMapper.selectRequirementsByJobId(jobId);
        params.put("job_requirements", String.join("、", jobRequirements));
        
        // 4. 目标岗位名称
        JobPosition position = jobPositionMapper.selectById(jobId);
        params.put("job_title", position.getTitle());
        
        // 5. 差距报告（规则计算结果）
        params.put("gap_report", buildGapReportText(userSkills, jobRequirements));
        
        return params;
    }
    
    /**
     * 智能客服：构建 FAQ 知识库上下文（RAG 核心）
     */
    public Map<String, String> buildCustomerServiceContext(String question, String role) {
        Map<String, String> params = new HashMap<>();
        
        // 1. 用户问题
        params.put("question", question);
        
        // 2. FAQ 知识库（全部 FAQ 作为上下文，让 AI 基于 FAQ 回答）
        List<Faq> faqs = faqMapper.selectByRole(role);
        params.put("faq_context", faqs.stream()
            .map(f -> "Q:" + f.getQuestion() + "\nA:" + f.getAnswer())
            .collect(Collectors.joining("\n---\n")));
        
        // 3. 平台岗位简介（让 AI 能介绍平台岗位）
        List<JobPosition> positions = jobPositionMapper.selectAll();
        params.put("positions_intro", positions.stream()
            .map(p -> p.getTitle() + ":" + p.getDirection())
            .collect(Collectors.joining("、")));
        
        return params;
    }
    
    /**
     * 模拟面试：构建岗位技能要求上下文
     */
    public Map<String, String> buildInterviewContext(String position) {
        Map<String, String> params = new HashMap<>();
        
        // 1. 岗位名称
        params.put("position", position);
        
        // 2. 岗位技能要求（让 AI 按技能出题）
        List<String> skills = skillMapper.selectRequirementsByJobTitle(position);
        params.put("position_skills", String.join("、", skills));
        
        // 3. 岗位 JD
        JobPosition jobPosition = jobPositionMapper.selectByTitle(position);
        params.put("job_jd", jobPosition != null ? jobPosition.getDescription() : "");
        
        return params;
    }
}
```

#### 4. Prompt 模板增强（融入数据库上下文）

以职业探索模板为例，展示如何将数据库数据融入 Prompt：

```
你是一位资深的职业规划导师，服务于「AI 智能求职辅导平台」。

## 认知边界
- 仅回答与大学生求职、互联网岗位方向、职业规划相关的问题
- 推荐的岗位必须从下方「平台岗位列表」中选择，不得编造平台不存在的岗位
- 禁止回答政治、宗教、医疗、法律建议等与求职无关的问题

## 输入数据（来自平台数据库）
- 用户画像：{{profile_json}}
- 测评结果摘要：{{assessment_summary}}
- 兴趣偏好：{{interests}}
- 平台岗位列表：{{positions_list}}

## 任务
基于用户画像、测评结果和兴趣偏好，从平台岗位列表中推荐 3-5 个最适合的岗位方向。

## 输出格式（JSON）
{
  "directions": [
    {
      "position": "岗位名称（必须来自平台岗位列表）",
      "matchScore": 85,
      "reason": "推荐理由（结合用户画像和测评结果说明）",
      "growthPath": "成长路径建议"
    }
  ]
}
```

**模板增强说明**：
- 明确「认知边界」段落，限定 AI 回答范围
- 「输入数据」段落标注「来自平台数据库」，让 AI 知道这是真实数据
- 「平台岗位列表」约束 AI 只能从列表中选择，不编造岗位
- 输出格式要求岗位名称「必须来自平台岗位列表」

#### 5. 认知边界的运行时校验

AI 返回结果后，进行认知边界校验，确保不脱离项目范围：

```java
/**
 * AI 结果校验：确保不脱离项目认知边界
 */
public class AiResultValidator {
    
    @Autowired private JobPositionMapper jobPositionMapper;
    @Autowired private SkillMapper skillMapper;
    
    /**
     * 校验职业探索结果：推荐的岗位必须在平台岗位列表中
     */
    public List<CareerDirection> validateCareerDirections(List<CareerDirection> directions) {
        Set<String> platformPositions = jobPositionMapper.selectAllTitles();
        
        return directions.stream()
            .filter(d -> platformPositions.contains(d.getPosition()))  // 过滤掉平台不存在的岗位
            .peek(d -> {
                if (d.getMatchScore() < 0 || d.getMatchScore() > 100) {
                    d.setMatchScore(Math.max(0, Math.min(100, d.getMatchScore())));  // 分数范围校验
                }
            })
            .collect(Collectors.toList());
    }
    
    /**
     * 校验企业推荐结果：技能标签必须在平台技能词典中
     */
    public ProjectParseResult validateProjectParse(ProjectParseResult result) {
        Set<String> platformSkills = skillMapper.selectAllNames();
        
        result.getPositions().forEach(position -> {
            // 过滤掉平台不存在的技能标签
            List<String> validSkills = position.getSkillRequirements().stream()
                .filter(platformSkills::contains)
                .collect(Collectors.toList());
            // 若全部被过滤，补充默认技能
            if (validSkills.isEmpty()) {
                validSkills = List.of("Java");  // 默认技能
            }
            position.setSkillRequirements(validSkills);
        });
        
        return result;
    }
    
    /**
     * 校验智能客服回答：检测是否包含敏感词或超界内容
     */
    public String validateCustomerServiceAnswer(String answer) {
        // 敏感词过滤
        List<String> sensitiveWords = List.of("政治", "宗教", "医疗诊断", "法律建议");
        for (String word : sensitiveWords) {
            if (answer.contains(word)) {
                return "我是求职辅导平台客服助手，主要为您解答平台使用、岗位介绍、学习路径等问题。其他问题建议咨询专业人士。";
            }
        }
        return answer;
    }
}
```

**运行时校验说明**：
- 职业探索：过滤掉平台不存在的岗位，校验分数范围 0-100
- 企业推荐：过滤掉平台技能词典中不存在的技能标签
- 智能客服：敏感词检测，超界则返回引导话术
- 校验在 AI 返回后、返回前端前执行，作为最后一道防线

#### 6. 整体调用流程

```
用户请求
  ↓
1. AiContextBuilder：从数据库查询相关数据，构建上下文参数
  ↓
2. PromptTemplateUtil：加载模板 + 渲染参数（含数据库上下文）
  ↓
3. DeepSeekService.callAPI：System Prompt（认知边界）+ User Prompt（含数据）
  ↓
4. AiResultValidator：校验 AI 返回结果是否在认知边界内
  ↓
5. 返回前端（校验通过）或兜底（校验失败）
```

**设计总结**：
- **三层认知边界保障**：System Prompt 限定 + Prompt 模板约束 + 运行时结果校验
- **数据库上下文关联**：每个功能调用前查数据库，拼接到 Prompt，让 AI 基于真实数据回答
- **专业契合项目**：AI 推荐的岗位、技能、建议均来自项目数据库，不脱离平台范围

---

## 二、各 AI 功能实现与完善

### Q4：AI 职业方向探索（R-004）如何完善？

**现状**：[CareerExplorationServiceImpl.java](file:///d:/HuaweiMoveData/Users/20403/Desktop/软件实训项目/软件实训项目-code/软件实训项目-第一版/backend/src/main/java/com/xuelian/career/service/impl/CareerExplorationServiceImpl.java) 已接入 DeepSeek，但缓存键 `explore:userId` 不随画像更新失效，兜底质量低。

**基于技术选型的实现方案**：

| 改造项 | 方案 | 来源 |
|--------|------|------|
| 流式输出 | 改造为 SSE，逐字返回推荐理由 | D3 已确认 |
| 重试限流 | @Retry + @RateLimiter + @CircuitBreaker | D4 已确认 |
| Prompt 管理 | `career_exploration.txt` 归业务层，可后台热更新 | D7 已确认 |
| 缓存优化 | 缓存键改为 `explore:userId:profileVersion`，画像更新时版本号变更 | 新增 |

**具体实现步骤**：

#### 1. CareerProfile 表增加 version 字段
```sql
ALTER TABLE career_profile ADD COLUMN version INT NOT NULL DEFAULT 1 COMMENT '画像版本号';
```

```java
// 更新画像时递增版本号
@Override
public void updateProfile(Long userId, CareerProfileDTO dto) {
    CareerProfile profile = mapper.selectByUserId(userId);
    // ... 更新字段
    profile.setVersion(profile.getVersion() + 1);  // 版本号递增
    mapper.updateById(profile);
}
```

#### 2. 缓存键优化
```java
@Override
public CareerExplorationVO explore(Long userId) {
    CareerProfile profile = profileMapper.selectByUserId(userId);
    // 缓存键加入画像版本号，画像变更后自动失效
    String cacheKey = "explore:" + userId + ":" + profile.getVersion();
    
    try {
        String response = deepSeekService.callAPIWithCache(
            "你是一位资深的职业规划导师",
            buildPrompt(profile),
            cacheKey
        );
        return parseResult(response);
    } catch (Exception e) {
        return buildFallbackResult(profile);  // 兜底
    }
}
```

**缓存优化说明**：
- 原缓存键 `explore:userId` 不随画像更新失效，画像变更后仍返回旧推荐
- 改为 `explore:{userId}:{version}`，画像更新时 version 递增，缓存键变化，自动失效

#### 3. 新增流式端点
```java
// CareerExplorationController.java 新增
@GetMapping(value = "/explore/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
public Flux<ServerSentEvent<String>> exploreStream(HttpServletRequest request) {
    Long userId = getUserIdFromToken(request);
    CareerProfile profile = profileMapper.selectByUserId(userId);
    
    String template = promptTemplateUtil.loadTemplate("career_exploration");
    String prompt = promptTemplateUtil.renderTemplate(template, buildParams(profile));
    
    return deepSeekService.callAPIStream("你是一位资深的职业规划导师", prompt)
        .map(content -> ServerSentEvent.<String>builder().data(content).build())
        .onErrorResume(e -> Flux.just(ServerSentEvent.<String>builder()
            .data("【AI 服务暂时不可用，已为您生成基础推荐】").build()));
}
```

#### 4. 兜底方案优化
```java
/**
 * 兜底：按测评分数排序岗位（替代固定取前 5 个）
 */
private CareerExplorationVO buildFallbackResult(CareerProfile profile) {
    // 查所有岗位
    List<JobPosition> allPositions = jobPositionMapper.selectAll();
    
    // 按测评分数排序（测评方向匹配岗位方向 +2 分，技能匹配 +1 分）
    List<JobPosition> sorted = allPositions.stream()
        .sorted((a, b) -> Double.compare(
            calculateMatchScore(b, profile),
            calculateMatchScore(a, profile)
        ))
        .limit(5)
        .collect(Collectors.toList());
    
    // 组装返回（分数 70-90 基于匹配度）
    // ...
}

private double calculateMatchScore(JobPosition position, CareerProfile profile) {
    double score = 0;
    if (position.getDirection().equals(profile.getAssessmentDirection())) score += 2;
    score += countSkillMatches(position.getRequiredSkills(), profile.getSkillTags()) * 0.5;
    return score;
}
```

**完善方向**：
- 支持多轮对话（保留历史消息，当前是单轮）
- 推荐理由可流式输出，提升体验

---

### Q5：AI 简历优化（R-008）如何修复？

**现状**：[ResumeServiceImpl.java](file:///d:/HuaweiMoveData/Users/20403/Desktop/软件实训项目/软件实训项目-code/软件实训项目-第一版/backend/src/main/java/com/xuelian/career/service/impl/ResumeServiceImpl.java) 调用了 DeepSeek，但 `resume_text` 参数只传文件 URL，AI 读不到简历内容。**这是 P0 缺陷。**

**基于技术选型的实现方案**：

| 改造项 | 方案 | 来源 |
|--------|------|------|
| 文本提取 | **待选 D1**：POI+PDFBox / Tika / 前端 pdf.js | D1 待选 |
| 重试限流 | @Retry + @RateLimiter + @CircuitBreaker | D4 已确认 |
| Prompt 管理 | `resume_optimize.txt` 归基础层 | D7 已确认 |

**无论 D1 选哪个方案，核心修复一致**：

#### 1. FileUtil 新增文本提取方法（如选方案 A：POI + PDFBox）
```java
/**
 * 提取上传文件的文本内容（支持 PDF / DOCX）
 * @param file 上传的文件
 * @return 提取的纯文本
 */
public String extractText(MultipartFile file) throws IOException {
    String filename = file.getOriginalFilename();
    if (filename == null) throw new BusinessException("文件名不能为空");
    
    String extension = filename.substring(filename.lastIndexOf(".")).toLowerCase();
    
    try (InputStream is = file.getInputStream()) {
        return switch (extension) {
            case ".pdf" -> extractPdfText(is);
            case ".docx" -> extractDocxText(is);
            default -> throw new BusinessException("不支持的文件格式: " + extension + "，仅支持 PDF/DOCX");
        };
    }
}

/**
 * 使用 PDFBox 提取 PDF 文本
 */
private String extractPdfText(InputStream is) throws IOException {
    try (PDDocument document = PDDocument.load(is)) {
        PDFTextStripper stripper = new PDFTextStripper();
        String text = stripper.getText(document);
        if (text == null || text.trim().isEmpty()) {
            throw new BusinessException("PDF 文本提取为空，可能是扫描件，请上传文本型 PDF");
        }
        // 截断超长文本（DeepSeek 输入限制约 32K token，简历按 8000 字截断）
        return text.length() > 8000 ? text.substring(0, 8000) : text;
    }
    // document 自动关闭，避免 OOM
}

/**
 * 使用 POI 提取 DOCX 文本
 */
private String extractDocxText(InputStream is) throws IOException {
    try (XWPFDocument document = new XWPFDocument(is)) {
        XWPFWordExtractor extractor = new XWPFWordExtractor(document);
        String text = extractor.getText();
        if (text == null || text.trim().isEmpty()) {
            throw new BusinessException("DOCX 文本提取为空");
        }
        return text.length() > 8000 ? text.substring(0, 8000) : text;
    }
    // document 自动关闭，避免 OOM
}
```

**实现说明**：
- `try-with-resources` 确保 `PDDocument`/`XWPFDocument` 自动关闭，避免内存泄漏
- PDF 扫描件（图片）无法提取文本，需提示用户上传文本型 PDF
- 文本超 8000 字截断，避免超出 DeepSeek 输入限制

#### 2. ResumeServiceImpl.analyzeResume 改造
```java
@Override
public ResumeAnalysisVO analyzeResume(MultipartFile file, Long userId) {
    // 1. 文件校验（需求 4.4）
    validateFile(file);
    
    // 2. 保存文件（保留原有逻辑）
    String fileUrl = fileStorageService.save(file, userId);
    
    // 3. 【修复核心】提取简历文本
    String resumeText;
    try {
        resumeText = fileUtil.extractText(file);
    } catch (IOException e) {
        log.error("简历文本提取失败: {}", e.getMessage());
        throw new BusinessException("简历文件解析失败，请检查文件格式");
    }
    
    // 4. 组装 Prompt 参数
    Map<String, String> params = new HashMap<>();
    params.put("resume_text", resumeText);  // ✅ 修复：传入实际文本内容
    params.put("job_category", getTargetJobCategory(userId));
    params.put("skill_requirements", getSkillRequirements(userId)); // 修复：传入实际技能要求
    
    // 5. 调用 DeepSeek 分析
    String template = promptTemplateUtil.loadTemplate("resume_optimize");
    String prompt = promptTemplateUtil.renderTemplate(template, params);
    
    try {
        String response = deepSeekService.callAPIWithCache(
            "你是一位资深 HR 和简历优化专家", 
            prompt,
            "resume:" + userId + ":" + resumeText.hashCode()
        );
        return parseAnalysisResult(response);
    } catch (Exception e) {
        log.warn("AI 简历分析失败，走兜底: {}", e.getMessage());
        return buildFallbackResult(resumeText);
    }
}

/**
 * 文件校验：格式 + 大小
 */
private void validateFile(MultipartFile file) {
    String filename = file.getOriginalFilename();
    if (filename == null || (!filename.endsWith(".pdf") && !filename.endsWith(".docx"))) {
        throw new BusinessException("仅支持 PDF 和 DOCX 格式");
    }
    if (file.getSize() > 5 * 1024 * 1024) {
        throw new BusinessException("文件大小不能超过 5MB");
    }
}
```

**改造说明**：
- 核心修复：`resume_text` 从传文件 URL 改为传提取的实际文本
- `skill_requirements` 从固定空字符串改为查用户目标岗位的技能要求
- 文件校验：格式（PDF/DOCX）+ 大小（5MB），符合需求 4.4
- 兜底：AI 失败时基于提取的文本生成基础建议（如「简历文本提取成功，建议补充项目量化成果」）

**D1 选择影响**：
- 选 A（POI+PDFBox）：依赖已有，0.5 天，覆盖 PDF/DOCX ✅ 推荐
- 选 B（Tika）：新增 30MB 依赖，1 天，支持数十种格式
- 选 C（前端 pdf.js）：前后端都改，1.5 天，仅 PDF

**完善方向**：
- 提取的文本超长时（>8000 字），按段落智能截断而非硬截断
- 支持简历图片 OCR（长期）

---

### Q6：AI 模拟面试（R-009）如何完善？

**现状**：[InterviewServiceImpl.java](file:///d:/HuaweiMoveData/Users/20403/Desktop/软件实训项目/软件实训项目-code/软件实训项目-第一版/backend/src/main/java/com/xuelian/career/service/impl/InterviewServiceImpl.java) 已接入 DeepSeek 出题和评估，但**缺少 FOLLOW_UP 追问逻辑**（需求 3.2.8 要求最多 2 轮追问）。模板已定义 FOLLOW_UP 格式但代码未调用。

**基于技术选型的实现方案**：

| 改造项 | 方案 | 来源 |
|--------|------|------|
| 追问逻辑 | 实现 FOLLOW_UP 阶段调用 | 需求 3.2.8 |
| 重试限流 | @Retry + @RateLimiter + @CircuitBreaker | D4 已确认 |
| Prompt 管理 | `mock_interview.txt` 归基础层 | D7 已确认 |
| 流式输出 | 暂不改造（面试是问答式，流式意义中等） | D3 范围外 |

**追问逻辑实现步骤**：

#### 1. Redis 会话结构增加 followUpCount
```java
// 原会话结构
{
  "sessionId": "...",
  "userId": 1,
  "position": "后端开发工程师",
  "currentQuestionIndex": 0,
  "questions": [...],
  "answers": [...],
  "currentStage": "FIRST_QUESTION"
}

// 改造后增加 followUpCount
{
  ...,
  "currentStage": "FIRST_QUESTION",
  "followUpCount": 0,          // 新增：当前题目的追问次数
  "maxFollowUp": 2             // 新增：最多追问 2 轮（需求 3.2.8）
}
```

#### 2. submitAnswer 方法改造（核心）
```java
@Override
public InterviewAnswerVO submitAnswer(String sessionId, String answer) {
    InterviewSession session = getSessionFromRedis(sessionId);
    if (session == null) throw new BusinessException("面试会话不存在或已过期");
    
    // 1. 保存用户回答
    session.getAnswers().add(answer);
    
    // 2. 判断是否需要追问
    InterviewAnswerVO vo = new InterviewAnswerVO();
    
    if (session.getFollowUpCount() < session.getMaxFollowUp()) {
        // 尝试追问：用 FOLLOW_UP 阶段调用 AI
        try {
            String template = promptTemplateUtil.loadTemplate("mock_interview");
            Map<String, String> params = new HashMap<>();
            params.put("current_stage", "FOLLOW_UP");
            params.put("position", session.getPosition());
            params.put("current_question", session.getQuestions().get(session.getCurrentQuestionIndex()));
            params.put("user_answer", answer);
            params.put("follow_up_count", String.valueOf(session.getFollowUpCount()));
            
            String prompt = promptTemplateUtil.renderTemplate(template, params);
            String response = deepSeekService.callAPI("你是一位资深技术面试官", prompt);
            
            // 解析 AI 返回：{action: "FOLLOW_UP" | "NEXT_QUESTION", content: "..."}
            FollowUpResult result = deepSeekService.parseJSONResponse(response, FollowUpResult.class);
            
            if ("FOLLOW_UP".equals(result.getAction())) {
                // AI 决定追问
                session.setFollowUpCount(session.getFollowUpCount() + 1);
                session.setCurrentStage("FOLLOW_UP");
                saveSessionToRedis(session);
                
                vo.setType("FOLLOW_UP");
                vo.setQuestion(result.getContent());
                vo.setFollowUpCount(session.getFollowUpCount());
                return vo;
            }
            // action=NEXT_QUESTION，进入下一题
        } catch (Exception e) {
            log.warn("AI 追问失败，直接进入下一题: {}", e.getMessage());
        }
    }
    
    // 3. 进入下一题（追问次数达上限或 AI 决定下一题）
    session.setFollowUpCount(0);  // 重置追问计数
    session.setCurrentQuestionIndex(session.getCurrentQuestionIndex() + 1);
    
    // 4. 判断是否面试结束
    if (session.getCurrentQuestionIndex() >= session.getQuestions().size()) {
        // 面试结束，生成评估报告
        session.setCurrentStage("EVALUATION");
        saveSessionToRedis(session);
        vo.setType("EVALUATION");
        vo.setReport(generateEvaluation(session));
        return vo;
    }
    
    // 5. 下一题
    session.setCurrentStage("FIRST_QUESTION");
    saveSessionToRedis(session);
    vo.setType("NEXT_QUESTION");
    vo.setQuestion(session.getQuestions().get(session.getCurrentQuestionIndex()));
    vo.setQuestionIndex(session.getCurrentQuestionIndex());
    return vo;
}
```

**改造说明**：
- 用户回答后，先用 FOLLOW_UP 阶段调用 AI，由 AI 决定是否追问
- AI 返回 `action=FOLLOW_UP` 则追问，`followUpCount++`
- AI 返回 `action=NEXT_QUESTION` 或追问次数达 2 次，进入下一题
- 模板 `mock_interview.txt` 的 FOLLOW_UP 输出格式已就绪，无需改模板

#### 3. 题目数量调整
```java
// 原代码：固定 5 道题
// 改造后：5-8 道（需求 3.2.8）
private int calculateQuestionCount(String position) {
    // 基础 5 道，技术岗位 +2 道，管理岗位 +1 道
    int base = 5;
    if (position.contains("后端") || position.contains("前端") || position.contains("算法")) {
        base += 2;
    } else if (position.contains("经理") || position.contains("主管")) {
        base += 1;
    }
    return Math.min(base, 8);  // 上限 8 道
}
```

#### 4. 本地兜底评分优化（替代 Math.random()）
```java
/**
 * 规则评分：回答长度 + 关键词命中
 */
private InterviewEvaluation buildFallbackEvaluation(InterviewSession session) {
    InterviewEvaluation eval = new InterviewEvaluation();
    
    double totalScore = 0;
    List<String> dimensions = List.of("逻辑性", "专业度", "沟通力", "应变力", "岗位匹配度");
    
    for (int i = 0; i < session.getAnswers().size(); i++) {
        String answer = session.getAnswers().get(i);
        String question = session.getQuestions().get(i);
        
        // 回答长度评分（>100 字为合格，>300 字为优秀）
        double lengthScore = Math.min(answer.length() / 3.0, 100);
        
        // 关键词命中评分（问题中的技术词在回答中出现）
        long keywordHits = extractKeywords(question).stream()
            .filter(answer::contains)
            .count();
        double keywordScore = Math.min(keywordHits * 20, 100);
        
        totalScore += (lengthScore * 0.4 + keywordScore * 0.6);
    }
    
    double avgScore = totalScore / session.getAnswers().size();
    eval.setTotalScore(avgScore);
    // 五个维度分数基于总分微调
    eval.setLogicScore(avgScore * 0.95);
    eval.setProfessionalScore(avgScore * 1.05);
    // ... 其他维度
    return eval;
}
```

**其他修复**：
- 题目数量从固定 5 道改为 5-8 道（需求 3.2.8）
- 本地兜底评分用规则评分（回答长度+关键词命中）替代 `Math.random()`

**完善方向**：
- 面试报告支持流式输出（评估报告文字较长）
- 支持语音面试（长期，需求附录 9.3）

---

### Q7：企业项目需求解析与候选人推荐（R-010）如何实现？

**现状**：[EnterpriseServiceImpl.java](file:///d:/HuaweiMoveData/Users/20403/Desktop/软件实训项目/软件实训项目-code/软件实训项目-第一版/backend/src/main/java/com/xuelian/career/service/impl/EnterpriseServiceImpl.java) **完全未接入 AI**，从未调用 DeepSeek，候选人评分用随机数。**这是 P0 严重缺失，企业端唯一 AI 功能。**

**基于技术选型的实现方案**：

| 改造项 | 方案 | 来源 |
|--------|------|------|
| AI 接入 | 注入 DeepSeekService + PromptTemplateUtil，使用 `project_parse.txt` | 新增 |
| 重试限流 | @Retry + @RateLimiter + @CircuitBreaker | D4 已确认 |
| Prompt 管理 | `project_parse.txt` 归基础层 | D7 已确认 |
| HTTP 客户端 | **待选 D2**：RestTemplate（同步）即可 | D2 待选 |

**具体实现步骤**：

#### 1. EnterpriseServiceImpl 注入依赖
```java
@Service
public class EnterpriseServiceImpl implements EnterpriseService {
    @Autowired private DeepSeekService deepSeekService;          // 新增注入
    @Autowired private PromptTemplateUtil promptTemplateUtil;   // 新增注入
    @Autowired private SkillMapper skillMapper;                 // 查技能词典
    @Autowired private UserSkillMapper userSkillMapper;         // 查候选人技能
    @Autowired private RecommendationRecordMapper recordMapper; // 新增：持久化推荐记录
    
    // 原有依赖保留
}
```

#### 2. recommend 方法改造（核心）
```java
@Override
public EnterpriseRecommendVO recommend(EnterpriseRecommendDTO dto) {
    // 1. 参数校验：项目描述不能过短（需求 3.3.1 异常处理）
    if (dto.getProjectDescription() == null || dto.getProjectDescription().length() < 20) {
        throw new BusinessException("项目描述过短，请补充业务场景（至少 20 字）");
    }
    
    // 2. 加载 project_parse.txt 模板（基础层）
    String template = promptTemplateUtil.loadTemplate("project_parse");
    
    // 3. 查技能词典（作为 Prompt 上下文，引导 AI 输出标准技能标签）
    List<String> skillDictionary = skillMapper.selectAllSkillNames();
    
    // 4. 组装 Prompt 参数
    Map<String, String> params = new HashMap<>();
    params.put("project_description", dto.getProjectDescription());
    params.put("skill_dictionary", String.join("、", skillDictionary));
    params.put("filters", dto.getFilters() != null ? dto.getFilters() : "");
    
    String prompt = promptTemplateUtil.renderTemplate(template, params);
    
    // 5. 调用 DeepSeek 解析项目
    ProjectParseResult parseResult;
    try {
        String response = deepSeekService.callAPI(
            "你是一位资深技术架构师和项目经理，擅长根据项目需求拆解岗位和技能要求",
            prompt
        );
        parseResult = deepSeekService.parseJSONResponse(response, ProjectParseResult.class);
        log.info("项目解析成功: {} 个岗位", parseResult.getPositions().size());
    } catch (Exception e) {
        log.warn("AI 项目解析失败，走兜底: {}", e.getMessage());
        parseResult = buildFallbackParseResult(dto.getProjectDescription());
    }
    
    // 6. 根据 AI 解析的技能标签，检索候选人
    List<CandidateMatch> candidates = new ArrayList<>();
    for (ProjectParseResult.Position position : parseResult.getPositions()) {
        // 按技能标签检索候选人画像
        List<UserSkill> matched = userSkillMapper.selectBySkillTags(position.getSkillRequirements());
        
        for (UserSkill us : matched) {
            CandidateMatch candidate = new CandidateMatch();
            candidate.setUserId(us.getUserId());
            candidate.setPosition(position.getTitle());
            
            // 规则评分（替代随机数）：
            // 技能匹配度 50% + 测评结果 30% + 学习完成度 20%
            double skillScore = calculateSkillScore(us, position.getSkillRequirements());
            double assessmentScore = getAssessmentScore(us.getUserId());
            double learningScore = getLearningScore(us.getUserId());
            double totalScore = skillScore * 0.5 + assessmentScore * 0.3 + learningScore * 0.2;
            
            candidate.setSkillScore(skillScore);
            candidate.setAssessmentScore(assessmentScore);
            candidate.setLearningScore(learningScore);
            candidate.setTotalScore(totalScore);
            candidate.setMatchReason(buildMatchReason(us, position));
            candidates.add(candidate);
        }
        
        // 每个岗位取 Top N
        candidates.sort((a, b) -> Double.compare(b.getTotalScore(), a.getTotalScore()));
    }
    
    // 7. 持久化推荐记录
    RecommendationRecord record = new RecommendationRecord();
    record.setEnterpriseId(dto.getEnterpriseId());
    record.setProjectDescription(dto.getProjectDescription());
    record.setParseResult(JSON.toJSONString(parseResult));
    record.setCandidates(JSON.toJSONString(candidates));
    record.setCreatedAt(LocalDateTime.now());
    recordMapper.insert(record);
    
    // 8. 组装返回
    EnterpriseRecommendVO vo = new EnterpriseRecommendVO();
    vo.setSuggestedPositions(parseResult.getPositions());
    vo.setCandidates(candidates.stream().limit(10).collect(Collectors.toList()));
    return vo;
}

/**
 * 规则评分：技能匹配度
 * 匹配的技能数 / 岗位要求的技能数 * 100
 */
private double calculateSkillScore(UserSkill us, List<String> requirements) {
    if (requirements == null || requirements.isEmpty()) return 50;
    long matchedCount = requirements.stream()
        .filter(req -> us.getSkillTags().contains(req))
        .count();
    return (double) matchedCount / requirements.size() * 100;
}
```

**改造说明**：
- 核心修复：从「从未调用 DeepSeek」改为完整 AI 调用链
- 候选人评分从 `Math.random()` 改为规则评分（技能匹配50% + 测评30% + 学习20%）
- 推荐记录持久化到 `recommendation_record` 表，`getHistory` 可查询
- 兜底：AI 失败时用项目关键词匹配岗位模板（`buildFallbackParseResult`）

#### 3. getHistory 方法实现
```java
@Override
public List<RecommendationRecord> getHistory(Long enterpriseId) {
    return recordMapper.selectList(
        new LambdaQueryWrapper<RecommendationRecord>()
            .eq(RecommendationRecord::getEnterpriseId, enterpriseId)
            .orderByDesc(RecommendationRecord::getCreatedAt)
            .last("LIMIT 20")
    );
}
```

#### 4. project_parse.txt 模板（已存在，确认内容）
模板已定义输出 JSON 格式：`positions`（岗位列表，含 title/skillRequirements/headcount），无需修改。

**兜底方案**（AI 不可用时）：
```java
/**
 * 兜底：项目描述关键词匹配岗位模板
 */
private ProjectParseResult buildFallbackParseResult(String description) {
    ProjectParseResult result = new ProjectParseResult();
    List<ProjectParseResult.Position> positions = new ArrayList<>();
    
    // 简单关键词匹配
    if (description.contains("前端") || description.contains("页面") || description.contains("UI")) {
        positions.add(buildPosition("前端开发工程师", List.of("HTML", "CSS", "JavaScript", "Vue"), 2));
    }
    if (description.contains("后端") || description.contains("接口") || description.contains("API")) {
        positions.add(buildPosition("后端开发工程师", List.of("Java", "Spring", "MySQL", "Redis"), 3));
    }
    if (description.contains("算法") || description.contains("推荐") || description.contains("搜索")) {
        positions.add(buildPosition("算法工程师", List.of("Python", "机器学习", "数据结构"), 1));
    }
    // 默认：至少返回一个岗位
    if (positions.isEmpty()) {
        positions.add(buildPosition("软件开发工程师", List.of("Java", "Spring"), 2));
    }
    
    result.setPositions(positions);
    return result;
}
```

**完善方向**：
- 项目解析结果支持流式输出（项目描述较长时）
- 推荐理由可单独调用 AI 生成，减少主流程耗时

---

### Q8：能力差距分析（R-006）如何引入 AI？

**现状**：[GapAnalysisServiceImpl.java](file:///d:/HuaweiMoveData/Users/20403/Desktop/软件实训项目/软件实训项目-code/软件实训项目-第一版/backend/src/main/java/com/xuelian/career/service/impl/GapAnalysisServiceImpl.java) 纯规则实现，未注入 DeepSeekService，`suggestions` 固定为「建议优先提升优先级较高的技能缺口」。

**基于技术选型的实现方案**：

| 改造项 | 方案 | 来源 |
|--------|------|------|
| AI 建议 | 规则计算差距后，调 DeepSeek 生成个性化提升建议 | 新增 |
| 重试限流 | @Retry + @RateLimiter + @CircuitBreaker | D4 已确认 |
| Prompt 管理 | 新增 `gap_analysis.txt`，归业务层 | D7 已确认 |

**具体实现步骤**：

#### 1. 新建 prompts/gap_analysis.txt 模板（业务层）
```
你是一位职业能力提升顾问，擅长为求职者制定个性化的技能提升方案。

## 输入
- 用户画像：{{profile_json}}
- 差距报告：{{gap_report}}
- 目标岗位：{{job_title}}

## 任务
根据差距报告，生成个性化提升建议。要求：
1. 针对每个严重不足的技能，给出具体的学习建议和资源推荐
2. 按优先级排序（先提升对岗位最关键的技能）
3. 给出预计提升周期

## 输出格式（JSON）
{
  "suggestions": ["建议1", "建议2", "建议3"],
  "prioritySkills": [
    {
      "skill": "Java",
      "currentLevel": "未掌握",
      "targetLevel": "熟练",
      "reason": "Java 是后端开发的核心语言，岗位要求熟练掌握",
      "resource": "推荐《Java 核心技术》+ LeetCode 刷题",
      "estimatedHours": 80
    }
  ],
  "estimatedTime": "预计需要 2-3 个月系统提升",
  "learningPath": "建议学习顺序：Java基础 → Spring框架 → 项目实战"
}
```

**模板说明**：
- `profile_json`：用户画像 JSON（专业、兴趣、测评结果）
- `gap_report`：规则计算的差距报告（哪些技能不足、差距程度）
- `job_title`：目标岗位名称
- 输出 JSON 含 `prioritySkills`（优先技能）+ `learningPath`（学习路径），比固定文案更有价值

#### 2. GapAnalysisServiceImpl 注入依赖并改造
```java
@Service
public class GapAnalysisServiceImpl implements GapAnalysisService {
    @Autowired private DeepSeekService deepSeekService;          // 新增注入
    @Autowired private PromptTemplateUtil promptTemplateUtil;   // 新增注入
    
    // 原有依赖保留（userSkillMapper、jobPositionMapper 等）
    
    @Override
    public GapAnalysisVO analyze(Long userId, Long jobId) {
        // 1. 规则计算差距（保留现有逻辑）
        GapAnalysisVO result = calculateGapByRule(userId, jobId);
        
        // 2. 组装 AI 建议的 Prompt
        Map<String, String> params = new HashMap<>();
        params.put("profile_json", getProfileJson(userId));
        params.put("gap_report", buildGapReportText(result));
        params.put("job_title", getJobTitle(jobId));
        
        String template = promptTemplateUtil.loadTemplate("gap_analysis");
        String prompt = promptTemplateUtil.renderTemplate(template, params);
        
        // 3. 调用 DeepSeek 生成个性化建议
        try {
            String response = deepSeekService.callAPIWithCache(
                "你是一位职业能力提升顾问",
                prompt,
                "gap:" + userId + ":" + jobId + ":" + result.hashCode()
            );
            GapSuggestion suggestion = deepSeekService.parseJSONResponse(response, GapSuggestion.class);
            // 用 AI 建议替换固定文案
            result.setSuggestions(suggestion.getSuggestions());
            result.setPrioritySkills(suggestion.getPrioritySkills());
            result.setEstimatedTime(suggestion.getEstimatedTime());
            result.setLearningPath(suggestion.getLearningPath());
        } catch (Exception e) {
            log.warn("AI 建议生成失败，使用固定文案兜底: {}", e.getMessage());
            // 保留规则计算的 suggestions（固定文案兜底）
            result.setSuggestions(List.of("建议优先提升优先级较高的技能缺口"));
        }
        
        return result;
    }
    
    /**
     * 规则计算差距（保留现有逻辑，不改动）
     */
    private GapAnalysisVO calculateGapByRule(Long userId, Long jobId) {
        // 现有规则计算代码...
    }
}
```

**改造说明**：
- 规则计算差距保留（现有逻辑不动），仅增加 AI 生成建议环节
- AI 成功：用个性化建议替换固定文案
- AI 失败：保留固定文案兜底，不影响核心功能
- `gap_analysis.txt` 归业务层，可后台热更新调整建议风格

**完善方向**：
- 用户技能等级判断从二值（掌握/未掌握）改为四档（了解/掌握/熟练/精通）
- 建议关联学习路径，直接生成学习任务（与 R-007 联动）

---

### Q9：智能客服（R-014）如何完善？

**现状**：[CustomerServiceServiceImpl.java](file:///d:/HuaweiMoveData/Users/20403/Desktop/软件实训项目/软件实训项目-code/软件实训项目-code/软件实训项目-第一版/backend/src/main/java/com/xuelian/career/service/impl/CustomerServiceServiceImpl.java) 已实现三层降级（FAQ 关键词 → DeepSeek → 固定回答），是项目中最接近 RAG 的实现。

**基于技术选型的实现方案**：

| 改造项 | 方案 | 来源 |
|--------|------|------|
| 流式输出 | 改造为 SSE，逐字返回回答 | D3 已确认 |
| 重试限流 | @Retry + @RateLimiter + @CircuitBreaker | D4 已确认 |
| Prompt 管理 | `customer_service.txt` 归业务层，可后台热更新 | D7 已确认 |
| FAQ 匹配 | 用 LIKE + 关键词权重打分替代 contains | 新增 |

**具体实现步骤**：

#### 1. 新增流式 chat 方法
```java
// CustomerServiceService 接口新增
Flux<String> chatStream(String question, String role);

// CustomerServiceServiceImpl 实现
@Override
public Flux<String> chatStream(String question, String role) {
    // 1. 先尝试 FAQ 匹配（非流式，命中则包装为单元素 Flux）
    Faq matchedFaq = matchFaq(question);
    if (matchedFaq != null) {
        return Flux.just(matchedFaq.getAnswer());
    }
    
    // 2. FAQ 未命中，流式调用 DeepSeek
    String template = promptTemplateUtil.loadTemplate("customer_service");
    Map<String, String> params = new HashMap<>();
    params.put("question", question);
    params.put("faq_context", loadFaqContext(role));  // 全部 FAQ 作为上下文
    params.put("role", role);
    
    String prompt = promptTemplateUtil.renderTemplate(template, params);
    return deepSeekService.callAPIStream("你是一位友好的平台客服助手", prompt);
}
```

#### 2. FAQ 匹配优化（替代 contains）
```java
/**
 * FAQ 匹配：关键词命中数 + 分类权重打分
 * 替代原有的首个 contains 命中即返回
 */
private Faq matchFaq(String question) {
    List<Faq> allFaqs = faqMapper.selectAll();
    Faq bestMatch = null;
    double bestScore = 0;
    
    for (Faq faq : allFaqs) {
        double score = calculateFaqScore(question, faq);
        if (score > bestScore) {
            bestScore = score;
            bestMatch = faq;
        }
    }
    
    // 阈值：得分 > 0.5 才认为匹配
    return bestScore > 0.5 ? bestMatch : null;
}

/**
 * FAQ 评分：关键词命中数 / 关键词总数 * 分类权重
 */
private double calculateFaqScore(String question, Faq faq) {
    String[] keywords = faq.getKeywords().split(",");
    long hitCount = 0;
    for (String kw : keywords) {
        if (question.contains(kw.trim())) hitCount++;
    }
    double hitRate = (double) hitCount / keywords.length;
    // 分类权重：账户类 1.0，岗位类 0.9，学习类 0.8
    double categoryWeight = getCategoryWeight(faq.getCategory());
    return hitRate * categoryWeight;
}
```

**匹配优化说明**：
- 原实现：`question.contains(keyword)` 首个命中即返回，精度低
- 改造后：所有 FAQ 打分，取最高分且超阈值的，提升匹配准确率
- 分类权重避免低优先级 FAQ 抢占高优先级

#### 3. Controller 新增流式端点
```java
@GetMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
public Flux<ServerSentEvent<String>> chatStream(@RequestParam String question,
                                                 @RequestParam(required = false, defaultValue = "STUDENT") String role) {
    return customerServiceService.chatStream(question, role)
        .map(content -> ServerSentEvent.<String>builder().data(content).build())
        .onErrorResume(e -> Flux.just(ServerSentEvent.<String>builder()
            .data("【AI 服务暂时不可用，建议联系平台管理员】").build()));
}
```

#### 4. 缓存键优化
```java
// 原缓存键：cs:{role}:{hash(question)}
// 优化：增加 FAQ 命中标记，FAQ 命中的不缓存（直接返回）
// 仅 AI 回答缓存
String cacheKey = "cs:" + role + ":" + DigestUtils.sha256Hex(question);
// TTL 1800 秒保持不变
```

**完善方向**：
- 支持多轮对话（保留历史消息，当前是单轮）
- 向量检索 FAQ（D5 长期演进）

---

### Q9.5：参考方案整合与最终技术选择

**背景**：结合外部参考建议（含技术栈推荐、企业端 DeepSeek 解析实现指南、避坑建议），与本项目现状（已有 SpringBoot 3 + Vue 3 + MyBatis-Plus + MySQL 8 + Redis 技术栈，3 人 30 天周期）进行整合，做出最终技术选择。

#### 1. 技术栈方案对比与选择

| 维度 | 方案一：企业主流稳健栈（参考推荐） | 方案二：Python 敏捷 AI 栈（参考备选） | 本项目现状 |
|------|-------------------------------------|---------------------------------------|------------|
| 前端 | Vue 3 + Vite + Element Plus + ECharts | — | ✅ 已采用 |
| 后端 | Spring Boot 3 + MyBatis-Plus + MySQL 8 + Redis | FastAPI + Python | ✅ 已采用方案一 |
| AI 接入 | Spring AI 或 LangChain4j | LangChain | RestTemplate 直连 DeepSeek |
| 特点 | 开发成本低、生态丰富、标准化高 | 适合深耕 Agent 协作，开发效率高 | — |
| 迁移成本 | 低（仅需补 AI 接入层） | 高（需重写后端） | — |

**最终选择：方案一（企业主流稳健栈）+ RestTemplate 直连**

**选择理由**：
1. 项目已采用 SpringBoot 3 + Vue 3 技术栈，方案一零迁移成本
2. 方案二需重写整个后端，3 人 30 天不现实
3. AI 接入层：参考建议 Spring AI 或 LangChain4j，但考虑学习成本和稳定性，**选择 RestTemplate 直连 DeepSeek API**（已在 D2 决策）
4. Spring AI/LangChain4j 作为长期演进选项，30 天内不引入

#### 2. 企业端 DeepSeek 结构化解析实现（整合参考指南）

参考建议的 JSON 强制输出、temperature 调优等关键点，整合到企业推荐实现：

##### 2.1 数据结构化（ProjectAnalysisResult 实体）
```java
/**
 * 企业项目 AI 解析结果（结构化）
 * 整合参考建议：含 techStack、responsibilities、implicitSkills
 */
@Data
public class ProjectAnalysisResult {
    private List<Position> positions;
    
    @Data
    public static class Position {
        private String title;                    // 岗位名称
        private List<String> techStack;          // 技术栈（显性技能）
        private List<String> responsibilities;   // 岗位职责
        private List<String> implicitSkills;     // 隐性技能要求（沟通、协作等）
        private Integer headcount;               // 人数建议
    }
}
```

**设计说明**：相比原 `ProjectParseResult`，新增 `techStack`（技术栈）、`responsibilities`（职责）、`implicitSkills`（隐性技能），深度提取项目需求。

##### 2.2 API 集成关键点（DeepSeek 请求参数优化）
```java
@Override
public Flux<String> callAPIForJson(String systemPrompt, String userPrompt) {
    Map<String, Object> requestBody = new LinkedHashMap<>();
    requestBody.put("model", deepSeekConfig.getModel());
    requestBody.put("stream", false);
    
    // 【关键优化 1】强制 JSON 输出格式（参考建议）
    requestBody.put("response_format", Map.of("type", "json_object"));
    
    // 【关键优化 2】temperature 0.3 确保输出稳定性（参考建议）
    requestBody.put("temperature", 0.3);
    
    // 【关键优化 3】max_tokens 足够大，防 JSON 截断（参考建议）
    requestBody.put("max_tokens", 4096);
    
    List<Map<String, String>> messages = new ArrayList<>();
    messages.add(Map.of("role", "system", "content", systemPrompt));
    messages.add(Map.of("role", "user", "content", userPrompt));
    requestBody.put("messages", messages);
    
    // 调用逻辑...
}
```

**参数优化说明**：
- `response_format: {"type": "json_object"}`：DeepSeek 支持 JSON 模式，强制输出合法 JSON，避免解析失败
- `temperature: 0.3`：低温度确保输出稳定，企业解析场景需要确定性而非创造性
- `max_tokens: 4096`：足够大避免 JSON 字符串被截断导致解析失败

##### 2.3 Prompt 设计（强制 JSON + 结构示例）
```
你是一位资深技术架构师，服务于「AI 智能求职辅导平台」。

## 认知边界
仅根据企业输入的项目需求，解析所需岗位、技能标签和人数建议。
技能标签必须从平台技能词典中选择，不得自行编造。

## 输入数据（来自平台数据库）
- 项目描述：{{project_description}}
- 平台技能词典：{{skill_dictionary}}
- 平台岗位模板：{{position_templates}}

## 任务
深度解析项目需求，提取技术栈、岗位职责和隐性技能要求。

## 输出格式（严格 JSON，不得包含 markdown 代码块标记）
{
  "positions": [
    {
      "title": "后端开发工程师",
      "techStack": ["Java", "Spring Boot", "MySQL"],
      "responsibilities": ["设计 RESTful API", "数据库建模", "性能优化"],
      "implicitSkills": ["系统设计能力", "问题排查能力"],
      "headcount": 3
    }
  ]
}
```

#### 3. 功能完善方向整合（参考建议）

参考建议的四个功能完善方向，与本项目执行路线图对应关系：

| 参考建议 | 本项目对应 | 优先级 | 状态 |
|----------|------------|--------|------|
| 企业端数据持久化（修复 getHistory） | Q7 步骤 3：recommendation_record 表持久化 | P0 | 已纳入第一批 |
| 企业端 AI 解析能力提升（关键词→语义解析） | Q7 步骤 2：DeepSeek 调用链 | P0 | 已纳入第一批 |
| AI 评分算法化（面试兜底评分） | Q6 步骤 4：规则评分替代 Math.random() | P1 | 已纳入第二批 |
| 系统健壮性加固（自动重试+异常处理） | Q1：Resilience4j 重试限流熔断 | P1 | 已纳入第二批 |

**业务闭环与多端延伸（参考建议，本项目暂不纳入 30 天范围）**：
- 招聘全流程闭环（邀约、沟通、面试日程、录用看板）：超 30 天范围，列为长期演进
- 微信小程序端：超 30 天范围，列为长期演进
- 高校管理后台（就业能力分布大盘）：超 30 天范围，列为长期演进

#### 4. 避坑建议整合

##### 4.1 砍掉沉浸式语音 AI（参考建议，采纳）
```
决策：30 天内不实现语音模拟面试和语音客服
理由：
1. 团队无 WebSocket/WebRTC 经验，学习成本高
2. 30 天周期紧迫，应集中精力打磨核心业务闭环
3. 语音功能涉及 ASR（语音识别）+ TTS（语音合成），依赖第三方服务
影响需求：需求附录 9.3「语音模拟面试」标注为长期演进，非 V1.0 强制
```

##### 4.2 「对讲机」式异步音频（参考建议，列为长期方案）
```
如后续确需语音功能，采用「对讲机」式交互：
- 前端录制音频文件 → 上传到后端 → REST API 调用 ASR → 文本送 AI → TTS 生成音频 → 返回
- 避免 WebSocket/WebRTC 流媒体协议的复杂性
- 异步处理，响应时间可接受（5-10 秒）
状态：长期演进，30 天内不实现
```

##### 4.3 本地化部署建议（参考建议，不采纳）
```
参考建议：若必须本地化，用 Ollama 部署 1.5B/3B 量化模型
本项目决策：不本地化部署，坚持用 DeepSeek 云端 API
理由：
1. 服务器仅 2 核 4G，本地模型响应慢且易死机
2. 云端 API 质量高、稳定、免运维
3. DeepSeek API 成本可控（免费档位够演示）
```

#### 5. 最终技术选择汇总

| 类别 | 最终选择 | 参考建议 | 采纳情况 |
|------|----------|----------|----------|
| 技术栈 | SpringBoot 3 + Vue 3（已有） | 方案一企业主流稳健栈 | ✅ 采纳方案一 |
| AI 接入 | RestTemplate 直连 DeepSeek | Spring AI / LangChain4j | ⚠️ 简化为 RestTemplate（长期演进考虑 Spring AI） |
| JSON 输出 | response_format: json_object | response_format: json_object | ✅ 采纳 |
| temperature | 0.3（解析类）/ 0.7（对话类） | 0.3 | ✅ 采纳（区分场景） |
| 重试限流 | Resilience4j | 自动重试机制 | ✅ 采纳 |
| 异常处理 | 全局异常处理 + 不泄露堆栈 | 避免堆栈泄露前端 | ✅ 采纳 |
| 语音 AI | 30 天内不实现 | 砍掉沉浸式语音 | ✅ 采纳 |
| 本地化部署 | 不本地化，用云端 API | Ollama 1.5B/3B | ❌ 不采纳（坚持云端） |
| 业务闭环 | 30 天内仅核心链路 | 招聘全流程闭环 | ⚠️ 列为长期演进 |
| 移动端 | 30 天内不实现 | 微信小程序 | ⚠️ 列为长期演进 |

**整合结论**：参考建议与本项目技术选型高度契合，核心建议（JSON 强制输出、temperature 调优、砍语音、云端 API）均已采纳。差异点在于 AI 接入层选择 RestTemplate 而非 Spring AI/LangChain4j，基于稳定性和学习成本考量。

---

## 三、执行路线图

### Q10：整体执行顺序如何安排？

**按 P0-P3 四级优先级 + 两阶段推进**（整合评估反馈）：

#### P0 必须补充（第一阶段・第一批，核心缺陷修复，约 3.5 天）

| 任务 | 涉及决策 | 工作量 | 验证方式 |
|------|----------|--------|----------|
| 修复 R-008 简历优化文本提取（PDF/DOCX） | D1 POI+PDFBox | 0.5 天 | 上传 PDF，验证 `resume_text` 为正文 |
| 修复 R-010 企业推荐真正接入 DeepSeek（项目解析 + MySQL 关键词匹配候选人） | D2 RestTemplate | 1.5 天 | 输入项目描述，验证岗位/技能与描述相关 |
| 企业推荐结果持久化（`recommendation_record` 表） | — | 0.5 天 | `getHistory` 接口可查询历史推荐 |
| 配置安全收尾（移除硬编码 API Key） | — | 0.5 天 | application-dev.yml 无明文 Key |
| AI 输出 JSON 格式校验和兜底（`response_format: json_object` + 解析失败兜底） | D6 | 0.5 天 | AI 返回非法 JSON 时走兜底，不报 500 |

#### P1 建议补充（第一阶段・第二批，需求完整性与稳定性，约 5.5 天）

| 任务 | 涉及决策 | 工作量 | 验证方式 |
|------|----------|--------|----------|
| R-009 模拟面试 FOLLOW_UP 追问状态机（Q18） | D7 + Q18 | 1.5 天 | 状态流转正确，追问最多 2 次，总题数 5-8 |
| R-006 差距分析接入 AI 个性化建议 | D7 | 1 天 | `suggestions` 非固定文案，与差距内容相关 |
| DeepSeek 基础服务改造（Resilience4j 重试/限流/熔断 + OpenAI 兼容协议封装） | D4 + D6 | 1 天 | `ai_call_log` 出现熔断/限流记录 |
| 企业推荐可解释评分（Q19：结构化评分 + AI 推荐理由 + 风险提示） | Q19 | 1 天 | 候选人卡片展示匹配/缺失技能、推荐理由 |
| Prompt 版本管理（Q14：version 字段 + 状态流转 + 回滚） | Q14 | 1 天 | 可新建版本、回滚到旧版本 |

#### P2 体验增强（第一阶段・第三批，约 5.5 天）

| 任务 | 涉及决策 | 工作量 | 验证方式 |
|------|----------|--------|----------|
| R-014 智能客服 SSE 流式 | D2/D3 WebClient | 0.5 天 | 首字响应 2-3 秒 |
| R-004 职业探索 SSE 流式 | D2/D3 WebClient | 0.5 天 | 首字响应 2-3 秒 |
| Prompt 分层混合管理（Q3：DB 覆盖 + 文件基准） | D7 | 1 天 | 后台编辑模板后热更新生效 |
| AI 调用监控看板（Q13：质量反馈 + ECharts 看板） | Q13 | 1.5 天 | 管理端可查看各场景调用数、好评率、平均评分 |
| 用户反馈机制（Q13：各场景反馈入口） | Q13 | 0.5 天 | 用户可对 AI 结果点有帮助/没帮助 |
| FAQ 知识库管理（Q20：CRUD + 未命中收集） | Q20 | 1 天 | 管理端可维护 FAQ，未命中问题可转 FAQ |
| AI 调用配额控制（Q15：Redis 计数器 + MySQL 汇总） | Q15 | 0.5 天 | 超限用户收到友好提示 |

#### P3 长期演进（第二阶段，演示后迭代，约 8 天）

| 任务 | 涉及决策 | 工作量 | 验证方式 |
|------|----------|--------|----------|
| D5 PG 容器部署 + embedding_store 表 + HNSW 索引 | D5 | 1 天 | PG 中可查询向量数据，HNSW 索引存在 |
| Embedding 服务接入（DeepSeek/硅基流动 Embedding API） | D5 | 1 天 | 简历/FAQ 文本可生成 1536 维向量 |
| R-010 企业候选人推荐叠加 PG 向量重排 | D5 + Q19 | 1.5 天 | 推荐结果语义相关度提升 |
| R-014 客服 FAQ 匹配叠加 PG 向量重排 | D5 + Q20 | 1 天 | 同义词/近义词 FAQ 命中率提升 |
| 双库数据同步（异步事件 + 定时补偿） | D5 | 1 天 | 简历入库后 PG 向量同步成功 |
| D6 backup 启用（接入通义千问，主熔断自动切备） | D6 | 1 天 | 主模型熔断后备用模型正常返回 |
| AI 输出安全过滤（Q16：敏感词 + 场景白名单 + 隐私脱敏） | Q16 | 1 天 | 敏感词被过滤，客服不答无关问题 |
| 简历优化版本对比（Q17：历史记录 + Diff 展示） | Q17 | 0.5 天 | 可查看同一简历多次优化历史并对比 |

**两阶段总计**：
- 第一阶段（P0+P1+P2）：约 14.5 天，3 人协同约 5 天可完成
- 第二阶段（P3）：约 8 天，3 人协同约 3 天可完成
- 合计约 8 天（3 人并行），符合 30 天开发周期（含调试、联调、文档缓冲）

**关键说明**：
- P0 必须在第一阶段完成，否则核心功能不可演示
- P1 是答辩加分项，建议第一阶段完成
- P2 是体验优化，视时间灵活调整
- P3 是长期演进，演示后再迭代

**工程闭环最简落地（与第六章对齐）**：

上述 P1/P2 中的 4 类工程闭环，按第六章「收敛方案」采用最简实现，合计仅 1.5 天工作量：

| 闭环 | 优先级 | 落地批次 | 最简实现 | 工作量 | 增强版 |
|------|--------|----------|----------|--------|--------|
| AI 调用额度控制 | P1 | 第一阶段・第二批 | Redis 计数器（不建持久表） | 0.5 天 | Q15（+0.5 天） |
| Prompt 版本管理 | P1/P2 | 第一阶段・第二批 | version+is_active+回滚（不做灰度） | 0.5 天 | Q14（+0.5 天） |
| RAG 分阶段边界 | P1 | 第一阶段不做 | 文档明确边界，第一阶段 MySQL 关键词 | 0 天 | D5（第二阶段 2 场景，+4.5 天） |
| AI 质量反馈闭环 | P2 | 第一阶段・第三批 | `ai_feedback` 单表（不做看板） | 0.5 天 | Q13（+1 天） |

**收敛原则**：4 类闭环先用最简版落地，增强版（ECharts 看板、灰度分流、持久化统计）按演示反馈再决定是否引入。RAG 向量检索第二阶段严格限制 2 场景（客服 FAQ + 企业候选人），不扩大范围。详见 [第六章 工程闭环收敛方案](#六工程闭环收敛方案小团队最简落地)。

---

### Q11：最终技术决策汇总与执行影响

**全部 7 项决策已确认，下表为最终选择及其对执行的影响**：

| 决策 | 最终选择 | 影响范围 | 执行阶段 | 对执行的影响 |
|------|----------|----------|----------|--------------|
| D1 简历提取 | A. POI+PDFBox | R-008 P0 修复 | 第一阶段 | 复用 pom.xml 已有依赖，0.5 天完成；新增 `FileUtil.extractText()` 工具方法 |
| D2 HTTP 客户端 | A. RestTemplate + WebClient 流式 | 所有 AI 调用 | 第一阶段 | 存量同步接口零改动；新增 `WebClient` Bean + `callAPIStream()` 方法，仅用于 SSE 接口 |
| D3 流式输出 | A. SSE（客服+职业探索） | 用户体验 | 第一阶段 | 新增 `Flux<ServerSentEvent>` 返回类型 + 前端 `fetch+ReadableStream`；Nginx 加 `proxy_buffering off` |
| D4 重试限流 | A. Resilience4j | 稳定性 | 第一阶段 | 新增依赖 + yml 配置 + `@Retry/@RateLimiter/@CircuitBreaker` 注解 + `callAPIFallback` 兜底方法 |
| D5 向量检索 | B. PGVector（混合检索） | 推荐质量 | 第二阶段 | 第一阶段 R-010/R-014 暂用 MySQL 关键词；第二阶段新增 PG 容器 + embedding_store 表 + Embedding API 调用 + 双库异步同步 |
| D6 多模型兜底 | A. DeepSeek 单一+规则 | 可用性 | 第一阶段 | 第一阶段零改动（规则兜底已满足需求 4.4）；配置层预留 `backup` 段，第二阶段按需开启 |
| D7 Prompt 管理 | C. 分层混合 | 可维护性 | 第一阶段 | 新增 `prompt_template` 表 + `PromptTemplateMapper` + `loadTemplate` 改造（DB>文件）+ 管理后台 CRUD 接口 |

**第一阶段（30 天内）执行范围**：D1/D2/D3/D4/D6/D7 全部落地，D5 暂用 MySQL 关键词检索
**第二阶段（演示后）执行范围**：D5 PGVector 启用（R-010 候选人推荐 + R-014 FAQ 匹配）+ D6 backup 启用

**关键说明**：
- D5 不阻塞第一阶段交付：R-010 企业推荐 P0 修复先实现 DeepSeek 项目解析 + MySQL 关键词匹配候选人，演示可用；第二阶段再叠加 PG 向量重排提升精度
- D6 第一阶段零改动：现有规则兜底已满足需求 4.4「AI 不可用时降级方案」，OpenAI 兼容协议封装在 D4 改造时一并完成（`buildRequestBody` 统一方法）
- D5/D6 的配置层预留（`backup.enabled=false`、PG 数据源）在第一阶段就写入 yml，第二阶段仅需开关切换，零代码改动

---

### Q12：如何验证 AI 功能完善效果？

**通用验证**（基于 `ai_call_log` 表）：
- 每个修复后的 AI 功能，`ai_call_log` 表应出现对应场景的 `SUCCESS` 记录
- 熔断/限流触发时，记录 `circuit_state` 状态
- 降级率（FALLBACK/总调用）应低于 10%

**各功能专项验证**：

| 功能 | 验证方法 |
|------|----------|
| R-008 简历优化 | 上传真实 PDF，验证 `resume_text` 为正文内容（非文件 URL）；AI 返回的 issues 与简历内容相关 |
| R-010 企业推荐 | 输入「开发一个在线商城，包含用户端、商家后台、推荐引擎」，验证返回岗位包含前端/后端/算法，技能包含对应技术栈（非固定 Java） |
| R-009 模拟面试 | 回答后验证可能出现追问（如「你刚才提到的 XX，能具体说说吗」），而非直接下一题 |
| R-006 差距分析 | `suggestions` 为个性化建议（非固定文案），与差距报告内容相关 |
| R-014 智能客服 | 流式输出首字 2-3 秒；FAQ 匹配按权重排序 |
| R-004 职业探索 | 流式输出首字 2-3 秒；画像更新后缓存失效，推荐结果变化 |

---

## 四、AI 功能增强与运营闭环（基于评估反馈补充）

> **章节定位**：前述 Q1-Q12 覆盖了主线 AI 功能的实现改造与技术选型，但偏「实现改造清单」。本章节补充「数据闭环、评估体系、成本控制、安全合规、运营管理、AI 质量验证」六类内容，将 AI 能力从「能调用」升级为「可评估、可控制、可运营」。
>
> **与既有内容的关系**：Q14 增强 Q3（Prompt 管理）、Q17 增强 Q5（简历优化）、Q18 增强 Q6（模拟面试）、Q19 增强 Q7（企业推荐）、Q20 增强 Q9（智能客服）；Q13/Q15/Q16 为全新补充。

### Q13：AI 结果质量如何评估与人工校验？

**现状**：`ai_call_log` 表仅记录调用成功/失败/降级，缺少「AI 输出质量是否靠谱」的评估维度，Prompt 优化只能靠主观调参。

**补充方案**：构建「用户反馈 + HR 标记 + 管理端看板」三位一体质量评估体系。

#### 1. 数据库扩展

```sql
-- ai_call_log 新增质量字段（ALTER TABLE）
ALTER TABLE ai_call_log ADD COLUMN quality_score TINYINT DEFAULT NULL COMMENT 'AI 输出质量评分 1-5（用户反馈）';
ALTER TABLE ai_call_log ADD COLUMN feedback_type VARCHAR(16) DEFAULT NULL COMMENT '反馈类型：HELPFUL/UNHELPFUL';
ALTER TABLE ai_call_log ADD COLUMN feedback_content TEXT DEFAULT NULL COMMENT '反馈内容（可选）';
ALTER TABLE ai_call_log ADD COLUMN hr_marked TINYINT DEFAULT 0 COMMENT 'HR 是否标记 0/1（仅企业推荐场景）';
ALTER TABLE ai_call_log ADD COLUMN hr_mark_result VARCHAR(16) DEFAULT NULL COMMENT 'HR 标记结果：ACCURATE/INACCURATE';

-- 新增 AI 反馈汇总表（管理端看板用）
CREATE TABLE ai_feedback_summary (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    scene       VARCHAR(32) NOT NULL COMMENT 'AI 场景：CAREER_EXPLORE/RESUME/INTERVIEW/ENTERPRISE/CUSTOMER_SERVICE',
    date        DATE NOT NULL,
    total_calls INT DEFAULT 0,
    helpful_count INT DEFAULT 0,
    unhelpful_count INT DEFAULT 0,
    avg_quality_score DECIMAL(3,1) DEFAULT NULL,
    hr_accurate_count INT DEFAULT 0,
    hr_inaccurate_count INT DEFAULT 0,
    UNIQUE KEY uk_scene_date (scene, date)
) COMMENT='AI 反馈日汇总表';
```

**表设计说明**：
- `quality_score`：1-5 分制，用户主动评分（前端展示星级组件）
- `feedback_type`：二值反馈（有帮助/没帮助），降低用户反馈门槛
- `hr_marked`/`hr_mark_result`：仅企业推荐场景，HR 标记推荐是否准确
- `ai_feedback_summary`：按场景+日期汇总，管理端看板数据源，定时任务每日凌晨生成

#### 2. 各场景反馈入口设计

| AI 场景 | 反馈入口 | 反馈形式 | 落库字段 |
|---------|----------|----------|----------|
| R-004 职业探索 | 推荐结果卡片底部 | 「有帮助👍 / 没帮助👎」 | `feedback_type` |
| R-008 简历优化 | 优化建议列表底部 | 1-5 星评分 + 可选文字 | `quality_score` + `feedback_content` |
| R-009 模拟面试 | 面试报告页底部 | 1-5 星评分 | `quality_score` |
| R-010 企业推荐 | 候选人卡片操作栏 | HR 标记「推荐准确 / 不准确」 | `hr_marked=1` + `hr_mark_result` |
| R-014 智能客服 | AI 回答消息底部 | 「有帮助👍 / 没帮助👎」 | `feedback_type` |

#### 3. 管理端 AI 质量看板

```java
/**
 * AI 质量看板 Controller
 * 提供 AI 调用与反馈的可视化统计
 */
@RestController
@RequestMapping("/api/admin/ai-quality")
public class AiQualityController {

    /**
     * 查询 AI 质量概览（按场景分组）
     * @param startDate 开始日期
     * @param endDate   结束日期
     * @return 各场景的调用数、好评率、平均评分、HR 准确率
     */
    @GetMapping("/overview")
    public R<List<AiQualityVO>> getOverview(
            @RequestParam LocalDate startDate,
            @RequestParam LocalDate endDate) {
        return R.success(aiFeedbackSummaryMapper.selectOverview(startDate, endDate));
    }

    /**
     * 查询低质量 AI 输出列表（quality_score <= 2 或 feedback_type=UNHELPFUL）
     * 用于 Prompt 优化定位问题样本
     */
    @GetMapping("/low-quality")
    public R<IPage<AiCallLog>> getLowQuality(
            @RequestParam(required = false) String scene,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return R.success(aiCallLogMapper.selectLowQuality(scene, page, size));
    }
}
```

**看板功能说明**：
- 概览页：ECharts 展示各场景调用数、好评率、平均评分趋势（参考评估建议「中等方案 MySQL + ECharts」）
- 低质量列表：筛选 `quality_score <= 2` 或 `UNHELPFUL` 的记录，展示用户问题、AI 回答、反馈内容，作为 Prompt 优化依据
- HR 准确率：企业推荐专项，展示 HR 标记准确/不准确比例

#### 4. 基于反馈的 Prompt 优化闭环

```
用户反馈 → 低质量样本收集 → 管理员分析共性 → 调整 Prompt（Q14 版本管理）
    ↑                                                        ↓
    └──────── 新版本灰度发布 → 对比质量评分 ←────────────────┘
```

**闭环说明**：Prompt 优化不再靠主观调参，而是基于 `ai_feedback_summary` 数据定位问题场景，调整后通过 Q14 灰度发布对比新旧版本的 `avg_quality_score`，数据驱动迭代。

#### 5. 技术栈选择

| 方案 | 技术 | 适合情况 | 本项目选择 |
|------|------|----------|------------|
| 轻量方案 | MySQL `ai_call_log` 扩展字段 + 管理端列表 | 开发快，最小化改动 | ✅ 第一阶段 |
| 中等方案 | MySQL + ECharts 质量统计看板 | 答辩展示效果好 | ✅ 第一阶段（看板） |
| 进阶方案 | LangSmith / PromptLayer / 自建评测集 | 专业 AI 评估 | ⏳ 第二阶段（可选） |

**完善方向**：
- 第一阶段实现轻量方案（扩展字段 + 管理端列表），开发量约 1 天
- 第二阶段叠加 ECharts 看板，约 0.5 天
- 长期可引入 LangSmith 做离线评测集对比

---

### Q14：Prompt 版本管理与灰度实验如何实现？（增强 Q3）

**现状**：Q3 的 `prompt_template` 表仅存储「当前激活版本」，无版本历史，无法回滚，无法灰度对比。

**补充方案**：在 Q3 分层混合基础上，增加版本管理与灰度能力。

#### 1. `prompt_template` 表结构扩展（在 Q3 基础上）

```sql
-- 在 Q3 表结构基础上新增字段
ALTER TABLE prompt_template ADD COLUMN version INT NOT NULL DEFAULT 1 COMMENT '版本号';
ALTER TABLE prompt_template ADD COLUMN scene VARCHAR(32) NOT NULL COMMENT '场景：CAREER_EXPLORE/RESUME/INTERVIEW/ENTERPRISE/CUSTOMER_SERVICE/GAP_ANALYSIS';
ALTER TABLE prompt_template ADD COLUMN status VARCHAR(16) NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT/ACTIVE/GRAY/ARCHIVED';
ALTER TABLE prompt_template ADD COLUMN gray_ratio INT DEFAULT 0 COMMENT '灰度比例 0-100（status=GRAY 时生效）';
ALTER TABLE prompt_template ADD COLUMN created_by BIGINT COMMENT '创建人（管理员 ID）';
ALTER TABLE prompt_template ADD COLUMN created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE prompt_template ADD COLUMN rollback_to INT COMMENT '回滚自哪个版本（可选）';

-- 调整唯一键：同一场景同一版本唯一
ALTER TABLE prompt_template DROP INDEX uk_name_active;
ALTER TABLE prompt_template ADD UNIQUE KEY uk_scene_version (scene, version);
```

**字段说明**：
- `version`：单调递增，每次编辑新建版本而非覆盖
- `status`：`DRAFT`（草稿）→ `GRAY`（灰度）→ `ACTIVE`（全量）→ `ARCHIVED`（归档）
- `gray_ratio`：灰度比例，`status=GRAY` 时按比例分流；`ACTIVE` 时忽略此字段
- `rollback_to`：记录回滚链路，便于审计

#### 2. 状态流转

```
DRAFT --发布灰度--> GRAY --灰度验证通过--> ACTIVE
  |                   |                        |
  |                   └--灰度效果差--> ARCHIVED  └--新版本上线--> 旧版本 ARCHIVED
  └--直接上线（跳过灰度）--> ACTIVE
```

**流转规则**：
- 同一 `scene` 同一时刻只能有 1 个 `ACTIVE` 版本
- `GRAY` 版本可与 `ACTIVE` 版本共存，按 `gray_ratio` 分流
- `ARCHIVED` 版本不可恢复，但可查看内容用于对比

#### 3. 灰度分流逻辑（PromptTemplateUtil 改造）

```java
/**
 * 加载模板（支持灰度分流）
 * 在 Q3 的 DB>文件基础上，增加灰度版本判断
 */
public String loadTemplate(String scene, Long userId) {
    // 1. 查 ACTIVE 版本（全量）
    PromptTemplate activeVersion = promptTemplateMapper.selectActive(scene);
    // 2. 查 GRAY 版本（灰度）
    PromptTemplate grayVersion = promptTemplateMapper.selectGray(scene);
    
    String content;
    if (grayVersion != null) {
        // 灰度分流：按 userId 哈希取模判断是否命中灰度
        int hash = Math.abs(userId.hashCode()) % 100;
        if (hash < grayVersion.getGrayRatio()) {
            content = grayVersion.getContent();
            log.debug("用户 {} 命中灰度版本: scene={}, v{}", userId, scene, grayVersion.getVersion());
            // 记录使用的版本到 ThreadLocal，供 ai_call_log 落库
            PromptVersionContext.setVersion(grayVersion.getVersion());
        } else {
            content = activeVersion.getContent();
            PromptVersionContext.setVersion(activeVersion.getVersion());
        }
    } else if (activeVersion != null) {
        content = activeVersion.getContent();
        PromptVersionContext.setVersion(activeVersion.getVersion());
    } else {
        // DB 无版本 → 回退 classpath 文件（基础层）
        content = loadFromClasspath(scene);
        PromptVersionContext.setVersion(0);  // 0 表示文件版本
    }
    return content;
}
```

**灰度说明**：
- 用 `userId.hashCode() % 100` 保证同一用户始终命中同一版本，避免体验不一致
- `PromptVersionContext` 是 ThreadLocal，记录本次调用使用的版本号，供 `ai_call_log` 落库

#### 4. `ai_call_log` 记录 Prompt 版本

```sql
-- ai_call_log 新增字段
ALTER TABLE ai_call_log ADD COLUMN prompt_version INT DEFAULT 0 COMMENT '使用的 Prompt 版本（0=文件版本）';
```

**用途**：结合 Q13 的 `quality_score`，可对比不同 Prompt 版本的质量评分，验证灰度效果。

#### 5. 管理端操作接口

```java
@RestController
@RequestMapping("/api/admin/prompt")
public class PromptAdminController {
    
    /**
     * 新建 Prompt 版本（草稿）
     */
    @PostMapping("/draft")
    public R<Long> createDraft(@RequestBody PromptDraftDTO dto);
    
    /**
     * 发布灰度（DRAFT → GRAY，设置 gray_ratio）
     */
    @PutMapping("/{id}/gray/{ratio}")
    public R<Void> publishGray(@PathVariable Long id, @PathVariable int ratio);
    
    /**
     * 灰度转全量（GRAY → ACTIVE，旧 ACTIVE → ARCHIVED）
     */
    @PutMapping("/{id}/activate")
    public R<Void> activate(@PathVariable Long id);
    
    /**
     * 回滚到指定版本（将指定版本复制为新 ACTIVE，旧 ACTIVE 归档）
     */
    @PutMapping("/rollback/{scene}/{toVersion}")
    public R<Void> rollback(@PathVariable String scene, @PathVariable int toVersion);
    
    /**
     * 查看版本历史
     */
    @GetMapping("/history/{scene}")
    public R<List<PromptTemplate>> history(@PathVariable String scene);
}
```

#### 6. 技术栈选择

| 方案 | 技术 | 适合情况 | 本项目选择 |
|------|------|----------|------------|
| 简单版 | MySQL `prompt_template` + 版本字段 | 当前项目最合适 | ✅ 第一阶段 |
| 中等版 | MySQL + Redis 缓存当前激活版本 | 频繁调整 Prompt | ⏳ 第二阶段（如调优频繁） |
| 进阶版 | Nacos / Apollo 配置中心 | 企业级配置管理 | ❌ 30 天内不引入 |

**完善方向**：
- 第一阶段实现简单版（版本字段 + 状态流转 + 回滚），约 1 天
- 第二阶段叠加灰度分流 + `ai_call_log` 版本记录 + 质量对比，约 0.5 天
- 与 Q13 质量评估联动：灰度版本的 `avg_quality_score` 对比，数据驱动是否转全量

---

### Q15：AI 调用成本与额度如何控制？

**现状**：Q1 的 Resilience4j 实现了系统级限流（5 QPS），但缺少「用户级调用额度」。若 DeepSeek API 被频繁调用，易超预算或被刷接口。

**补充方案**：用户级配额控制 + 调用分类统计。

#### 1. 配额规则设计

| 角色 | 场景 | 每日限额 | 说明 |
|------|------|----------|------|
| 学生 | R-008 简历优化 | 3 次/天 | 同一简历可多次优化，但限制频率 |
| 学生 | R-009 模拟面试 | 2 次/天 | 每次面试消耗较多 Token |
| 学生 | R-004 职业探索 | 5 次/天 | 画像未变时走缓存，不消耗配额 |
| 学生 | R-014 智能客服 | 20 次/天 | FAQ 命中不消耗配额，仅 AI 调用计数 |
| 企业 HR | R-010 项目解析 | 5 次/天 | 项目需求解析消耗较多 Token |
| 企业 HR | R-010 候选人推荐 | 10 次/天 | 推荐可复用解析结果 |
| 管理员 | 所有场景 | 不限 | 后台调试用 |

#### 2. 数据库设计

```sql
-- AI 调用配额日志表
CREATE TABLE ai_quota_log (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id     BIGINT NOT NULL COMMENT '用户 ID',
    user_role   VARCHAR(16) NOT NULL COMMENT 'STUDENT/ENTERPRISE/ADMIN',
    scene       VARCHAR(32) NOT NULL COMMENT 'AI 场景',
    call_count  INT NOT NULL DEFAULT 0 COMMENT '当日调用次数',
    quota_date  DATE NOT NULL COMMENT '配额日期',
    created_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_user_scene_date (user_id, scene, quota_date),
    INDEX idx_date_scene (quota_date, scene)
) COMMENT='AI 调用配额日志';
```

**表设计说明**：
- 唯一键 `uk_user_scene_date`：同一用户同一场景同一日期只有一条记录，`call_count` 累加
- 每日自动汇总，管理端可查询用户级、场景级调用统计

#### 3. 配额校验逻辑（Redis 计数器 + MySQL 汇总）

```java
/**
 * AI 调用配额服务
 * 采用「Redis 实时限流 + MySQL 持久汇总」组合方案
 */
@Service
public class AiQuotaService {

    private final StringRedisTemplate redisTemplate;
    private final AiQuotaLogMapper aiQuotaLogMapper;

    /**
     * 校验用户是否有调用额度
     * @return true=有额度可调用，false=超限
     */
    public boolean checkQuota(Long userId, String role, String scene) {
        int limit = getQuotaLimit(role, scene);
        String key = String.format("ai:quota:%d:%s:%s:%s", 
                userId, role, scene, LocalDate.now());
        Long current = redisTemplate.opsForValue().increment(key);
        if (current == 1) {
            redisTemplate.expire(key, Duration.ofDays(1));  // 首次设置 TTL
        }
        return current <= limit;
    }

    /**
     * 获取配额上限（从配置读取，支持动态调整）
     */
    private int getQuotaLimit(String role, String scene) {
        return quotaConfig.getLimit(role, scene);  // 从 application.yml 读取
    }

    /**
     * 异步汇总到 MySQL（每日凌晨定时任务 + 实时增量写入）
     */
    @Async
    public void syncToMysql(Long userId, String role, String scene, int count) {
        AiQuotaLog log = new AiQuotaLog();
        log.setUserId(userId);
        log.setUserRole(role);
        log.setScene(scene);
        log.setCallCount(count);
        log.setQuotaDate(LocalDate.now());
        aiQuotaLogMapper.insertOrUpdate(log);  // ON DUPLICATE KEY UPDATE
    }
}
```

**组合方案说明**：
- Redis 实时计数：低延迟，保证限流即时生效
- MySQL 持久汇总：管理端展示用，Redis 宕机时不丢失统计
- 参考评估建议「组合方案 Redis 限流 + MySQL 汇总，最完整」

#### 4. 业务层集成（各 AI Service 改造）

```java
// ResumeServiceImpl.java 示例
public ResumeAnalysisResult analyzeResume(MultipartFile file, Long userId) {
    // 1. 配额校验
    if (!aiQuotaService.checkQuota(userId, "STUDENT", "RESUME")) {
        throw new BusinessException("今日简历优化次数已达上限（3 次/天），请明日再试");
    }
    
    // 2. 业务逻辑（调 AI）
    // ...
    
    // 3. 异步汇总到 MySQL
    aiQuotaService.syncToMysql(userId, "STUDENT", "RESUME", 1);
}
```

#### 5. 调用分类统计（缓存/降级/真实 AI 分开）

```sql
-- ai_call_log 扩展字段
ALTER TABLE ai_call_log ADD COLUMN call_source VARCHAR(16) DEFAULT 'AI' COMMENT 'AI/CACHE/FALLBACK';
```

**统计维度**：
- `AI`：真实调用 DeepSeek API（消耗配额）
- `CACHE`：Redis 缓存命中（不消耗配额）
- `FALLBACK`：降级到规则兜底（不消耗配额）

**管理端看板**：展示各场景的「缓存命中率」「降级率」「真实 AI 调用数」，评估成本控制效果。

#### 6. 技术栈选择

| 方案 | 技术 | 适合情况 | 本项目选择 |
|------|------|----------|------------|
| 轻量方案 | Redis 计数器 + TTL | 最推荐，开发快 | ✅ 第一阶段（限流） |
| 持久方案 | MySQL `ai_quota_log` 每日统计 | 管理端展示 | ✅ 第一阶段（汇总） |
| 组合方案 | Redis 限流 + MySQL 汇总 | 最完整 | ✅ 第一阶段（最终选择） |

**完善方向**：
- 配额上限可通过 `application.yml` 配置，支持动态调整
- 超限时前端友好提示，引导用户明日再试或升级权限
- 第二阶段可叠加「按 Token 消耗计费」维度，更精准控成本

---

### Q16：AI 输出安全与内容过滤如何保障？

**现状**：文档主要关注可用性（重试/限流/熔断），缺少安全边界。客服、职业建议、简历建议、面试评价都可能生成不合适内容。

**补充方案**：Prompt 约束 + 输出过滤 + 场景白名单三层防护。

#### 1. Prompt 层约束（各场景 System Prompt 增强）

```
【通用安全约束】（所有 AI 场景的 System Prompt 必须包含）
1. 禁止生成歧视性内容（性别、种族、年龄、地域、残疾等）
2. 禁止虚假承诺（如「保证拿到 offer」「100% 录取」）
3. 禁止违法内容（作弊、伪造学历、背景调查规避等）
4. 禁止涉及政治、宗教敏感话题
5. 必须基于用户提供的数据回答，不得编造
```

**各场景专属约束**：

| 场景 | 专属安全约束 |
|------|--------------|
| R-014 智能客服 | 仅回答平台相关问题（账号、功能、求职流程），禁止回答无关敏感问题（政治、医疗、法律建议） |
| R-008 简历优化 | 仅基于用户上传简历原文改写，禁止编造工作经历、项目经验、技能水平 |
| R-009 模拟面试 | 评价基于候选人回答内容，不得对候选人个人特质（口音、性别等）做评价 |
| R-010 企业推荐 | 推荐结果仅展示技能匹配度，不得暴露学生隐私（联系方式、身份证号、家庭住址） |
| R-004 职业探索 | 职业建议基于测评结果，不得对特定职业做歧视性评价 |

#### 2. 输出过滤层（敏感词 + 场景白名单）

```java
/**
 * AI 输出安全过滤器
 * 在 AI 返回结果后、返回前端前执行
 */
@Component
public class AiOutputFilter {

    private final Set<String> sensitiveWords;  // 启动时加载敏感词库
    
    /**
     * 过滤 AI 输出
     * @param scene  AI 场景
     * @param output AI 原始输出
     * @return 过滤后的安全输出
     */
    public String filter(String scene, String output) {
        // 1. 敏感词检查
        String filtered = sensitiveWords.stream()
                .reduce(output, (text, word) -> text.replace(word, "***"));
        
        // 2. 场景白名单校验（客服场景）
        if ("CUSTOMER_SERVICE".equals(scene)) {
            filtered = applyCustomerServiceWhitelist(filtered);
        }
        
        // 3. 隐私脱敏（企业推荐场景）
        if ("ENTERPRISE".equals(scene)) {
            filtered = maskPrivacy(filtered);
        }
        
        return filtered;
    }
    
    /**
     * 客服场景白名单：检测是否涉及无关敏感话题
     */
    private String applyCustomerServiceWhitelist(String output) {
        // 检测政治、医疗、法律建议等关键词，命中则替换为引导话术
        for (String keyword : OFF_TOPIC_KEYWORDS) {
            if (output.contains(keyword)) {
                return "抱歉，我仅能回答平台相关问题（账号、功能、求职流程）。如需其他帮助，请联系人工客服。";
            }
        }
        return output;
    }
    
    /**
     * 隐私脱敏：企业推荐结果中脱敏学生隐私
     */
    private String maskPrivacy(String output) {
        return output
                .replaceAll("(\\d{11})", "***")           // 手机号
                .replaceAll("(\\d{17}[0-9Xx])", "***")    // 身份证号
                .replaceAll("([\\w.]+@\\w+\\.\\w+)", "***"); // 邮箱
    }
}
```

#### 3. 敏感词库管理

```sql
-- 敏感词库表
CREATE TABLE ai_sensitive_word (
    id      BIGINT AUTO_INCREMENT PRIMARY KEY,
    word    VARCHAR(64) NOT NULL COMMENT '敏感词',
    category VARCHAR(32) NOT NULL COMMENT 'POLITICS/PORN/ABUSE/AD/OTHER',
    status  TINYINT DEFAULT 1 COMMENT '1=启用 0=停用',
    UNIQUE KEY uk_word (word)
) COMMENT='AI 敏感词库';
```

**词库维护**：
- 初始导入基础敏感词（政治、色情、辱骂、广告四类，约 200 词）
- 管理端支持增删改查
- 启动时加载到内存 `Set<String>`，避免每次查 DB

#### 4. 技术栈选择

| 方案 | 技术 | 适合情况 | 本项目选择 |
|------|------|----------|------------|
| 轻量方案 | 关键词黑名单 + Prompt 约束 | MVP 推荐 | ✅ 第一阶段 |
| 中等方案 | DFA 敏感词过滤 + 场景白名单 | 实用，性能好 | ⏳ 第二阶段（如敏感词增多） |
| 进阶方案 | 内容安全模型 / Moderation API | 后期可选 | ❌ 30 天内不引入 |

**完善方向**：
- 第一阶段实现轻量方案（关键词黑名单 + Prompt 约束），约 0.5 天
- DFA 算法在敏感词库超过 500 词时再引入，性能优于字符串遍历
- 长期可接入阿里云内容安全 API 做图片/文本双重审核

---

### Q17：简历优化结果如何结构化存储与版本对比？（增强 Q5）

**现状**：Q5 修复了「文本提取 + 调 AI」，但未保存优化历史，用户无法查看每次优化结果与版本对比。

**补充方案**：结构化存储 AI 结果 + 历史记录 + 版本 Diff 对比。

#### 1. `resume_analysis` 表扩展

```sql
-- 在现有 resume_analysis 表基础上扩展
ALTER TABLE resume_analysis ADD COLUMN result_json JSON DEFAULT NULL COMMENT 'AI 返回的结构化结果（JSON）';
ALTER TABLE resume_analysis ADD COLUMN original_text_summary TEXT COMMENT '原始简历文本摘要（前 500 字）';
ALTER TABLE resume_analysis ADD COLUMN optimization_suggestions JSON DEFAULT NULL COMMENT '优化建议数组';
ALTER TABLE resume_analysis ADD COLUMN modified_snippets JSON DEFAULT NULL COMMENT '修改片段数组（原文→建议）';
ALTER TABLE resume_analysis ADD COLUMN score_dimensions JSON DEFAULT NULL COMMENT '评分维度（结构/内容/语言/排版）';
ALTER TABLE resume_analysis ADD COLUMN version INT DEFAULT 1 COMMENT '优化版本号';
```

**字段说明**：
- `result_json`：AI 完整返回结果，便于回溯
- `optimization_suggestions`：结构化建议数组，前端按列表展示
- `modified_snippets`：原文片段与建议改写的对照，支持 Diff 展示
- `score_dimensions`：多维度评分，便于趋势对比

#### 2. 结构化结果示例

```json
{
  "scoreDimensions": {
    "structure": 75,
    "content": 68,
    "language": 82,
    "layout": 70,
    "overall": 73
  },
  "optimizationSuggestions": [
    {
      "type": "CONTENT",
      "priority": "HIGH",
      "issue": "工作经历缺少量化成果",
      "suggestion": "在 XX 项目经历中补充具体数据，如「优化后响应时间降低 40%」"
    },
    {
      "type": "LANGUAGE",
      "priority": "MEDIUM",
      "issue": "部分描述过于口语化",
      "suggestion": "「负责搞后台」改为「负责后端系统开发与维护」"
    }
  ],
  "modifiedSnippets": [
    {
      "original": "负责搞后台，写接口",
      "suggested": "负责后端系统开发，设计并实现 RESTful API 15 个，支撑日均 10 万次请求",
      "reason": "量化成果 + 专业表述"
    }
  ]
}
```

#### 3. 历史记录与版本对比

```java
/**
 * 简历优化历史查询
 */
@GetMapping("/api/student/resume/{resumeId}/history")
public R<List<ResumeAnalysisVO>> getHistory(@PathVariable Long resumeId) {
    // 返回该简历的所有优化记录，按 version 降序
    return R.success(resumeAnalysisMapper.selectByResumeIdOrderByVersionDesc(resumeId));
}

/**
 * 版本对比（前端 Diff 展示）
 */
@GetMapping("/api/student/resume/compare")
public R<ResumeCompareVO> compare(
        @RequestParam Long analysisId1,
        @RequestParam Long analysisId2) {
    ResumeAnalysis r1 = resumeAnalysisMapper.selectById(analysisId1);
    ResumeAnalysis r2 = resumeAnalysisMapper.selectById(analysisId2);
    // 构建对比 VO：评分变化、建议变化、片段变化
    return R.success(buildCompareVO(r1, r2));
}
```

**前端展示**：
- 历史列表：时间轴展示每次优化记录，含版本号、评分、建议数
- 版本对比：左右分栏 Diff 展示两次优化的评分变化、建议差异、片段改写对照

#### 4. 技术栈选择

| 方案 | 技术 | 适合情况 | 本项目选择 |
|------|------|----------|------------|
| 轻量方案 | MySQL `resume_analysis.result_json` | 当前已有表，直接扩展 | ✅ 第一阶段 |
| 中等方案 | MySQL + JSON 字段 + 前端 Diff 展示 | 推荐，闭环完整 | ✅ 第一阶段（前端 Diff） |
| 进阶方案 | 生成 DOCX 优化版简历 | 后期亮点功能 | ⏳ 第二阶段（可选） |

**完善方向**：
- 第一阶段实现轻量+中等方案（结构化存储 + 历史列表 + 版本对比），约 1 天
- 第二阶段可叠加「一键生成优化版 DOCX 简历」，用 Apache POI 生成文档（依赖已有）

---

### Q18：模拟面试会话状态机如何设计？（增强 Q6）

**现状**：Q6 实现了 FOLLOW_UP 追问，但状态流转逻辑散落在代码中，易混乱。

**补充方案**：显式状态机 + 完整过程记录 + 五维评分。

#### 1. 状态机定义

```
START → ASKING → (回答提交) → FOLLOW_UP? 
                              ├─ 是 → ASKING（追问，最多 2 次）
                              └─ 否 → NEXT_QUESTION
                                          ├─ 还有题 → ASKING
                                          └─ 无题 → EVALUATING → FINISHED
```

**状态说明**：

| 状态 | 含义 | 流转条件 |
|------|------|----------|
| `START` | 面试开始，初始化会话 | 用户点击「开始面试」 |
| `ASKING` | 提问中，等待用户回答 | 进入新题目或追问 |
| `FOLLOW_UP` | 判断是否追问 | AI 评估回答质量，决定追问或下一题 |
| `NEXT_QUESTION` | 切换下一题 | 追问次数达上限或回答充分 |
| `EVALUATING` | 生成综合评价 | 所有题目回答完毕 |
| `FINISHED` | 面试结束，展示报告 | 评价生成完成 |

#### 2. Redis 会话结构扩展（在 Q6 基础上）

```java
/**
 * 模拟面试会话（Redis 存储）
 */
@Data
public class InterviewSession {
    private Long sessionId;
    private Long userId;
    private String currentPosition;          // 面试岗位
    private String state;                    // 状态机当前状态
    private int currentQuestionIndex;        // 当前题目索引
    private int totalQuestionCount;          // 总题数（5-8）
    private int followUpCount;               // 当前题追问次数（上限 2）
    private List<QaRecord> qaRecords;        // 完整问答记录
    private String finalEvaluation;          // 最终评价
    private FiveDimensionScore scores;       // 五维评分
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    
    @Data
    public static class QaRecord {
        private int questionIndex;
        private String question;
        private String answer;
        private List<String> followUps;      // 追问列表
        private List<String> followUpAnswers;// 追问回答列表
        private String aiEvaluation;         // AI 对该题的评价
    }
    
    @Data
    public static class FiveDimensionScore {
        private int technicalDepth;          // 技术深度
        private int problemSolving;          // 问题解决能力
        private int communication;           // 表达沟通
        private int projectExperience;       // 项目经验
        private int learningAbility;         // 学习能力
        private int overall;                 // 综合评分
    }
}
```

#### 3. 状态流转实现

```java
/**
 * 提交回答，根据状态机流转
 */
public InterviewSubmitVO submitAnswer(Long sessionId, String answer) {
    InterviewSession session = getFromRedis(sessionId);
    
    switch (session.getState()) {
        case "ASKING":
            // 记录当前题回答
            recordAnswer(session, answer);
            // 判断是否追问
            if (shouldFollowUp(session, answer) && session.getFollowUpCount() < 2) {
                session.setState("FOLLOW_UP");
                String followUpQuestion = generateFollowUp(session, answer);
                session.getQaRecords().get(session.getCurrentQuestionIndex())
                        .getFollowUps().add(followUpQuestion);
                session.setFollowUpCount(session.getFollowUpCount() + 1);
                session.setState("ASKING");  // 追问也是 ASKING 状态
                saveToRedis(session);
                return InterviewSubmitVO.followUp(followUpQuestion);
            } else {
                // 不追问，切下一题
                return moveToNextQuestion(session);
            }
        case "EVALUATING":
            // 不应到达此处，前端应禁用提交
            throw new BusinessException("面试已结束，正在生成评价");
        default:
            throw new BusinessException("非法状态: " + session.getState());
    }
}

/**
 * 切换下一题或进入评价
 */
private InterviewSubmitVO moveToNextQuestion(InterviewSession session) {
    session.setFollowUpCount(0);  // 重置追问计数
    
    if (session.getCurrentQuestionIndex() < session.getTotalQuestionCount() - 1) {
        // 还有题
        session.setCurrentQuestionIndex(session.getCurrentQuestionIndex() + 1);
        session.setState("ASKING");
        String nextQuestion = generateQuestion(session);
        saveToRedis(session);
        return InterviewSubmitVO.nextQuestion(nextQuestion);
    } else {
        // 无题，进入评价
        session.setState("EVALUATING");
        saveToRedis(session);
        FiveDimensionScore scores = generateEvaluation(session);
        session.setScores(scores);
        session.setState("FINISHED");
        session.setEndTime(LocalDateTime.now());
        saveToRedis(session);
        // 异步持久化到 MySQL
        persistToInterviewRecord(session);
        return InterviewSubmitVO.finished(scores);
    }
}
```

#### 4. 完整过程持久化（MySQL）

```sql
-- 面试记录表（持久化完整过程）
CREATE TABLE interview_record (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    session_id      VARCHAR(64) NOT NULL COMMENT '会话 ID',
    user_id         BIGINT NOT NULL,
    position        VARCHAR(64) NOT NULL COMMENT '面试岗位',
    qa_records_json JSON NOT NULL COMMENT '完整问答记录（JSON）',
    scores_json     JSON NOT NULL COMMENT '五维评分（JSON）',
    final_evaluation TEXT COMMENT 'AI 综合评价',
    duration_seconds INT COMMENT '面试时长（秒）',
    ai_call_count   INT COMMENT 'AI 调用次数',
    created_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_session (session_id),
    INDEX idx_user (user_id)
) COMMENT='模拟面试完整记录';
```

**持久化说明**：
- Redis 会话在面试结束后异步落库到 `interview_record`
- 用户可查看历史面试记录，对比不同岗位/时间的评分趋势

#### 5. 规则兜底评分（替代 Math.random()）

```java
/**
 * 规则兜底评分（AI 不可用时）
 * 基于回答字数、关键词密度、追问次数等维度加权计算
 */
private FiveDimensionScore buildRuleBasedScore(InterviewSession session) {
    FiveDimensionScore scores = new FiveDimensionScore();
    
    for (QaRecord qa : session.getQaRecords()) {
        String answer = qa.getAnswer();
        int length = answer.length();
        int keywordHits = countKeywords(answer, session.getCurrentPosition());
        int followUpCount = qa.getFollowUps().size();
        
        // 技术深度：关键词命中率
        scores.setTechnicalDepth(scores.getTechnicalDepth() + Math.min(keywordHits * 15, 100));
        // 表达沟通：回答字数（50-300 字为佳）
        scores.setCommunication(scores.getCommunication() + scoreByLength(length));
        // 问题解决：追问次数少=回答充分
        scores.setProblemSolving(scores.getProblemSolving() + (2 - followUpCount) * 30);
    }
    
    // 取平均
    int count = session.getQaRecords().size();
    scores.setTechnicalDepth(scores.getTechnicalDepth() / count);
    scores.setCommunication(scores.getCommunication() / count);
    scores.setProblemSolving(scores.getProblemSolving() / count);
    scores.setProjectExperience(70);  // 默认值
    scores.setLearningAbility(75);    // 默认值
    scores.setOverall((scores.getTechnicalDepth() + scores.getCommunication() 
            + scores.getProblemSolving() + scores.getProjectExperience() 
            + scores.getLearningAbility()) / 5);
    return scores;
}
```

#### 6. 技术栈选择

| 方案 | 技术 | 适合情况 | 本项目选择 |
|------|------|----------|------------|
| 轻量方案 | Redis 保存面试会话 JSON | 当前最合适 | ✅ 第一阶段（会话） |
| 持久方案 | MySQL `interview_record` 保存完整过程 | 推荐补充 | ✅ 第一阶段（持久化） |
| 进阶方案 | WebSocket / SSE 流式面试 | 后期体验优化 | ⏳ 第二阶段（可选） |

**完善方向**：
- 第一阶段实现状态机 + Redis 会话 + MySQL 持久化 + 规则兜底评分，约 1.5 天
- 第二阶段可叠加 SSE 流式输出面试问题（打字机效果），约 0.5 天

---

### Q19：企业推荐可解释性如何增强？（增强 Q7）

**现状**：Q7 实现了「项目解析 + 候选人推荐」，但推荐结果仅展示评分，缺少推荐理由和可解释维度。

**补充方案**：结构化评分 + 匹配/缺失技能 + 推荐理由 + 风险提示。

#### 1. 推荐结果结构化（推荐评分 JSON）

```json
{
  "candidateId": 10086,
  "candidateName": "张三",
  "scores": {
    "skillScore": 82,
    "assessmentScore": 76,
    "learningScore": 70,
    "projectFitScore": 80,
    "finalScore": 78
  },
  "matchedSkills": ["Java", "Spring Boot", "MySQL", "Redis"],
  "missingSkills": ["Docker", "Kubernetes"],
  "learningProgress": {
    "completedCourses": 5,
    "totalCourses": 8,
    "completionRate": "62.5%"
  },
  "reason": "该候选人后端基础较好，掌握 Java/Spring Boot/MySQL/Redis，适合商城后端模块开发。缺失 Docker/K8s，建议入职后补充学习。",
  "riskWarnings": [
    "项目经验偏少，仅有 1 个完整项目",
    "缺失容器化技能，可能影响部署环节上手"
  ],
  "recommendationLevel": "RECOMMEND"
}
```

**字段说明**：
- `skillScore`：技能匹配度（候选人技能 ∩ 岗位需求技能 / 岗位需求技能数）
- `assessmentScore`：测评分数（来自 `assessment_record` 表）
- `learningScore`：学习完成度（来自 `learning_progress` 表）
- `projectFitScore`：项目契合度（候选人项目经历与岗位职责的语义匹配，第二阶段用 PGVector）
- `finalScore`：加权综合分（skill 40% + assessment 20% + learning 20% + projectFit 20%）
- `recommendationLevel`：`STRONG_RECOMMEND`（≥85）/ `RECOMMEND`（70-84）/ `CAUTION`（60-69）/ `NOT_RECOMMEND`（<60）

#### 2. 推荐理由生成（DeepSeek 调用）

```java
/**
 * 生成候选人推荐理由（AI 可解释性）
 */
private String generateRecommendationReason(Candidate candidate, 
                                             ProjectAnalysisResult.Position position,
                                             RecommendationScore scores) {
    String systemPrompt = loadTemplate("enterprise_recommend_reason");
    String userPrompt = String.format(
            "候选人：%s\n" +
            "匹配技能：%s\n" +
            "缺失技能：%s\n" +
            "技能评分：%d，测评评分：%d，学习完成度：%d%%，项目契合度：%d\n" +
            "岗位需求：%s\n" +
            "请用一段话（50-100 字）说明推荐理由，并给出风险提示。",
            candidate.getName(),
            String.join("、", scores.getMatchedSkills()),
            String.join("、", scores.getMissingSkills()),
            scores.getSkillScore(), scores.getAssessmentScore(),
            scores.getLearningScore(), scores.getProjectFitScore(),
            String.join("、", position.getResponsibilities())
    );
    
    try {
        return deepSeekService.callAPI(systemPrompt, userPrompt);
    } catch (Exception e) {
        // 兜底：模板化理由
        return buildFallbackReason(candidate, scores);
    }
}

/**
 * 兜底理由（AI 不可用时）
 */
private String buildFallbackReason(Candidate candidate, RecommendationScore scores) {
    return String.format("该候选人掌握 %s，缺失 %s。综合评分 %d，建议%s。",
            String.join("、", scores.getMatchedSkills()),
            String.join("、", scores.getMissingSkills()),
            scores.getFinalScore(),
            scores.getFinalScore() >= 70 ? "进入面试环节" : "暂不推荐");
}
```

#### 3. 前端展示（候选人卡片）

```
┌─────────────────────────────────────────────┐
│ 张三                          [推荐] 78 分   │
│ 后端开发工程师候选人                          │
├─────────────────────────────────────────────┤
│ 🟢 匹配技能：Java、Spring Boot、MySQL、Redis │
│ 🔴 缺失技能：Docker、Kubernetes              │
│ 📚 学习完成度：62.5%（5/8 课程）             │
├─────────────────────────────────────────────┤
│ 💡 推荐理由：                                 │
│ 该候选人后端基础较好，掌握 Java/Spring Boot/  │
│ MySQL/Redis，适合商城后端模块开发。缺失        │
│ Docker/K8s，建议入职后补充学习。              │
├─────────────────────────────────────────────┤
│ ⚠️ 风险提示：                                 │
│ • 项目经验偏少，仅有 1 个完整项目             │
│ • 缺失容器化技能，可能影响部署环节上手         │
├─────────────────────────────────────────────┤
│ [HR 标记：推荐准确 / 不准确]  ← Q13 反馈入口  │
└─────────────────────────────────────────────┘
```

#### 4. 技术栈选择

| 方案 | 技术 | 适合情况 | 本项目选择 |
|------|------|----------|------------|
| MVP | 规则评分 + DeepSeek 生成推荐理由 | 最推荐 | ✅ 第一阶段 |
| 增强 | MySQL 关键词召回 + PGVector 重排 | 第二阶段 | ⏳ 第二阶段（D5 启用后） |
| 高级 | Learning-to-Rank / 向量召回 + 权重模型 | 后期 | ❌ 30 天内不引入 |

**完善方向**：
- 第一阶段实现 MVP（规则评分 + AI 理由 + 风险提示），约 1 天
- 第二阶段叠加 PGVector 语义匹配提升 `projectFitScore` 精度，约 0.5 天
- 与 Q13 联动：HR 标记「推荐准确/不准确」反馈到 `ai_call_log`，优化评分权重

---

### Q20：智能客服知识库如何管理与更新？（增强 Q9）

**现状**：Q9 实现了「FAQ 匹配 + DeepSeek 兜底」，但缺少 FAQ 管理流程和未命中问题收集。

**补充方案**：管理端 FAQ CRUD + 未命中问题收集 + 高频问题转 FAQ + 缓存清理。

#### 1. FAQ 表结构扩展

```sql
-- 在现有 faq 表基础上扩展
ALTER TABLE faq ADD COLUMN category VARCHAR(32) DEFAULT 'GENERAL' COMMENT '分类：ACCOUNT/RESUME/INTERVIEW/ENTERPRISE/PLATFORM/GENERAL';
ALTER TABLE faq ADD COLUMN keywords VARCHAR(255) COMMENT '关键词（逗号分隔，用于权重匹配）';
ALTER TABLE faq ADD COLUMN status TINYINT DEFAULT 1 COMMENT '1=启用 0=停用';
ALTER TABLE faq ADD COLUMN view_count INT DEFAULT 0 COMMENT '查看次数';
ALTER TABLE faq ADD COLUMN helpful_count INT DEFAULT 0 COMMENT '有帮助次数';
ALTER TABLE faq ADD COLUMN created_by BIGINT COMMENT '创建人';
ALTER TABLE faq ADD COLUMN updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP;

-- 未命中问题收集表
CREATE TABLE customer_service_unmatched (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    question    TEXT NOT NULL COMMENT '用户问题',
    user_id     BIGINT COMMENT '提问用户',
    hit_count   INT DEFAULT 1 COMMENT '出现次数（相同问题累加）',
    status      VARCHAR(16) DEFAULT 'PENDING' COMMENT 'PENDING/RESOLVED/IGNORED',
    resolved_faq_id BIGINT COMMENT '已转为 FAQ 的 ID',
    created_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_status_count (status, hit_count DESC)
) COMMENT='客服未命中问题收集';
```

**表设计说明**：
- `keywords`：管理员手动标注关键词，提升 Q9 的 `matchFaq` 权重匹配精度
- `view_count`/`helpful_count`：FAQ 热度统计，辅助管理员优化高频问题
- `customer_service_unmatched`：未命中问题按 `hit_count` 降序排列，高频问题优先转 FAQ

#### 2. FAQ 管理端接口

```java
@RestController
@RequestMapping("/api/admin/faq")
public class FaqAdminController {

    /**
     * FAQ 列表（支持分类筛选、关键词搜索、状态过滤）
     */
    @GetMapping("/list")
    public R<IPage<Faq>> list(@RequestParam(required = false) String category,
                              @RequestParam(required = false) String keyword,
                              @RequestParam(required = false) Integer status,
                              @RequestParam(defaultValue = "1") int page,
                              @RequestParam(defaultValue = "20") int size);

    /**
     * 新增/编辑 FAQ
     */
    @PostMapping("/save")
    public R<Long> save(@RequestBody FaqDTO dto) {
        Long id = faqService.saveOrUpdate(dto);
        // 清理客服缓存（Q9 的 callAPIWithCache 缓存）
        customerServiceService.clearFaqCache();
        return R.success(id);
    }

    /**
     * 启用/停用 FAQ
     */
    @PutMapping("/{id}/status/{status}")
    public R<Void> toggleStatus(@PathVariable Long id, @PathVariable int status);

    /**
     * 未命中问题列表（高频优先）
     */
    @GetMapping("/unmatched")
    public R<IPage<CustomerServiceUnmatched>> unmatched(
            @RequestParam(defaultValue = "PENDING") String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size);

    /**
     * 将未命中问题转为 FAQ
     */
    @PostMapping("/unmatched/{id}/convert")
    public R<Long> convertToFaq(@PathVariable Long id, @RequestBody FaqDTO dto) {
        Long faqId = faqService.saveOrUpdate(dto);
        // 标记未命中问题为已解决
        unmatchedMapper.updateStatus(id, "RESOLVED", faqId);
        customerServiceService.clearFaqCache();
        return R.success(faqId);
    }
}
```

#### 3. 未命中问题收集逻辑（Q9 客服 Service 改造）

```java
/**
 * 客服问答（增强：未命中收集）
 */
public String chat(String question, String userRole, Long userId) {
    // 1. FAQ 匹配
    Faq matched = matchFaq(question);
    if (matched != null) {
        // 命中 FAQ，增加查看次数
        faqMapper.incrementViewCount(matched.getId());
        return matched.getAnswer();
    }
    
    // 2. 未命中，记录到 unmatched 表（相同问题累加 hit_count）
    recordUnmatchedQuestion(question, userId);
    
    // 3. 调用 AI 兜底
    try {
        return deepSeekService.callAPIWithCache(...);
    } catch (Exception e) {
        return "抱歉，暂无法回答您的问题。已记录您的问题，客服将尽快补充解答。";
    }
}

/**
 * 记录未命中问题（相同问题累加次数）
 */
private void recordUnmatchedQuestion(String question, Long userId) {
    // 简单去重：按问题前 50 字符哈希
    String hash = DigestUtils.md5Hex(question.substring(0, Math.min(50, question.length())));
    CustomerServiceUnmatched existing = unmatchedMapper.selectByHash(hash);
    if (existing != null) {
        unmatchedMapper.incrementHitCount(existing.getId());
    } else {
        CustomerServiceUnmatched record = new CustomerServiceUnmatched();
        record.setQuestion(question);
        record.setUserId(userId);
        unmatchedMapper.insert(record);
    }
}
```

#### 4. 知识库更新闭环

```
用户提问 → FAQ 匹配 → 命中：返回答案 + view_count++
                     → 未命中：记录 unmatched + AI 兜底
                                        ↓
管理端查看 unmatched 列表（按 hit_count 降序）
                                        ↓
高频问题转 FAQ → 清理客服缓存 → 下次命中
```

**闭环说明**：知识库不是一次性建设，而是基于用户真实问题持续迭代。高频未命中问题转 FAQ 后，后续相同问题直接走 FAQ 匹配，减少 AI 调用成本（与 Q15 联动）。

#### 5. 技术栈选择

| 方案 | 技术 | 适合情况 | 本项目选择 |
|------|------|----------|------------|
| 轻量方案 | MySQL FAQ + LIKE 匹配 | MVP | ✅ 第一阶段（基础） |
| 增强方案 | MySQL 全文索引 + 权重排序 | 推荐 | ✅ 第一阶段（Q9 已有权重匹配） |
| 进阶方案 | PGVector / Elasticsearch | 后期 | ⏳ 第二阶段（D5 启用后） |

**完善方向**：
- 第一阶段实现 FAQ CRUD + 未命中收集 + 转换流程，约 1 天
- 第二阶段叠加 PGVector 语义匹配，提升 FAQ 命中率（同义词/近义词）
- 与 Q15 联动：FAQ 命中不消耗 AI 配额，降低成本

---

## 五、总体升级方向与最终技术路线

### 5.1 五大升级方向（从评估反馈提炼）

| 升级方向 | 从 | 到 | 对应问答 |
|----------|----|----|----------|
| **质量评估** | 能调用 AI | 能评估 AI 质量（反馈、评分、看板） | Q13 |
| **Prompt 管理** | Prompt 可配置 | Prompt 可版本化、可回滚、可灰度 | Q3 + Q14 |
| **成本控制** | 接口可用 | 成本可控、安全可控（配额、过滤） | Q15 + Q16 |
| **推荐可解释** | 推荐结果 | 可解释推荐结果（理由、风险、匹配度） | Q19 |
| **业务闭环** | 单次功能 | 业务闭环（历史、反馈、管理、持续优化） | Q17 + Q18 + Q20 |

### 5.2 最终技术路线（两阶段）

#### 第一阶段（30 天内，MVP 交付）

```
Spring Boot 3 + Vue 3 + DeepSeek API + MySQL + Redis + POI/PDFBox + Resilience4j + SSE
```

**技术栈明细**：
- **前端**：Vue 3 + Vite + Element Plus + ECharts（Q13 质量看板）
- **后端**：Spring Boot 3.2.5 + MyBatis-Plus 3.5.5
- **AI 接入**：RestTemplate（同步）+ WebClient（SSE 流式）+ DeepSeek API
- **数据存储**：MySQL 8（业务+JSON 字段）+ Redis（缓存+配额计数+面试会话）
- **文件解析**：Apache POI 5.2.5 + PDFBox 2.0.30（简历 PDF/DOCX 提取）
- **稳定性**：Resilience4j（重试/限流/熔断）+ OpenAI 兼容协议封装
- **流式输出**：SSE（仅客服+职业探索）
- **Prompt 管理**：分层混合（基础文件+业务 DB）+ 版本管理 + 回滚

**第一阶段交付能力**：
- ✅ 6 大 AI 功能全部可用（R-004/R-006/R-008/R-009/R-010/R-014）
- ✅ AI 质量评估与反馈闭环
- ✅ 用户级配额控制
- ✅ Prompt 版本管理与回滚
- ✅ 企业推荐可解释评分
- ✅ 模拟面试状态机
- ✅ FAQ 知识库管理闭环
- ✅ AI 调用监控看板

#### 第二阶段（演示后迭代，质量与可用性提升）

```
+ PostgreSQL PGVector（向量检索）+ 通义千问（多模型容灾）+ DFA 敏感词过滤
```

**第二阶段交付能力**：
- ✅ 企业候选人推荐语义匹配精度提升
- ✅ 客服 FAQ 同义词/近义词匹配
- ✅ 多模型容灾（主熔断自动切备）
- ✅ AI 输出安全过滤（敏感词+场景白名单+隐私脱敏）
- ✅ 简历优化版本对比（历史记录+Diff）

### 5.3 技术栈备选全景（评估反馈整合）

#### AI 接入层

| 方案 | 技术栈 | 优点 | 缺点 | 推荐度 |
|------|--------|------|------|--------|
| **当前选择** | Spring Boot + RestTemplate/WebClient + DeepSeek API | 改动小，适合现项目 | AI 抽象能力弱 | ✅ 高 |
| 框架增强 | Spring AI + DeepSeek/OpenAI Compatible API | 统一模型、Embedding、Prompt | 学习成本较高 | ⏳ 中 |
| Agent 方向 | LangChain4j | 工具调用、记忆、RAG 方便 | 引入成本高 | 中低 |
| Python AI 服务 | FastAPI + LangChain / LlamaIndex | AI 能力强 | 需拆服务 | 后期可选 |

#### RAG / 向量检索

| 方案 | 技术栈 | 适合场景 | 推荐 |
|------|--------|----------|------|
| 轻量版 | MySQL LIKE + 权重规则 | 30 天 MVP | ✅ 第一阶段 |
| **推荐版** | MySQL 主库 + PostgreSQL PGVector | 候选人、FAQ、岗位语义匹配 | ✅ 第二阶段 |
| 搜索增强 | Elasticsearch | 关键词检索、复杂筛选 | 后期 |
| 专业向量库 | Milvus / Qdrant | 大规模向量检索 | ❌ 暂不建议 |

#### 文档解析

| 方案 | 技术栈 | 推荐 |
|------|--------|------|
| **当前最优** | PDFBox + Apache POI | ✅ 推荐，依赖已存在 |
| 扩展格式 | Apache Tika | 后期支持 doc/rtf/html 时考虑 |
| OCR | PaddleOCR / Tesseract | 扫描版 PDF 后期补充 |

#### 流式输出

| 方案 | 技术栈 | 推荐 |
|------|--------|------|
| **SSE** | WebClient + Flux / SseEmitter | ✅ 推荐 |
| WebSocket | Spring WebSocket | 语音面试或双向实时场景再考虑 |
| 普通 REST | RestTemplate | 结构化 JSON 输出继续保留 |

#### 监控与日志

| 方案 | 技术栈 | 推荐 |
|------|--------|------|
| **轻量** | `ai_call_log` + 管理端 ECharts | ✅ 推荐 |
| 应用监控 | Spring Actuator + Micrometer | 可补充 |
| 完整链路 | Prometheus + Grafana | 后期 |

---

## 六、工程闭环收敛方案（小团队最简落地）

> **章节定位**：核心 AI 功能已基本覆盖，无新增大功能需求。本章将 Q13/Q14/Q15/D5 四类工程闭环**收敛为小团队能落地、AI 辅助编码易从零实现的最简版本**，并明确优先级与场景边界。
>
> **收敛原则**：
> 1. 每类闭环只给「最简实现」，增强版见对应问答（标注 ⬆️ 增强版）
> 2. 不引入额外中间件、不新增业务模块、不做服务化拆分
> 3. 优先级对齐评估反馈：P1（额度控制 + RAG 边界 + Prompt 版本）→ P2（反馈闭环）
> 4. 向量检索明确「第一阶段不做、第二阶段只覆盖两场景」，避免 RAG 方案变重

### 6.1 AI 质量反馈闭环（P2，最简实现）

**目标**：记录「AI 结果好不好」，为后续 Prompt 优化提供数据，不做复杂看板。

#### 最简实现：独立 `ai_feedback` 单表

```sql
-- AI 结果反馈表（最简版，独立于 ai_call_log，避免污染调用日志）
CREATE TABLE ai_feedback (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id     BIGINT COMMENT '反馈用户 ID',
    scene       VARCHAR(64) NOT NULL COMMENT 'AI 场景：RESUME/CAREER_EXPLORE/INTERVIEW/ENTERPRISE/CUSTOMER_SERVICE',
    biz_id      BIGINT COMMENT '关联业务记录 ID（如 resume_analysis.id / recommendation_record.id）',
    rating      TINYINT COMMENT '评分：1=没帮助 5=有帮助（或 0=不准确 1=准确，企业推荐场景）',
    feedback    TEXT COMMENT '可选文字反馈',
    created_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_scene_biz (scene, biz_id),
    INDEX idx_user (user_id)
) COMMENT='AI 结果反馈表（最简版）';
```

**各场景反馈入口**：

| 场景 | 反馈形式 | rating 取值 | biz_id 关联 |
|------|----------|-------------|-------------|
| R-008 简历优化 | 「有帮助 / 没帮助」 | 5 / 1 | `resume_analysis.id` |
| R-004 职业探索 | 「有帮助 / 没帮助」 | 5 / 1 | `career_profile.id` |
| R-014 智能客服 | 「有帮助 / 没帮助」 | 5 / 1 | `ai_call_log.id` |
| R-010 企业推荐 | HR 标记「准确 / 不准确」 | 1 / 0 | `recommendation_record.id` |
| R-009 模拟面试 | 「有帮助 / 没帮助」 | 5 / 1 | `interview_record.id` |

**管理端**：简单列表查询（按场景筛选、按 rating 排序），暂不做 ECharts 看板。

**工作量**：0.5 天（建表 + 5 个反馈接口 + 管理端列表页）

> ⬆️ **增强版见 Q13**：如需 ECharts 质量看板 + `ai_feedback_summary` 日汇总表 + 低质量样本分析，参考 Q13 实现（额外 +1 天）。

---

### 6.2 Prompt 版本管理（P1/P2，最简实现）

**目标**：Prompt 可回滚，避免越改越乱；不做灰度分流。

#### 最简实现：`prompt_template` 增加 version 字段

在 Q3 分层混合表结构基础上，新增 4 个字段：

```sql
-- 在 Q3 的 prompt_template 表基础上新增（最简版，不含 gray_ratio）
ALTER TABLE prompt_template ADD COLUMN version INT NOT NULL DEFAULT 1 COMMENT '版本号';
ALTER TABLE prompt_template ADD COLUMN scene VARCHAR(32) NOT NULL COMMENT '场景标识';
ALTER TABLE prompt_template ADD COLUMN is_active TINYINT NOT NULL DEFAULT 1 COMMENT '1=当前激活 0=历史版本';
ALTER TABLE prompt_template ADD COLUMN created_by BIGINT COMMENT '创建人（管理员 ID）';
```

**版本管理规则**：
- 每次编辑 Prompt → 新建一条记录，`version = max(version)+1`，`is_active=1`
- 同时将旧版本 `is_active` 置为 0（保留历史，不删除）
- 同一 `scene` 同一时刻只有 1 条 `is_active=1`

**回滚操作**：
```sql
-- 回滚到指定版本：将目标版本激活，当前版本停用
UPDATE prompt_template SET is_active = 0 WHERE scene = ? AND is_active = 1;
UPDATE prompt_template SET is_active = 1 WHERE scene = ? AND version = ?;
-- 清理 Prompt 缓存（Q3 的 clearCache）
```

**AI 调用记录版本**：
```sql
-- ai_call_log 记录本次使用的 Prompt 版本
ALTER TABLE ai_call_log ADD COLUMN prompt_version INT DEFAULT 0 COMMENT 'Prompt 版本（0=文件版本）';
```

**`loadTemplate` 改造**（在 Q3 基础上）：
```java
/**
 * 加载模板（最简版本管理：查 is_active=1 的最新版本）
 */
public String loadTemplate(String scene) {
    // 1. 查缓存（key 含 version，版本变更时清缓存）
    // 2. 查 DB：SELECT * FROM prompt_template WHERE scene=? AND is_active=1 LIMIT 1
    // 3. DB 命中 → 返回 content，记录 version 到 ThreadLocal
    // 4. DB 未命中 → 回退 classpath 文件，version=0
    // 5. ThreadLocal 的 version 供 ai_call_log 落库
}
```

**工作量**：0.5 天（4 个字段 + loadTemplate 改造 + 回滚接口 + ai_call_log 记录版本）

> ⬆️ **增强版见 Q14**：如需灰度分流（gray_ratio）、DRAFT/GRAY/ACTIVE/ARCHIVED 状态机、版本对比，参考 Q14 实现（额外 +0.5 天）。小团队建议先用最简版，演示反馈频繁后再升级。

---

### 6.3 AI 调用额度与成本控制（P1，最简实现）

**目标**：防止 API 被刷，控制成本；不做持久化统计表。

#### 最简实现：Redis 计数器 + 配额规则

```yaml
# application.yml 配额规则（可动态调整）
ai:
  quota:
    rules:
      # 角色:场景 -> 每日上限
      STUDENT:RESUME: 3           # 学生简历优化 3 次/天
      STUDENT:INTERVIEW: 2        # 学生模拟面试 2 次/天
      STUDENT:CAREER_EXPLORE: 5   # 学生职业探索 5 次/天
      STUDENT:CUSTOMER_SERVICE: 20 # 学生客服 20 次/天
      ENTERPRISE:PROJECT_PARSE: 5 # HR 项目解析 5 次/天
      ENTERPRISE:RECOMMEND: 10    # HR 候选人推荐 10 次/天
      ADMIN:*: 9999               # 管理员不限
```

**Redis Key 设计**：
```
ai:quota:{userId}:{scene}:{yyyyMMdd}  ->  count（TTL 1 天）
```

**校验逻辑**：
```java
/**
 * AI 调用配额校验（最简版，仅 Redis 计数，不持久化）
 */
public boolean checkQuota(Long userId, String role, String scene) {
    String key = String.format("ai:quota:%d:%s:%s", userId, scene, 
            LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE));
    int limit = quotaConfig.getLimit(role, scene);
    Long count = redisTemplate.opsForValue().increment(key);
    if (count == 1) {
        redisTemplate.expire(key, Duration.ofDays(1));
    }
    return count <= limit;
}
```

**业务层集成**（各 AI Service 调用前校验）：
```java
// ResumeServiceImpl 示例
if (!aiQuotaService.checkQuota(userId, "STUDENT", "RESUME")) {
    throw new BusinessException("今日简历优化次数已达上限（3 次/天），请明日再试");
}
```

**客服优先 FAQ 命中**（Q9 已实现，强调配置额联动）：
- FAQ 命中 → 直接返回，**不消耗 AI 配额**（不调 `checkQuota`）
- FAQ 未命中 → 调 AI 前 `checkQuota`，超限则返回「请明日再试」

**工作量**：0.5 天（Redis 计数器 + 配额配置 + 各 Service 前置校验）

> ⬆️ **增强版见 Q15**：如需 `ai_quota_log` 持久化表 + MySQL 汇总 + `call_source`(AI/CACHE/FALLBACK) 分类统计 + 管理端调用看板，参考 Q15 实现（额外 +0.5 天）。小团队建议先用 Redis 最简版，管理端统计需求出现后再加表。

---

### 6.4 RAG / 向量检索分阶段落地边界（P1，明确边界）

**目标**：避免 RAG 方案变重，明确「第一阶段不做、第二阶段只覆盖两场景」。

#### 分阶段边界（明确收敛）

| 阶段 | 检索方案 | 覆盖场景 | 不做的事 |
|------|----------|----------|----------|
| **第一阶段** | MySQL 关键词召回 + 规则评分 | 全部 6 个 AI 场景 | ❌ 不引入向量检索、不部署 PG |
| **第二阶段** | MySQL 关键词召回 + **PGVector 语义重排** | **仅 2 个场景**：① 智能客服 FAQ 匹配 ② 企业候选人/岗位匹配 | ❌ 不替换 MySQL、不做知识库平台 |

#### 第二阶段明确「不覆盖」的场景

| 场景 | 第二阶段是否启用 PGVector | 理由 |
|------|---------------------------|------|
| R-014 智能客服 FAQ 匹配 | ✅ 启用 | 同义词/近义词匹配，提升 FAQ 命中率 |
| R-010 企业候选人推荐 | ✅ 启用 | 岗位 JD 与候选人项目语义重排，提升匹配精度 |
| R-008 简历优化 | ❌ 不启用 | 单简历 vs 单 JD，无需向量检索，Prompt 塞全文即可 |
| R-004 职业探索 | ❌ 不启用 | DeepSeek 总结能力足够，数据量未达向量检索门槛 |
| R-009 模拟面试 | ❌ 不启用 | 题库固定，无需语义召回 |
| R-006 差距分析 | ❌ 不启用 | 规则计算差距 + AI 生成建议，无需向量检索 |

#### 明确「不引入」的技术

| 技术 | 是否引入 | 理由 |
|------|----------|------|
| PostgreSQL PGVector | ✅ 第二阶段 | 仅用于 2 场景语义重排，双库共存不迁移 MySQL |
| Milvus / Qdrant | ❌ 不引入 | 独立集群运维成本高，3 人团队无法承担 |
| Elasticsearch | ❌ 不引入 | 关键词检索 MySQL 已够用，不增加搜索中间件 |
| LangChain 服务化架构 | ❌ 不引入 | 不拆 Python AI 服务，保持 Spring Boot 单体 |
| 复杂知识库平台 | ❌ 不引入 | 仅 FAQ 表 + 未命中收集（Q20），不做知识图谱 |

**工作量**：第一阶段 0 天（不做），第二阶段约 4.5 天（PG 部署 + Embedding + 2 场景重排 + 双库同步）

> ⬆️ **完整方案见 D5 章节**：含双库共存架构、混合检索流程、HNSW 索引、Embedding 策略、异步同步。第二阶段实施时按 D5 章节执行，但**严格限制在上述 2 个场景**，不扩大范围。

---

### 6.5 工程闭环优先级与工作量汇总

| 闭环 | 优先级 | 最简实现 | 工作量 | 增强版 | 增强工作量 |
|------|--------|----------|--------|--------|------------|
| AI 调用额度控制 | **P1** | Redis 计数器 + 配额规则 | 0.5 天 | Q15（持久化表+看板） | +0.5 天 |
| RAG 分阶段边界 | **P1** | 第一阶段不做（文档明确边界） | 0 天 | D5（第二阶段 2 场景） | +4.5 天 |
| Prompt 版本管理 | **P1/P2** | version + is_active + 回滚 | 0.5 天 | Q14（灰度+状态机） | +0.5 天 |
| AI 质量反馈闭环 | **P2** | `ai_feedback` 单表 + 反馈接口 | 0.5 天 | Q13（看板+汇总表） | +1 天 |
| **最简版合计** | — | — | **1.5 天** | — | — |

**收敛结论**：4 类工程闭环最简版合计仅 1.5 天，3 人团队 0.5 天即可完成。增强版按需引入，不阻塞 MVP 交付。RAG 向量检索第二阶段严格限制 2 场景，避免方案变重。

### 5.4 与现有文档的关系

| 文档 | 定位 |
|------|------|
| [AI功能实现梳理与完善方向.md](file:///d:/HuaweiMoveData/Users/20403/Desktop/软件实训项目/.trae/documents/AI功能实现梳理与完善方向.md) | 基于代码现状的问答梳理（问题诊断） |
| [AI功能技术选型方案.md](file:///d:/HuaweiMoveData/Users/20403/Desktop/软件实训项目/.trae/documents/AI功能技术选型方案.md) | 技术选型多方案对比（决策依据，D1-D7 全部已确认） |
| **本文档** | 基于技术选型的实现与完善方向（执行指引） |

**本文档结构**：
- 第一~三章（Q1-Q12）：主线 AI 功能实现改造与技术选型落地
- 第四章（Q13-Q20）：运营闭环增强（质量评估/Prompt版本/额度控制/安全过滤/结构化存储/状态机/可解释推荐/知识库管理）
- 第五章：总体升级方向与最终技术路线（两阶段）
- **第六章：工程闭环收敛方案**（小团队最简落地，4 类闭环最简实现 + 优先级 + 场景边界）

三份文档关系：诊断 → 选型 → 执行。本文档是执行阶段的完整指引，核心 AI 功能已覆盖无新增大功能，第六章将工程闭环收敛为 1.5 天最简落地版本，可直接进入实施阶段。
