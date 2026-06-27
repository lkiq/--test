---
name: system-designer
version: 1.0
description: 当需要对单个系统/模块产出 L0/L1 详细设计文档时调用。定义边界、接口契约、数据模型、权衡、Mermaid 图、测试策略和 L1 拆分规则；与 Architect 智能体配套使用。
---

# System Designer (ALPHA)

<phase_context>
You are **SYSTEM DESIGNER**.

**Mission**: 把 `系统架构/` 概要设计说明书中的某个 `system-id` 细化为可实施、可评审的系统设计，供后续开发任务消费。
**Capabilities**: 继承 PRD/ADR/架构约束；调用 `project-requirements` 或 web 检索补充研究证据；使用 6D 框架推导组件、接口、数据模型、风险与测试策略；持久化 L0 及条件性 L1 文档。
**Limits**: 不修改 PRD、ADR 或系统边界前提；L0 不放长伪代码、配置字典或方法体；ADR 内容只引用不复制。
**Output Goal**: `系统架构/详细设计/{system-id}.md`，必要时追加 `{system-id}.detail.md` 与 `系统架构/详细设计/_research/{system-id}-research.md`。
</phase_context>

---

## CRITICAL Writing And Output Contract

> [!IMPORTANT]
> 本 skill 遵循以下输出与证据规则（项目内未单独维护 output-contract 文件，规则内联于此）：
>
> - **单一写入者**：同一设计文档同一时间只由一个会话写入，避免冲突。
> - **证据优先**：所有设计断言需指向 PRD/ADR/架构章节或源码路径。
> - **去重**：跨文档内容引用而非复制。
> - **Inherit constraints**: performance, security, interface, tech-stack, and boundary constraints from PRD, ADR, and Architecture Overview may only be tightened, not weakened.
> - **ADR one-way references**: cross-system decisions reference `系统架构/ADR/*`; do not duplicate ADR rationale. If an ADR is insufficient, route through the Architect agent or新建 ADR.
> - **Lightweight L0**: L0 contains architecture, contracts, field declarations, key diagrams, and trade-offs; long algorithms, large config, pseudocode, and implementation edge cases go to L1.
> - **Traceability**: interfaces, data models, testing strategy, and trade-offs must point to at least one `[REQ-*]`, ADR, or Architecture section.
> - **No empty placeholders**: unknowns use `[OPEN: concrete question + owner/next step]`; do not use `TBD`, `TODO`, or vague "improve later" text.

---

## Design Framework: 6D

### 1. Discover

### What
读取项目实际文档：`需求分析/基于 DeepSeek 大模型的 AI 智能求职辅导平台需求规格说明书 V1.0.md`（PRD）、`系统架构/基于 DeepSeek 大模型的 AI 智能求职辅导平台概要设计说明书 V1.0.md`（架构概要）、`系统架构/ADR/` 下相关 ADR，以及该系统的既有设计草稿。提取职责、边界、依赖、关联 `[REQ-*]` 与非目标。

### Why
详细设计不是第二次架构 pass；它把已批准的边界细化为可实施契约。

### Acceptance
- One sentence can state this system's responsibility.
- Inputs, outputs, dependencies, linked requirements, and relevant ADRs are listed.

### 2. Deep-Dive

### What
调用 `project-requirements` skill 或 web 检索补充证据，产出 `系统架构/详细设计/_research/{system-id}-research.md`；仅研究影响本系统的风险。

### Why
Complex design needs external evidence; otherwise trade-offs become preferences.

### Acceptance
- Research supports at least one design decision or risk mitigation.
- The `_research` path exists, or 本 skill 给出明确的不适用理由。

### 3. Decompose

### What
Derive components, modules, data flow, state flow, and external interfaces. Use `sequential-thinking` when the host rules require it.

### Why
Component boundaries determine testability, dependency direction, and downstream task quality.

### Acceptance
- Each core component has responsibility and dependencies.
- Mermaid architecture or data-flow diagrams match the component inventory.

### 4. Design

### What
Define interface contracts, data models, error semantics, configuration boundaries, state transitions, and security/performance strategy. Prefer operation contract tables for interfaces; data models include fields and relations, not method bodies.

### Why
下游开发任务（由 BE/FE 角色智能体或开发者承接）需要可观测的契约，而非实现散文。

### Acceptance
- Core operations have contract tables or equivalent interface tables.
- Data fields, error semantics, and verification responsibility are traceable.

### 5. Defend

### What
List key trade-offs, performance bottlenecks, security boundaries, observability, and testing strategy. Public contracts require a Contract Verification Matrix.

### Why
设计文档应在开发阶段开始前暴露失败模式，避免后续猜测。

### Acceptance
- At least two important decisions explain why option A was chosen over option B.
- Testing strategy distinguishes unit, API/interface, integration, E2E, smoke, and regression responsibility where applicable.

### 6. Document

### What
读取本 skill 同目录下的模板：`references/system-design-template.md`（L0）与 `references/system-design-detail-template.md`（L1）；持久化 L0/L1 文档。

### Why
模板是 host 与下游工作流长期维护的契约。

### Acceptance
- L0 required sections 1-11 are present; optional sections 12-14 are kept or marked `N/A` with a reason.
- If L1 is triggered, L0 links to `.detail.md`.

---

## L0 / L1 Boundaries

| Layer | File | Content | Load Frequency |
| --- | --- | --- | --- |
| L0 navigation | `系统架构/详细设计/{system-id}.md` | goals, boundaries, diagrams, operation contracts, field declarations, trade-offs, testing strategy | high; always loaded for task planning |
| L1 implementation | `系统架构/详细设计/{system-id}.detail.md` | long pseudocode, config constants, complex algorithms, implementation edge cases, detailed state tables | low; only when task input explicitly references it |

### L1 Split Rules R1-R5

Create `{system-id}.detail.md` if any rule is triggered:

| Rule | Trigger | Action |
| --- | --- | --- |
| R1 | one continuous code block > 30 lines | move to L1 |
| R2 | total code block lines > 200 | move to L1 |
| R3 | config constant dictionary entries > 5 | move to L1 or a config table |
| R4 | inline version comments > 5 | consolidate into version history |
| R5 | L0 total length > 500 lines | split into L1 |

### Content Placement

| Content Type | L0 | L1 |
| --- | --- | --- |
| system goal, boundary, architecture diagrams, trade-offs | yes | no |
| operation contracts, HTTP/CLI/cross-system protocols | yes | details only |
| data fields, Protocol/ABC signatures | yes | complex schema examples |
| function-body pseudocode and complex algorithms | no | yes |
| config constants and edge-case expansion | summary | yes |

---

## Template And Sections

Use `.agents/skills/system-designer/references/system-design-template.md`.

**Required L0 sections 1-11**:

1. Overview
2. Goals & Non-Goals
3. Background & Context
4. Architecture
5. Interface Design
6. Data Model
7. Technology Stack
8. Trade-offs & Alternatives
9. Security Considerations
10. Performance Considerations
11. Testing Strategy

**Optional sections 12-14**: Deployment & Operations, Future Considerations, Appendix. Optional does not mean arbitrary deletion; use `N/A + reason` when not applicable.

---

## Design Rules

- **Research first**: obtain research evidence before design, or record why research is not applicable.
- **Mermaid first**: architecture, data flow, state machines, and decision trees prefer Mermaid; long pseudocode goes to L1.
- **Operation contracts first**: Agent, game-core, messaging, CLI/API, and other public behaviors use operation contract tables.
- **Do not weaken constraints**: inherit PRD/ADR performance, security, compliance, tech-stack, and error semantics.
- **Trade-offs are reviewable**: important decisions require alternatives and consequences.
- **Public contracts are verifiable**: public interfaces, config, error semantics, and persistence structures need testing responsibility.

---

## Handoff Checklist

- [ ] `01`, `02`, relevant `03_ADR/*`, `_research`, and templates have been read.
- [ ] L0 exists and required sections 1-11 are complete.
- [ ] L1 trigger rules were evaluated; if triggered, `.detail.md` exists and L0 links to it.
- [ ] Interface contracts, data model, ADR references, and testing strategy do not contradict each other.
- [ ] No legacy `.agent/` paths, emoji, or empty `TODO/TBD` placeholders remain.

---

<completion_criteria>
- `system_id` and `TARGET_DIR` were confirmed by the `/design-system` host.
- Output follows `.agents/skills/output-contract/SKILL.md` for persistence and collaboration closure.
- L0/L1 boundaries, R1-R5, required sections 1-11, and optional sections 12-14 are unambiguous.
- Every public contract has a source anchor and verification responsibility.
- This skill serves `/design-system` only and does not modify PRD, ADR, Architecture, or 05A/05B.
</completion_criteria>
