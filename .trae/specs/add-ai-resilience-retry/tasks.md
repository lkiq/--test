# Tasks

- [x] Task 1: 引入 Resilience4j 依赖
  - [x] SubTask 1.1: `backend/pom.xml` 新增 `resilience4j-spring-boot3` 依赖（v2.2.0）+ `spring-boot-starter-aop`
  - [x] SubTask 1.2: 执行 `mvn dependency:tree` 确认依赖解析正常，无版本冲突
- [x] Task 2: application-dev.yml 新增 Resilience4j 配置
  - [x] SubTask 2.1: 新增 `resilience4j.retry.instances.ai-call`（max-attempts=3, wait-duration=200ms, retry-exceptions=[RestClientException, TimeoutException]）
  - [x] SubTask 2.2: 新增 `resilience4j.circuitbreaker.instances.ai-call`（failureRateThreshold=50%, slidingWindowSize=10, minimumNumberOfCalls=5, waitDurationInOpenState=30s, permittedNumberOfCallsInHalfOpenState=1）
  - [x] SubTask 2.3: 配置 register-health-indicator=true，自动-transition-from-open-to-half-open-enabled=true
- [x] Task 3: DeepSeekServiceImpl 加注解
  - [x] SubTask 3.1: doCallAPI 方法加 `@Retry(name="ai-call", fallbackMethod="retryFallback")`
  - [x] SubTask 3.2: doCallAPI 方法加 `@CircuitBreaker(name="ai-call", fallbackMethod="circuitFallback")`
  - [x] SubTask 3.3: 新增 `retryFallback` 方法：记录重试耗尽日志，抛异常由业务层兜底
  - [x] SubTask 3.4: 新增 `circuitFallback` 方法：记录熔断日志，返回 null（业务层走兜底）
  - [x] SubTask 3.5: doCallAPI 从 private 改为 public（AOP 代理要求）
  - [x] SubTask 3.6: catch 块由 throw new RuntimeException 改为 throw e（重抛 RestClientException 让 @Retry 捕获）
  - [x] SubTask 3.7: 修复自调用问题 - 新增 @Autowired @Lazy self 字段，doCallAPI 调用改为 self.doCallAPI
- [x] Task 4: 熔断状态日志增强
  - [x] SubTask 4.1: 新建 ResilienceConfig.java，注册 CircuitBreaker.EventConsumer
  - [x] SubTask 4.2: 状态切换（OPEN/CLOSED/HALF_OPEN）输出 INFO 日志，调用被拒绝输出 WARN
- [x] Task 5: 编译验证
  - [x] SubTask 5.1: `mvn compile -q` 确认无错误（退出码 0）
  - [x] SubTask 5.2: `mvn package -DskipTests -q` 打包成功
- [x] Task 6: 运行时验证
  - [x] SubTask 6.1: 启动后端（35.8s 含 Resilience4j 初始化），连续 10 次职业探索 AI 成功率 10/10 (100%)，≥ 90% 目标
  - [x] SubTask 6.2: 智能客服调用验证 AI 调用成功（2150ms，source=AI），注解通过 self 代理生效
- [x] Task 7: Git commit

# Task Dependencies

- [Task 2] depends on [Task 1]（配置需依赖已引入）
- [Task 3] depends on [Task 2]（注解需配置就绪）
- [Task 4] depends on [Task 3]（日志增强基于注解生效）
- [Task 5] depends on [Task 1, 2, 3, 4]
- [Task 6] depends on [Task 5]
- [Task 7] depends on [Task 6]
