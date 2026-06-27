# 智能体与 Skill 索引

本项目的 AI 智能体与 skill 分为三套，按"通用能力 → 项目级专家 → 模块级执行角色"分层组织。

## A 套：通用 Skill（`.codebuddy/skills/`）

可被任意智能体调用的能力包，不绑定具体角色。

| Skill | 路径 | 用途 |
| --- | --- | --- |
| project-requirements | [skills/project-requirements/SKILL.md](file:///d:/HuaweiMoveData/Users/20403/Desktop/软件实训项目/.codebuddy/skills/project-requirements/SKILL.md) | 把代码库反推为可视化 HTML 需求报告 |
| requirements-clarity | [skills/requirements-clarity/SKILL.md](file:///d:/HuaweiMoveData/Users/20403/Desktop/软件实训项目/.codebuddy/skills/requirements-clarity/SKILL.md) | 100 分评分机制澄清模糊需求，产出 PRD |
| system-designer | [skills/system-designer/SKILL.md](file:///d:/HuaweiMoveData/Users/20403/Desktop/软件实训项目/.codebuddy/skills/system-designer/SKILL.md) | 6D 框架产出单系统 L0/L1 详细设计文档 |

## B 套：项目级专家智能体（`.codebuddy/agents/`）

负责项目级文档产出，调用 A 套 skill 完成工作。

| 智能体 | 路径 | 职责 | 调用的 skill |
| --- | --- | --- | --- |
| Architect | [agents/Architect.md](file:///d:/HuaweiMoveData/Users/20403/Desktop/软件实训项目/.codebuddy/agents/Architect.md) | 系统架构设计、技术选型、数据库表设计、接口规范 | project-requirements, system-designer |
| requirement-analyst | [agents/requirement-analyst.md](file:///d:/HuaweiMoveData/Users/20403/Desktop/软件实训项目/.codebuddy/agents/requirement-analyst.md) | 项目级需求规格说明书（SRS） | requirements-clarity, project-requirements |

## C 套：模块级执行角色智能体（`需求分析/智能体skill/`）

由指挥官调度，按"开发-测试-通过"节奏推进 V1.0 模块。每个智能体含 `SKILL.md`（角色说明）与 `agents/openai.yaml`（工具与协作配置）。

| 角色 | 目录 | 职责 | 上游 | 下游 | 调用的 skill |
| --- | --- | --- | --- | --- | --- |
| commander-orchestrator | [智能体skill/commander-orchestrator/](file:///d:/HuaweiMoveData/Users/20403/Desktop/软件实训项目/需求分析/智能体skill/commander-orchestrator/SKILL.md) | 调度 8 角色推进模块闭环 | 用户/项目计划 | 全部角色 | （通过分派触发） |
| requirement-analyst (RA) | [智能体skill/requirement-analyst/](file:///d:/HuaweiMoveData/Users/20403/Desktop/软件实训项目/需求分析/智能体skill/requirement-analyst/SKILL.md) | 模块需求、用户场景、验收标准 | commander | SA, QA | requirements-clarity, project-requirements |
| project-manager (PM) | [智能体skill/project-manager/](file:///d:/HuaweiMoveData/Users/20403/Desktop/软件实训项目/需求分析/智能体skill/project-manager/SKILL.md) | 排期、阻塞跟踪、会议纪要 | commander | 全部执行角色 | — |
| system-architect (SA) | [智能体skill/system-architect/](file:///d:/HuaweiMoveData/Users/20403/Desktop/软件实训项目/需求分析/智能体skill/system-architect/SKILL.md) | HLD、API 契约、联调规则 | RA | DBA, BE, FE, QA | system-designer, project-requirements |
| database-architect (DBA) | [智能体skill/database-architect/](file:///d:/HuaweiMoveData/Users/20403/Desktop/软件实训项目/需求分析/智能体skill/database-architect/SKILL.md) | 表结构、约束、索引、测试数据 | SA | BE, QA | system-designer |
| backend-engineer (BE) | [智能体skill/backend-engineer/](file:///d:/HuaweiMoveData/Users/20403/Desktop/软件实训项目/需求分析/智能体skill/backend-engineer/SKILL.md) | 后端 API 开发、联调、自测 | SA, DBA | FE, QA | system-designer |
| frontend-engineer (FE) | [智能体skill/frontend-engineer/](file:///d:/HuaweiMoveData/Users/20403/Desktop/软件实训项目/需求分析/智能体skill/frontend-engineer/SKILL.md) | 前端页面、API 对接、交互状态 | SA, BE | QA | system-designer |
| qa-engineer (QA) | [智能体skill/qa-engineer/](file:///d:/HuaweiMoveData/Users/20403/Desktop/软件实训项目/需求分析/智能体skill/qa-engineer/SKILL.md) | 测试用例、缺陷、端到端验证 | RA, SA, BE, FE, DBA | commander, OP | — |
| ops-reviewer (OP) | [智能体skill/ops-reviewer/](file:///d:/HuaweiMoveData/Users/20403/Desktop/软件实训项目/需求分析/智能体skill/ops-reviewer/SKILL.md) | 评审交付物、协作断点、复盘 | PM, 全部角色 | commander | — |

## 协作链总览

```
用户 → commander-orchestrator
         │
         ├─ RA ─(模块需求/验收标准)─→ SA ─(HLD/API 契约)─→ DBA ─(表结构)─→ BE ─(API)─→ FE ─(页面)─→ QA ─(测试结论)─→ commander
         │                                                                                          │
         ├─ PM ─(排期/阻塞)──────────────────────────────────────────────────────────────────────┤
         └─ OP ─(评审/复盘)───────────────────────────────────────────────────────────────────────┘
```

## 调用关系

- **B 套智能体**通过 `use_skill` 工具调用 A 套 skill。
- **C 套智能体**在 `agents/openai.yaml` 的 `skills` 字段声明所需 skill，由 host 加载。
- **commander-orchestrator** 不直接调用 skill，通过分派角色智能体间接触发。
- **B 套与 C 套的衔接**：B 套 `requirement-analyst` 产出项目级 SRS，C 套 `requirement-analyst` 据此拆解模块级需求；B 套 `Architect` 产出概要设计，C 套 `system-architect` 据此细化 HLD 与 API 契约。

## 输出目录约定

| 产物 | 目录 |
| --- | --- |
| 需求规格说明书（SRS） | `需求分析/` |
| 概要设计说明书 | `系统架构/` |
| 单系统详细设计（L0/L1） | `系统架构/详细设计/` |
| ADR | `系统架构/ADR/` |
| 研究文档 | `系统架构/详细设计/_research/` |
| 可视化需求报告（HTML） | `docs/requirements/` |
| PRD（澄清产物） | `docs/prds/` |
