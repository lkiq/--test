# AI 智能求职辅导平台 — AI 功能实现梳理与完善方向（问答式）

> 本文档基于对《需求规格说明书 V1.0》《概要设计说明书 V1.0》《项目评分报告 V1.1》以及实际代码库的逐文件审阅，以问答形式梳理项目中所有 AI 相关功能的实现现状、问题与完善方向。
>
> 代码基线路径：`软件实训项目-code/软件实训项目-code/软件实训项目-第一版/`

---

## 一、AI 整体架构

### Q1：项目 AI 能力的总体架构是怎样的？

项目采用「单体后端 + 云端 AI 服务」的架构，AI 能力集中由后端封装，前端不直接调用 AI。整体分为三层：

| 层级 | 实现类 / 文件 | 职责 |
|------|---------------|------|
| AI 基础设施层 | [DeepSeekServiceImpl.java](file:///d:/HuaweiMoveData/Users/20403/Desktop/软件实训项目/软件实训项目-code/软件实训项目-code/软件实训项目-第一版/backend/src/main/java/com/xuelian/career/service/impl/DeepSeekServiceImpl.java)、[DeepSeekConfig.java](file:///d:/HuaweiMoveData/Users/20403/Desktop/软件实训项目/软件实训项目-code/软件实训项目-code/软件实训项目-第一版/backend/src/main/java/com/xuelian/career/config/DeepSeekConfig.java)、[PromptTemplateUtil.java](file:///d:/HuaweiMoveData/Users/20403/Desktop/软件实训项目/软件实训项目-code/软件实训项目-code/软件实训项目-第一版/backend/src/main/java/com/xuelian/career/util/PromptTemplateUtil.java) | 封装 DeepSeek API 调用、Redis 缓存、Prompt 模板渲染、调用日志记录 |
| AI 业务服务层 | CareerExplorationServiceImpl / ResumeServiceImpl / InterviewServiceImpl / CustomerServiceServiceImpl / EnterpriseServiceImpl / GapAnalysisServiceImpl | 组装 Prompt、调用 AI、解析结果、降级兜底 |
| 数据持久层 | [AiCallLog.java](file:///d:/HuaweiMoveData/Users/20403/Desktop/软件实训项目/软件实训项目-code/软件实训项目-code/软件实训项目-第一版/backend/src/main/java/com/xuelian/career/entity/AiCallLog.java) + `ai_call_log` 表 | 记录每次 AI 调用的场景、Prompt 摘要、请求哈希、响应来源、状态、耗时、错误信息 |

外部依赖为 DeepSeek Chat Completions API（HTTPS REST），未本地部署大模型，不依赖向量数据库，采用 MySQL 结构化数据 + 关键词召回 + DeepSeek 总结的轻量 RAG 思路。

---

### Q2：DeepSeek 基础服务封装了哪些能力？实现质量如何？

[DeepSeekServiceImpl.java](file:///d:/HuaweiMoveData/Users/20403/Desktop/软件实训项目/软件实训项目-code/软件实训项目-code/软件实训项目-第一版/backend/src/main/java/com/xuelian/career/service/impl/DeepSeekServiceImpl.java) 提供 4 个核心方法：

| 方法 | 能力 | 实现评价 |
|------|------|----------|
| `callAPI` | 调用 DeepSeek，含重试 + 退避 | ⚠️ 重试固定 1 次、退避固定 1 秒，无指数退避；失败抛 `RuntimeException` |
| `callAPIWithCache` | Redis 缓存调用 | ✅ 基于 SHA-256 哈希去重，TTL 可配 |
| `parseJSONResponse` | 解析 JSON（去除 markdown 包裹） | ✅ 能处理 ` ```json ``` ` 包裹 |
| `isAvailable` | API 可用性探测 | ⚠️ 仅基于 Redis 标记（60 秒 TTL），无主动心跳探测 |

**亮点**：每次调用都写入 `ai_call_log` 表，记录 `responseSource`（AI/CACHE/FALLBACK）、`status`（SUCCESS/FAILED）、`durationMs`、`fallbackReason`，便于监控与降级分析。

**不足**：
- 重试策略简单，未区分网络异常与业务异常（如 429 限流、4xx 参数错误）
- 无并发限流，高并发场景可能触发 DeepSeek 限流
- `isAvailable` 未缓存命中时默认返回 `true`，可能在 API 故障期持续打失败请求

---

### Q3：Prompt 模板是如何管理的？

使用 [PromptTemplateUtil.java](file:///d:/HuaweiMoveData/Users/20403/Desktop/软件实训项目/软件实训项目-code/软件实训项目-code/软件实训项目-第一版/backend/src/main/java/com/xuelian/career/util/PromptTemplateUtil.java) 从 `classpath:prompts/` 加载 `.txt` 模板，支持 `{{placeholder}}` 占位符替换，模板内容缓存在 `ConcurrentHashMap` 中。

**已存在的 5 个模板**（位于 `backend/src/main/resources/prompts/`）：

| 模板文件 | 用途 | 实际被使用 |
|----------|------|-----------|
| `career_exploration.txt` | 职业方向探索 | ✅ |
| `resume_optimize.txt` | 简历优化 | ✅ |
| `mock_interview.txt` | 模拟面试（含 FIRST_QUESTION / FOLLOW_UP / EVALUATION 三阶段） | ⚠️ 仅用 FIRST_QUESTION 和 EVALUATION |
| `customer_service.txt` | 智能客服 | ✅ |
| `project_parse.txt` | 企业项目需求解析 | ❌ **模板存在但代码未调用** |

**不足**：模板缓存无热更新机制（虽有 `clearCache()` 方法但无调用入口），修改模板需重启服务。

---

## 二、各 AI 功能模块实现现状

### Q4：AI 职业方向探索（R-004）实现得如何？

**实现完整度：✅ 较好**

[CareerExplorationServiceImpl.java](file:///d:/HuaweiMoveData/Users/20403/Desktop/软件实训项目/软件实训项目-code/软件实训项目-code/软件实训项目-第一版/backend/src/main/java/com/xuelian/career/service/impl/CareerExplorationServiceImpl.java) 实现了完整的 AI 调用链：

1. 组装用户画像 + 测评结果 + 兴趣偏好 + 岗位列表到 Prompt
2. 调用 `callAPIWithCache`（cacheKey = `explore:userId`）
3. 解析 JSON 返回 `directions` 数组（岗位、匹配分、理由、成长路径）
4. 失败时兜底：取岗位库前 5 个，固定分数 70-90

**问题**：
- 缓存键 `explore:userId` 未考虑用户画像更新，画像变更后仍返回旧推荐
- 兜底方案质量低：固定取前 5 个岗位，未结合测评分数做排序

---

### Q5：AI 简历优化（R-008）实现得如何？

**实现完整度：⚠️ 存在严重缺陷**

[ResumeServiceImpl.java](file:///d:/HuaweiMoveData/Users/20403/Desktop/软件实训项目/软件实训项目-code/软件实训项目-code/软件实训项目-第一版/backend/src/main/java/com/xuelian/career/service/impl/ResumeServiceImpl.java) 虽然调用了 DeepSeek，但存在关键问题：

```java
// ResumeServiceImpl.java 第 52 行
params.put("resume_text", "[简历文件: " + fileUrl + "]");
```

**核心问题**：`resume_text` 参数传入的只是文件 URL 字符串，**没有真正提取 PDF/DOCX 简历文本内容**。AI 收到的是 `[简历文件: resumes/1/xxx.pdf]` 这样的占位文本，根本无法分析简历实际内容。

**其他问题**：
- 未调用 Apache PDFBox / Apache POI 等库解析文件内容
- `skill_requirements` 参数固定传空字符串
- 未做文件大小/格式校验（需求要求限制格式和大小）
- 兜底方案返回固定分数 70，与实际简历无关

**这是当前 AI 功能中最大的实现缺陷，导致简历优化功能形同虚设。**

---

### Q6：AI 模拟面试（R-009）实现得如何？

**实现完整度：⚠️ 基本可用，但缺少追问能力**

[InterviewServiceImpl.java](file:///d:/HuaweiMoveData/Users/20403/Desktop/软件实训项目/软件实训项目-code/软件实训项目-code/软件实训项目-第一版/backend/src/main/java/com/xuelian/career/service/impl/InterviewServiceImpl.java) 实现了：

- Redis 存储面试会话（30 分钟 TTL）
- AI 出题（FIRST_QUESTION 阶段）
- AI 综合评估（EVALUATION 阶段）
- 本地题库兜底（5 道固定题）
- 本地评分兜底（随机 75-95 分）

**与需求不符的关键缺失**：需求 3.2.8 明确要求「AI 根据回答进行**最多 2 轮追问**」，但代码中：

```java
// mock_interview.txt 模板定义了 FOLLOW_UP 阶段
// 但 InterviewServiceImpl 中 current_stage 只使用了：
//   - "FIRST_QUESTION"（出题）
//   - "EVALUATION"（评估）
// 从未使用 "FOLLOW_UP"
```

`submitAnswer` 方法在用户回答后直接进入下一题，没有追问逻辑。模板中精心设计的 FOLLOW_UP 输出格式（`action: FOLLOW_UP / NEXT_QUESTION`）完全未被使用。

**其他问题**：
- 固定 5 道题，未按需求「5-8 道」动态调整
- 本地兜底评分用 `Math.random()`，同一用户多次面试分数不一致

---

### Q7：企业项目需求解析与候选人推荐（R-010）实现得如何？

**实现完整度：❌ 严重缺失，AI 功能完全未实现**

这是与需求差距最大的模块。[EnterpriseServiceImpl.java](file:///d:/HuaweiMoveData/Users/20403/Desktop/软件实训项目/软件实训项目-code/软件实训项目-code/软件实训项目-第一版/backend/src/main/java/com/xuelian/career/service/impl/EnterpriseServiceImpl.java) 的实际逻辑：

```java
// 1. 岗位推荐：直接取岗位库前 3 个，固定 headcount=2，固定技能 "Java-熟练"
List<JobPosition> positions = jobPositionMapper.selectList(...);
List<...> suggestedPositions = positions.stream().limit(3).map(...)

// 2. 候选人匹配：分数全部用随机数
double skillScore = 70 + Math.random() * 20;
double learningScore = 60 + Math.random() * 30;
```

**问题清单**：
- ❌ **从未调用 DeepSeekService**，连 `isAvailable()` 都没调用
- ❌ **从未使用 `project_parse.txt` 模板**（模板已存在但被遗忘）
- ❌ 项目描述输入完全被忽略，`projectSummary` 只是截取前 100 字符
- ❌ 候选人匹配分用 `Math.random()` 生成，每次结果不同
- ❌ 技能要求硬编码为 "Java-熟练"，与项目描述无关
- ❌ `getHistory` 返回空列表，未持久化推荐记录

**这与需求 3.3.1「DeepSeek 解析项目模块、建议岗位、技能标签和人数」严重不符，是当前最需要优先修复的模块。**

---

### Q8：智能客服（R-014）实现得如何？

**实现完整度：✅ 较好，三层降级策略清晰**

[CustomerServiceServiceImpl.java](file:///d:/HuaweiMoveData/Users/20403/Desktop/软件实训项目/软件实训项目-code/软件实训项目-code/软件实训项目-第一版/backend/src/main/java/com/xuelian/career/service/impl/CustomerServiceServiceImpl.java) 实现了三层策略：

1. **FAQ 关键词匹配**（最快）：从 `faq` 表按关键词命中返回
2. **DeepSeek AI 兜底**：将全部 FAQ 作为上下文喂给 AI，带 1800 秒缓存
3. **固定回答兜底**：提示用户联系管理员

**亮点**：把 FAQ 知识库作为 Prompt 上下文，是项目中最接近 RAG 思想的实现。

**不足**：
- 缓存键 `cs:role:hash(question)` 对问题微调敏感，相似问题无法复用
- FAQ 匹配是简单的 `contains`，无语义相似度
- 无多轮对话上下文支持

---

### Q9：能力差距分析（R-006）是否用了 AI？

**实现完整度：⚠️ 纯规则实现，未使用 AI（与模块标题不符）**

[GapAnalysisServiceImpl.java](file:///d:/HuaweiMoveData/Users/20403/Desktop/软件实训项目/软件实训项目-code/软件实训项目-code/软件实训项目-第一版/backend/src/main/java/com/xuelian/career/service/impl/GapAnalysisServiceImpl.java) 完全没有注入 `DeepSeekService`，纯规则计算：

- 用户技能等级按「是否在 skillTags 列表中」二值判断（掌握/未掌握）
- 差距程度按等级差值映射（完全达标/基本达标/需要提升/严重不足）
- `suggestions` 固定为 "建议优先提升优先级较高的技能缺口"

**问题**：
- 需求 3.2.5 标题为「AI 岗位能力差距分析」，但实现无 AI 参与
- 用户技能等级判断过于粗糙（只有「掌握/未掌握」两档，丢失了「了解/熟练/精通」）
- 学习建议无个性化，无法指导用户具体如何提升

---

## 三、AI 功能整体问题汇总

### Q10：当前 AI 功能存在哪些共性问题？

| 问题类别 | 具体表现 | 影响模块 |
|----------|----------|----------|
| **AI 未接入** | 代码完全未调用 DeepSeek | 企业推荐、差距分析 |
| **AI 调用缺陷** | 调用了 AI 但输入数据无效 | 简历优化（未提取文本） |
| **需求未实现** | 模板设计了但代码未用 | 模拟面试追问（FOLLOW_UP） |
| **兜底质量低** | 随机数/固定值兜底 | 企业推荐、面试评分 |
| **缓存策略粗** | 按用户 ID 缓存，不随数据更新失效 | 职业探索 |
| **重试机制弱** | 固定 1 秒退避，重试 1 次 | DeepSeekService |
| **配置安全** | application-dev.yml 仍有 API Key 默认值 | 全局 |
| **无流式输出** | 全部同步阻塞，用户等待 15-20 秒 | 所有 AI 接口 |

---

## 四、功能完善方向（按优先级）

### Q11：哪些是 P0 优先级（必须修复的核心缺陷）？

#### P0-1：修复企业项目需求解析的 AI 接入
- **位置**：[EnterpriseServiceImpl.java](file:///d:/HuaweiMoveData/Users/20403/Desktop/软件实训项目/软件实训项目-code/软件实训项目-code/软件实训项目-第一版/backend/src/main/java/com/xuelian/career/service/impl/EnterpriseServiceImpl.java)
- **方案**：注入 `DeepSeekService` 和 `PromptTemplateUtil`，使用已存在的 `project_parse.txt` 模板，将项目描述 + 技能词典作为输入，解析出岗位和技能要求后再做候选人匹配
- **预期**：让 R-010 真正具备 AI 演示价值，这是企业端唯一亮点

#### P0-2：修复简历优化的文本提取
- **位置**：[ResumeServiceImpl.java](file:///d:/HuaweiMoveData/Users/20403/Desktop/软件实训项目/软件实训项目-code/软件实训项目-第一版/backend/src/main/java/com/xuelian/career/service/impl/ResumeServiceImpl.java)
- **方案**：引入 Apache PDFBox（PDF）+ Apache POI（DOCX）提取文本，再传入 Prompt 的 `resume_text` 参数
- **预期**：让 AI 能真正读到简历内容，否则简历优化功能无意义

#### P0-3：配置安全收尾
- **位置**：[application-dev.yml](file:///d:/HuaweiMoveData/Users/20403/Desktop/软件实训项目/软件实训项目-code/软件实训项目-code/软件实训项目-第一版/backend/src/main/resources/application-dev.yml)
- **方案**：移除 `sk-18e83e105a634192aa0e745eb0d66c7d` 默认值，改为 `${DEEPSEEK_API_KEY:}` 强制环境变量注入

---

### Q12：哪些是 P1 优先级（需求未完整实现）？

#### P1-1：实现模拟面试追问逻辑
- **位置**：[InterviewServiceImpl.java](file:///d:/HuaweiMoveData/Users/20403/Desktop/软件实训项目/软件实训项目-code/软件实训项目-第一版/backend/src/main/java/com/xuelian/career/service/impl/InterviewServiceImpl.java) `submitAnswer` 方法
- **方案**：在用户回答后，先用 `current_stage=FOLLOW_UP` 调用 AI 判断是否需要追问；AI 返回 `action=FOLLOW_UP` 则追问，返回 `NEXT_QUESTION` 则进入下一题；每题最多追问 2 轮（需求 3.2.8）
- **模板已就绪**：`mock_interview.txt` 已定义 FOLLOW_UP 输出格式，只需补代码

#### P1-2：差距分析引入 AI 个性化建议
- **位置**：[GapAnalysisServiceImpl.java](file:///d:/HuaweiMoveData/Users/20403/Desktop/软件实训项目/软件实训项目-code/软件实训项目-第一版/backend/src/main/java/com/xuelian/career/service/impl/GapAnalysisServiceImpl.java)
- **方案**：规则计算差距后，将差距报告 + 用户画像交给 DeepSeek，生成个性化的提升建议和学习重点
- **新增**：需创建 `gap_analysis.txt` 模板

#### P1-3：AI 重试与降级机制完善
- **位置**：[DeepSeekServiceImpl.java](file:///d:/HuaweiMoveData/Users/20403/Desktop/软件实训项目/软件实训项目-code/软件实训项目-第一版/backend/src/main/java/com/xuelian/career/service/impl/DeepSeekServiceImpl.java)
- **方案**：引入指数退避（1s → 2s → 4s）；区分异常类型（429 限流 → 长退避，4xx → 不重试，5xx/网络 → 短重试）；引入简单令牌桶限流
- **对应评分报告**：V1.1 已标注「P1 AI 调用重试+降级机制」

---

### Q13：哪些是 P2 优先级（体验与质量提升）？

#### P2-1：流式输出（SSE）支持
- 当前所有 AI 接口同步阻塞，用户需等待 15-20 秒
- 改造为 SSE 流式返回，首字响应可降至 2-3 秒，显著改善体验
- 涉及：DeepSeekService 增加 `callAPIStream` 方法 + Controller 改为 `SseEmitter`

#### P2-2：缓存失效策略优化
- 职业探索缓存键加入画像更新时间戳或版本号
- 客服缓存改为基于问题语义相似度的召回
- 简历分析、面试评估等个性化场景不应缓存

#### P2-3：兜底方案质量提升
- 企业推荐兜底：改用项目描述关键词匹配岗位模板，替代随机数
- 面试评分兜底：基于回答长度、关键词命中做规则评分，替代 `Math.random()`
- 简历兜底：基于文件名/扩展名给出差异化建议

#### P2-4：AI 调用监控看板
- 基于 `ai_call_log` 表，在管理端看板新增 AI 调用统计：成功率、平均耗时、降级次数、各场景调用量
- 当降级率超阈值时告警

---

### Q14：哪些是 P3 优先级（长期演进方向）？

| 方向 | 说明 | 对应需求附录 9.3 |
|------|------|------------------|
| **向量检索 RAG** | 引入 Embedding + 向量库（如 Milvus/PGVector），提升岗位、简历、项目描述的语义匹配质量 | ✅ 附录明确建议 |
| **多轮对话上下文** | 职业探索、客服支持多轮对话，保留历史消息 | 提升体验 |
| **语音模拟面试** | 接入 ASR/TTS，支持语音问答 | ✅ 附录建议 |
| **多模型兜底** | DeepSeek 不可用时切换通义千问/智谱 GLM 等，提升可用性 | 增强可靠性 |
| **Prompt 版本管理** | Prompt 模板入库管理，支持 A/B 测试与版本回滚 | 可维护性 |
| **AI 调用成本控制** | 按用户/场景设置每日调用配额，避免成本失控 | 运营需要 |

---

## 五、实现状态总览矩阵

| 需求编号 | 功能模块 | AI 是否接入 | 兜底方案 | 完整度 | 优先级 |
|----------|----------|:-----------:|----------|:------:|:------:|
| R-004 | AI 职业方向探索 | ✅ | 规则推荐 | 较好 | — |
| R-008 | AI 简历优化 | ⚠️ 调用但输入无效 | 预置清单 | **缺陷** | **P0** |
| R-009 | AI 模拟面试 | ✅ | 本地题库 | 缺追问 | **P1** |
| R-010 | 企业项目需求解析 | ❌ 未接入 | 随机数 | **严重缺失** | **P0** |
| R-014 | 智能客服 | ✅ | FAQ+固定 | 较好 | — |
| R-006 | AI 能力差距分析 | ❌ 未接入 | 纯规则 | 未用 AI | **P1** |

---

## 六、执行建议

### 建议执行顺序

1. **第一批（P0，核心缺陷修复）**：
   - 修复 `EnterpriseServiceImpl` 接入 DeepSeek + `project_parse.txt`
   - 修复 `ResumeServiceImpl` 实现简历文本提取
   - 清理 `application-dev.yml` 硬编码 API Key

2. **第二批（P1，需求完整性）**：
   - 实现 `InterviewServiceImpl` 的 FOLLOW_UP 追问逻辑
   - 为 `GapAnalysisServiceImpl` 增加 AI 建议生成
   - 完善 `DeepSeekServiceImpl` 重试退避策略

3. **第三批（P2，体验优化）**：按需推进 SSE 流式、缓存优化、兜底质量

### 验证方式

- 每个 P0/P1 修复后，通过 `ai_call_log` 表确认对应场景出现 `SUCCESS` 记录
- 企业推荐：输入真实项目描述，验证返回的岗位和技能与描述相关（非固定 Java）
- 简历优化：上传真实 PDF，验证 `resume_text` 为正文内容（非文件 URL）
- 模拟面试：回答后验证可能出现追问（而非直接下一题）

---

## 附：关键文件索引

| 文件 | 路径 |
|------|------|
| AI 基础服务 | [DeepSeekServiceImpl.java](file:///d:/HuaweiMoveData/Users/20403/Desktop/软件实训项目/软件实训项目-code/软件实训项目-code/软件实训项目-第一版/backend/src/main/java/com/xuelian/career/service/impl/DeepSeekServiceImpl.java) |
| AI 配置 | [DeepSeekConfig.java](file:///d:/HuaweiMoveData/Users/20403/Desktop/软件实训项目/软件实训项目-code/软件实训项目-第一版/backend/src/main/java/com/xuelian/career/config/DeepSeekConfig.java) |
| Prompt 工具 | [PromptTemplateUtil.java](file:///d:/HuaweiMoveData/Users/20403/Desktop/软件实训项目/软件实训项目-code/软件实训项目-第一版/backend/src/main/java/com/xuelian/career/util/PromptTemplateUtil.java) |
| 职业探索 | [CareerExplorationServiceImpl.java](file:///d:/HuaweiMoveData/Users/20403/Desktop/软件实训项目/软件实训项目-code/软件实训项目-第一版/backend/src/main/java/com/xuelian/career/service/impl/CareerExplorationServiceImpl.java) |
| 简历优化 | [ResumeServiceImpl.java](file:///d:/HuaweiMoveData/Users/20403/Desktop/软件实训项目/软件实训项目-code/软件实训项目-第一版/backend/src/main/java/com/xuelian/career/service/impl/ResumeServiceImpl.java) |
| 模拟面试 | [InterviewServiceImpl.java](file:///d:/HuaweiMoveData/Users/20403/Desktop/软件实训项目/软件实训项目-code/软件实训项目-第一版/backend/src/main/java/com/xuelian/career/service/impl/InterviewServiceImpl.java) |
| 企业推荐 | [EnterpriseServiceImpl.java](file:///d:/HuaweiMoveData/Users/20403/Desktop/软件实训项目/软件实训项目-code/软件实训项目-第一版/backend/src/main/java/com/xuelian/career/service/impl/EnterpriseServiceImpl.java) |
| 智能客服 | [CustomerServiceServiceImpl.java](file:///d:/HuaweiMoveData/Users/20403/Desktop/软件实训项目/软件实训项目-code/软件实训项目-第一版/backend/src/main/java/com/xuelian/career/service/impl/CustomerServiceServiceImpl.java) |
| 差距分析 | [GapAnalysisServiceImpl.java](file:///d:/HuaweiMoveData/Users/20403/Desktop/软件实训项目/软件实训项目-code/软件实训项目-第一版/backend/src/main/java/com/xuelian/career/service/impl/GapAnalysisServiceImpl.java) |
| AI 调用日志 | [AiCallLog.java](file:///d:/HuaweiMoveData/Users/20403/Desktop/软件实训项目/软件实训项目-code/软件实训项目-第一版/backend/src/main/java/com/xuelian/career/entity/AiCallLog.java) |
| Prompt 模板目录 | [prompts/](file:///d:/HuaweiMoveData/Users/20403/Desktop/软件实训项目/软件实训项目-code/软件实训项目-第一版/backend/src/main/resources/prompts/) |
