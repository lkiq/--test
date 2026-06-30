# Tasks

- [x] Task 1: DeepSeekService 接口新增 2 个重载方法
  - [x] SubTask 1.1: 新增 `callAPI(String systemPrompt, String userPrompt, long timeoutMs, int maxTokens)` 方法声明
  - [x] SubTask 1.2: 新增 `callAPIWithCache(String cacheKey, String systemPrompt, String userPrompt, long ttlSeconds, int maxTokens)` 方法声明
- [x] Task 2: DeepSeekServiceImpl 实现重载 + 参数化 temperature/max_tokens
  - [x] SubTask 2.1: 抽取核心调用逻辑为私有方法 `doCallAPI(systemPrompt, userPrompt, timeoutMs, maxTokens, temperature)`
  - [x] SubTask 2.2: 实现 `callAPI(sys, prompt, timeoutMs, maxTokens)` 重载（默认 temperature 0.3）
  - [x] SubTask 2.3: 实现 `callAPIWithCache(cacheKey, sys, prompt, ttlSeconds, maxTokens)` 重载
  - [x] SubTask 2.4: 原 `callAPI(sys, prompt)` 委托新方法（默认 5000ms + 512 token + 0.3）
  - [x] SubTask 2.5: 原 `callAPIWithCache(cacheKey, sys, prompt, ttlSeconds)` 委托新方法（默认 512 token）
  - [x] SubTask 2.6: 将 L89 `temperature` 0.7 和 L90 `max_tokens` 4096 改为参数传入
- [x] Task 3: AppConfig 替换为 JdkClientHttpRequestFactory（连接池）
  - [x] SubTask 3.1: 删除 `SimpleClientHttpRequestFactory` 及 `setConnectTimeout(10000)` / `setReadTimeout(30000)`
  - [x] SubTask 3.2: 新增 `HttpClient.newBuilder()` 配置（connectTimeout 3 秒 + 10 线程异步执行器）
  - [x] SubTask 3.3: 新增 `JdkClientHttpRequestFactory` + `setReadTimeout(Duration.ofSeconds(6))`
- [x] Task 4: 6 个业务调用方传入差异化参数
  - [x] SubTask 4.1: AssessmentServiceImpl L260 改为 `callAPI(sys, prompt, 5000L, 512)`（测评建议）
  - [x] SubTask 4.2: CareerExplorationServiceImpl L67 改为 `callAPIWithCache(cacheKey, sys, prompt, 8000L, 768)`（职业探索，走缓存）
  - [x] SubTask 4.3: InterviewServiceImpl L265 改为 `callAPI(sys, prompt, 6000L, 256)`（面试出题）
  - [x] SubTask 4.4: InterviewServiceImpl L307 改为 `callAPI(sys, prompt, 6000L, 768)`（面试评价）
  - [x] SubTask 4.5: ResumeServiceImpl L56 改为 `callAPI(sys, prompt, 5000L, 512)`（简历优化）
  - [x] SubTask 4.6: CustomerServiceServiceImpl L98 改为 `callAPIWithCache(cacheKey, sys, prompt, 3600L, 512)`（智能客服）
- [x] Task 5（可选）: DeepSeekServiceImpl 新增独立线程池 + CompletableFuture 异步包装
  - [x] SubTask 5.1: 类顶部新增 `aiCallExecutor`（10 线程 daemon 线程池）
  - [x] SubTask 5.2: `doCallAPI` 中将同步 `restTemplate.exchange` 用 `CompletableFuture.supplyAsync(..., aiCallExecutor)` 包装
  - [x] SubTask 5.3: 新增 `@PreDestroy shutdown()` 方法销毁线程池
- [x] Task 6: 编译验证
  - [x] SubTask 6.1: 执行 `mvn compile`，确认 BUILD SUCCESS 无编译错误
- [x] Task 7: Git commit（合并为 1 个 commit，便于精准回滚）

# Task Dependencies

- [Task 2] depends on [Task 1]（实现需先有接口声明）
- [Task 4] depends on [Task 2]（业务调用方需先有重载方法可用）
- [Task 5] depends on [Task 2]（异步包装需在 doCallAPI 抽取后进行）
- [Task 6] depends on [Task 1, 2, 3, 4, 5]（编译验证需所有改动完成）
- [Task 7] depends on [Task 6]（编译通过后才能提交）
- [Task 1, 3] 无依赖，可并行
