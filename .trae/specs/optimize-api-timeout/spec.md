# API 超时优化 Spec

## Why

当前 DeepSeek API 调用存在三大根因导致超时率约 70%：`max_tokens=4096` 过大（生成 15-30 秒）、`SimpleClientHttpRequestFactory` 无连接池（每次多耗 300-800ms TCP+TLS 握手）、读取超时 30 秒过长（用户等 30 秒才报错）。本次改造将 API 调用 95% 在 5 秒内返回，超时率降到 ~10%，并为后续 Resilience4j 重试/限流/熔断（阶段 3）铺平道路——超时不解决，重试会加剧问题。

## What Changes

- **改动 1.1**：`DeepSeekService` 接口新增 2 个重载方法（带 `timeoutMs` 和 `maxTokens` 参数）
- **改动 1.2**：`DeepSeekServiceImpl` 实现重载方法，将 `temperature`（0.7→参数化）和 `max_tokens`（4096→参数化）改为参数传入，原方法委托新方法（向后兼容）
- **改动 1.3**：`AppConfig` 将 `SimpleClientHttpRequestFactory` 替换为 `JdkClientHttpRequestFactory`（Java 17 原生 HttpClient，自带连接复用），读取超时 30 秒→6 秒
- **改动 1.4**：6 个业务调用方传入差异化参数（`max_tokens` 256/512/768，超时 5s/6s/8s）
- **改动 1.5（可选）**：`DeepSeekServiceImpl` 新增 10 线程独立线程池 + `CompletableFuture` 异步包装，隔离 Tomcat 线程

## Impact

- **Affected specs**：无（基础设施层改造，无对外接口破坏）
- **Affected code**：
  - [DeepSeekService.java](file:///d:/HuaweiMoveData/Users/20403/Desktop/软件实训项目/软件实训项目-code/backend/src/main/java/com/xuelian/career/service/DeepSeekService.java) — 接口新增 2 个重载方法
  - [DeepSeekServiceImpl.java](file:///d:/HuaweiMoveData/Users/20403/Desktop/软件实训项目/软件实训项目-code/backend/src/main/java/com/xuelian/career/service/impl/DeepSeekServiceImpl.java) — 实现重载 + 参数化 temperature/max_tokens + 可选线程池
  - [AppConfig.java](file:///d:/HuaweiMoveData/Users/20403/Desktop/软件实训项目/软件实训项目-code/backend/src/main/java/com/xuelian/career/config/AppConfig.java) — 替换 HTTP 工厂
  - [AssessmentServiceImpl.java](file:///d:/HuaweiMoveData/Users/20403/Desktop/软件实训项目/软件实训项目-code/backend/src/main/java/com/xuelian/career/service/impl/AssessmentServiceImpl.java) — 测评建议
  - [CareerExplorationServiceImpl.java](file:///d:/HuaweiMoveData/Users/20403/Desktop/软件实训项目/软件实训项目-code/backend/src/main/java/com/xuelian/career/service/impl/CareerExplorationServiceImpl.java) — 职业探索
  - [InterviewServiceImpl.java](file:///d:/HuaweiMoveData/Users/20403/Desktop/软件实训项目/软件实训项目-code/backend/src/main/java/com/xuelian/career/service/impl/InterviewServiceImpl.java) — 面试出题 + 面试评价（2 处）
  - [ResumeServiceImpl.java](file:///d:/HuaweiMoveData/Users/20403/Desktop/软件实训项目/软件实训项目-code/backend/src/main/java/com/xuelian/career/service/impl/ResumeServiceImpl.java) — 简历优化
  - [CustomerServiceServiceImpl.java](file:///d:/HuaweiMoveData/Users/20403/Desktop/软件实训项目/软件实训项目-code/backend/src/main/java/com/xuelian/career/service/impl/CustomerServiceServiceImpl.java) — 智能客服

## ADDED Requirements

### Requirement: 差异化 AI 调用参数

系统 SHALL 提供带 `timeoutMs` 和 `maxTokens` 参数的 AI 调用重载方法，使业务调用方能按场景调优生成速度与完整度。

#### Scenario: 结构化输出场景（测评/简历/客服）
- **WHEN** 业务调用 AI 生成结构化 JSON（测评建议/简历优化/智能客服）
- **THEN** 使用 `max_tokens=512` + `timeoutMs=5000` + `temperature=0.3`，512 token 足够且生成稳定

#### Scenario: 长文本输出场景（面试评价/职业探索）
- **WHEN** 业务调用 AI 生成多维度评估或长文本推荐（面试评价/职业探索）
- **THEN** 使用 `max_tokens=768` + `timeoutMs=6000~8000`，避免 JSON 截断

#### Scenario: 单题生成场景（面试出题）
- **WHEN** 业务调用 AI 生成单道面试题
- **THEN** 使用 `max_tokens=256` + `timeoutMs=6000` + `temperature=0.6`，256 token 够用且保留对话发散性

### Requirement: HTTP 连接池复用

系统 SHALL 使用 `JdkClientHttpRequestFactory`（Java 17 原生 HttpClient）替代 `SimpleClientHttpRequestFactory`，实现 TCP+TLS 连接复用，每次调用节省 300-800ms 握手开销。

#### Scenario: 连续多次 AI 调用
- **WHEN** 连续发起 10 次 AI 调用
- **THEN** HttpClient 自动复用 keep-alive 连接，`ai_call_log.duration_ms` 平均较改造前减少 300-800ms

### Requirement: 读取超时合理化

系统 SHALL 将 RestTemplate 读取超时从 30 秒降至 6 秒（略大于业务 5 秒硬超时），避免用户长时间等待。

#### Scenario: API 响应缓慢
- **WHEN** DeepSeek API 响应超过 6 秒
- **THEN** HttpClient 触发读取超时，业务层走兜底逻辑，用户在 6 秒内得到兜底响应

### Requirement: 独立线程池隔离 AI 调用（可选）

系统 SHOULD 使用 10 线程独立线程池（daemon 线程）包装 AI 调用，隔离 Tomcat 线程，避免并发 20 个 AI 调用时 Tomcat 线程紧张。

#### Scenario: 并发 AI 调用
- **WHEN** 并发发起 20 个 AI 调用
- **THEN** AI 调用由独立线程池处理，Tomcat 线程不被阻塞，应用整体响应能力不受影响

## MODIFIED Requirements

### Requirement: DeepSeekService 接口

原接口仅 4 个方法（`callAPI`、`callAPIWithCache`、`parseJSONResponse`、`isAvailable`），所有场景统一 `max_tokens=4096` + 30 秒超时。修改后新增 2 个重载方法支持差异化参数，原方法保留并向后兼容（委托新方法，默认 5000ms + 512 token + temperature 0.3）。

### Requirement: DeepSeekServiceImpl 实现

原实现硬编码 `temperature=0.7`（L89）和 `max_tokens=4096`（L90），同步阻塞调用（L97-L102）。修改后将 temperature 和 max_tokens 参数化，抽取核心调用逻辑为 `doCallAPI` 私有方法，原 `callAPI` 委托新重载方法。

### Requirement: AppConfig RestTemplate Bean

原配置使用 `SimpleClientHttpRequestFactory` + `setConnectTimeout(10000)` + `setReadTimeout(30000)`，无连接池。修改后使用 `JdkClientHttpRequestFactory` + `HttpClient.newBuilder()` + `connectTimeout(3s)` + `readTimeout(6s)`，零新增依赖（Spring 6.1 原生支持）。
