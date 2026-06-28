# AI 调用韧性增强 Spec

## Why

阶段 2 超时优化已落地（connectTimeout 6s、业务超时 5/6/8s、连接池、线程池隔离、缓存），连通性测试显示 curl 10/10 成功（connect 86-121ms）。但 Java HttpClient 偶发首次 TLS 握手 3-4s，叠加 DeepSeek 服务端偶发慢响应，仍会导致 connect timeout 或业务超时兜底。当前 doCallAPI 失败即兜底，无重试机制，单次抖动即影响用户体验。

本 spec 落地实施路线计划阶段 3 的第一项：**Resilience4j 重试 + 熔断**，作为偶发超时的根治方案。

## What Changes

- 引入 `resilience4j-spring-boot3` 依赖（v2.2.0，Spring Boot 3.2 兼容）
- 在 `DeepSeekServiceImpl.doCallAPI` 上叠加 `@Retry` 注解：connect timeout / 5xx 自动重试，最多 2 次重试，间隔 200ms
- 新增 `@CircuitBreaker` 注解：连续 5 次失败开启熔断，30s 半开探测，熔断期间直接走兜底
- 配置通过 `application.yml` 管理（`resilience4j.retry.*` / `resilience4j.circuitbreaker.*`）
- **BREAKING**：`doCallAPI` 失败行为变化 — 原直接抛异常走兜底，现先重试 2 次再兜底；熔断开启期间所有调用立即兜底

## Impact

- Affected specs: [optimize-api-timeout]（超时优化的韧性补充）
- Affected code:
  - `backend/pom.xml`（新增 resilience4j 依赖）
  - `backend/src/main/resources/application-dev.yml`（新增 resilience4j 配置块）
  - `backend/src/main/java/com/xuelian/career/service/impl/DeepSeekServiceImpl.java`（doCallAPI 加注解）
  - `backend/src/main/java/com/xuelian/career/config/AppConfig.java`（新增 CircuitBreaker/Retry 配置 Bean，可选）

## ADDED Requirements

### Requirement: AI 调用重试机制

系统 SHALL 在 DeepSeek API 调用失败时（connect timeout、read timeout、5xx 响应）自动重试，最多重试 2 次，每次间隔 200ms，重试失败后才走兜底逻辑。

#### Scenario: 首次连接超时后重试成功
- **WHEN** DeepSeek API 首次调用因 connect timeout 失败
- **THEN** 系统在 200ms 后自动重试
- **AND** 重试成功则返回 AI 结果（source=AI）
- **AND** 重试失败则走兜底（source=FALLBACK）

#### Scenario: 重试次数耗尽
- **WHEN** 连续 3 次调用（1 次原始 + 2 次重试）均失败
- **THEN** 记录失败日志到 ai_call_log
- **AND** 抛出异常由业务层走兜底
- **AND** 累计熔断器失败计数

### Requirement: AI 调用熔断机制

系统 SHALL 在 DeepSeek API 连续失败 5 次后开启熔断器，熔断期间所有 AI 调用立即走兜底（不发起 HTTP 请求），30 秒后进入半开状态探测恢复。

#### Scenario: 连续失败触发熔断
- **WHEN** 最近 5 次 AI 调用全部失败
- **THEN** 熔断器开启（OPEN 状态）
- **AND** 后续 AI 调用直接走兜底（不发 HTTP 请求，duration < 5ms）
- **AND** 日志记录 "CircuitBreaker OPEN，跳过 AI 调用"

#### Scenario: 熔断半开探测成功
- **WHEN** 熔断 30 秒后
- **THEN** 熔断器进入半开（HALF_OPEN）状态
- **AND** 放行 1 次探测调用
- **AND** 探测成功则熔断器关闭（CLOSED），恢复正常调用
- **AND** 探测失败则继续熔断 30 秒

## MODIFIED Requirements

### Requirement: AI 调用失败兜底

原行为：doCallAPI 失败立即抛异常，业务层 catch 后走兜底。

新行为：doCallAPI 失败后先由 Resilience4j 重试 2 次；重试耗尽后抛异常；熔断器 OPEN 期间 doCallAPI 不执行（直接返回 null 或抛 CircuitBreakerOpenException），业务层走兜底。业务层 catch 逻辑不变。

## REMOVED Requirements

无（不移除任何现有功能，仅增强）。
