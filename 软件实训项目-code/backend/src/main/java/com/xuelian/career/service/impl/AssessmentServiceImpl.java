package com.xuelian.career.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xuelian.career.dto.request.AssessmentSubmitRequest;
import com.xuelian.career.dto.response.AssessmentReportResponse;
import com.xuelian.career.entity.AssessmentQuestion;
import com.xuelian.career.entity.AssessmentResult;
import com.xuelian.career.mapper.AssessmentQuestionMapper;
import com.xuelian.career.mapper.AssessmentResultMapper;
import com.xuelian.career.service.AssessmentService;
import com.xuelian.career.service.DeepSeekService;
import com.xuelian.career.util.PromptTemplateUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 能力测评服务实现 - 题目生成、评分计算、AI分析报告生成
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AssessmentServiceImpl implements AssessmentService {

    private final AssessmentQuestionMapper questionMapper;
    private final AssessmentResultMapper resultMapper;
    private final DeepSeekService deepSeekService;
    private final PromptTemplateUtil promptUtil;
    private final ObjectMapper objectMapper;

    /** 五个测评维度 */
    private static final String[] DIMENSIONS = {"PROGRAMMING", "LOGIC", "PRODUCT", "TECH", "COMMUNICATION"};
    /** 每个维度抽取题目数 */
    private static final int QUESTIONS_PER_DIMENSION = 5;

    /** 维度中文名映射 */
    private static final Map<String, String> DIM_CN = Map.of(
            "PROGRAMMING", "编程能力",
            "LOGIC", "逻辑推理",
            "PRODUCT", "产品思维",
            "TECH", "技术素养",
            "COMMUNICATION", "沟通表达"
    );

    @Override
    public List<AssessmentQuestion> getQuestions(String type) {
        List<AssessmentQuestion> questions = new ArrayList<>();
        for (String dimension : DIMENSIONS) {
            List<AssessmentQuestion> dimQuestions = questionMapper.selectRandomByDimension(dimension, QUESTIONS_PER_DIMENSION);
            questions.addAll(dimQuestions);
        }
        return questions;
    }

    @Override
    public AssessmentReportResponse submitAssessment(Long userId, AssessmentSubmitRequest request) {
        // 1. 计算各维度得分
        Map<String, Double> dimensionScores = new LinkedHashMap<>();
        Map<String, Integer> dimensionCounts = new LinkedHashMap<>();

        List<Long> questionIds = request.getAnswers().stream()
                .map(AssessmentSubmitRequest.AnswerItem::getQuestionId)
                .collect(Collectors.toList());
        List<AssessmentQuestion> questions = questionMapper.selectBatchIds(questionIds);
        Map<Long, AssessmentQuestion> questionMap = questions.stream()
                .collect(Collectors.toMap(AssessmentQuestion::getId, q -> q));

        for (AssessmentSubmitRequest.AnswerItem item : request.getAnswers()) {
            AssessmentQuestion question = questionMap.get(item.getQuestionId());
            if (question == null) continue;

            String dimension = question.getDimension();
            boolean isCorrect = question.getAnswer().equals(item.getAnswer());
            double score = isCorrect ? question.getScore() : 0;

            dimensionScores.merge(dimension, score, Double::sum);
            dimensionCounts.merge(dimension, question.getScore(), Integer::sum);
        }

        // 2. 计算各维度百分比得分
        Map<String, Double> percentageScores = new LinkedHashMap<>();
        for (String dim : DIMENSIONS) {
            double total = dimensionScores.getOrDefault(dim, 0.0);
            int maxPossible = dimensionCounts.getOrDefault(dim, 25);
            percentageScores.put(dim, maxPossible > 0 ? Math.round(total / maxPossible * 100.0) : 0.0);
        }

        // 3. 计算综合总分
        double totalScore = percentageScores.values().stream()
                .mapToDouble(Double::doubleValue)
                .average()
                .orElse(0.0);

        // 4. 调用 AI 生成分析（或使用兜底算法）
        Map<String, Object> aiAnalysis = generateAnalysis(percentageScores);

        // 5. 构建测评结果并持久化
        AssessmentResult result = new AssessmentResult();
        result.setUserId(userId);
        result.setType(request.getType() != null ? request.getType() : "COMPREHENSIVE");
        result.setProgrammingScore(percentageScores.get("PROGRAMMING"));
        result.setLogicScore(percentageScores.get("LOGIC"));
        result.setProductScore(percentageScores.get("PRODUCT"));
        result.setTechScore(percentageScores.get("TECH"));
        result.setCommunicationScore(percentageScores.get("COMMUNICATION"));
        result.setTotalScore(totalScore);
        result.setCreatedAt(LocalDateTime.now());

        // 将 AI 分析结果存入 resultJson
        try {
            result.setResultJson(objectMapper.writeValueAsString(aiAnalysis));
        } catch (JsonProcessingException e) {
            log.warn("序列化 AI 分析结果失败", e);
        }

        resultMapper.insert(result);

        // 6. 构建响应
        return buildReport(result, percentageScores, aiAnalysis);
    }

    @Override
    public AssessmentReportResponse getResult(Long resultId) {
        AssessmentResult result = resultMapper.selectById(resultId);
        if (result == null) return null;

        Map<String, Double> percentageScores = buildScoreMap(result);
        Map<String, Object> aiAnalysis = parseResultJson(result.getResultJson());
        return buildReport(result, percentageScores, aiAnalysis);
    }

    @Override
    public List<AssessmentReportResponse> getHistory(Long userId) {
        LambdaQueryWrapper<AssessmentResult> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AssessmentResult::getUserId, userId)
                .orderByDesc(AssessmentResult::getCreatedAt);
        List<AssessmentResult> results = resultMapper.selectList(wrapper);

        return results.stream()
                .map(r -> buildReport(r, buildScoreMap(r), parseResultJson(r.getResultJson())))
                .collect(Collectors.toList());
    }

    /**
     * 从实体构建分数映射
     */
    private Map<String, Double> buildScoreMap(AssessmentResult result) {
        Map<String, Double> scores = new LinkedHashMap<>();
        scores.put("PROGRAMMING", result.getProgrammingScore());
        scores.put("LOGIC", result.getLogicScore());
        scores.put("PRODUCT", result.getProductScore());
        scores.put("TECH", result.getTechScore());
        scores.put("COMMUNICATION", result.getCommunicationScore());
        return scores;
    }

    /**
     * 解析 resultJson 中的 AI 分析结果
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> parseResultJson(String resultJson) {
        if (resultJson == null || resultJson.isEmpty()) return null;
        try {
            return objectMapper.readValue(resultJson, Map.class);
        } catch (Exception e) {
            log.warn("解析 resultJson 失败", e);
            return null;
        }
    }

    /**
     * 构建测评报告响应
     */
    private AssessmentReportResponse buildReport(AssessmentResult result, Map<String, Double> scores,
                                                  Map<String, Object> aiAnalysis) {
        String level = getLevel(result.getTotalScore());
        Map<String, String> dimensionLevels = new LinkedHashMap<>();
        for (Map.Entry<String, Double> entry : scores.entrySet()) {
            dimensionLevels.put(DIM_CN.getOrDefault(entry.getKey(), entry.getKey()), getLevel(entry.getValue()));
        }

        // 保留原有 strengths/weaknesses 作为兜底
        String strengths = scores.entrySet().stream()
                .filter(e -> e.getValue() >= 85)
                .map(e -> DIM_CN.getOrDefault(e.getKey(), e.getKey()))
                .collect(Collectors.joining("、"));
        if (strengths.isEmpty()) strengths = "无显著优势维度";

        String weaknesses = scores.entrySet().stream()
                .filter(e -> e.getValue() < 55)
                .map(e -> DIM_CN.getOrDefault(e.getKey(), e.getKey()))
                .collect(Collectors.joining("、"));
        if (weaknesses.isEmpty()) weaknesses = "无显著薄弱维度";

        // 维度名称映射
        Map<String, Double> namedScores = new LinkedHashMap<>();
        scores.forEach((k, v) -> namedScores.put(DIM_CN.getOrDefault(k, k), v));

        return AssessmentReportResponse.builder()
                .id(result.getId())
                .type(result.getType())
                .dimensionScores(namedScores)
                .totalScore(result.getTotalScore())
                .level(level)
                .dimensionLevels(dimensionLevels)
                .strengths(strengths)
                .weaknesses(weaknesses)
                .aiAnalysis(aiAnalysis)
                .createdAt(result.getCreatedAt())
                .build();
    }

    /**
     * 生成分析报告：优先尝试 AI，失败则使用本地兜底算法
     */
    private Map<String, Object> generateAnalysis(Map<String, Double> scores) {
        // 尝试 AI 分析
        try {
            if (deepSeekService.isAvailable()) {
                Map<String, Object> aiResult = callAIForAnalysis(scores);
                if (aiResult != null) {
                    log.info("AI 分析生成成功");
                    return aiResult;
                }
            }
        } catch (Exception e) {
            log.warn("AI 分析失败，使用本地兜底算法: {}", e.getMessage());
        }

        // 兜底：本地算法生成结构化分析
        log.info("使用本地兜底算法生成分析");
        return fallbackAnalysis(scores);
    }

    /**
     * 调用 DeepSeek AI 生成测评分析
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> callAIForAnalysis(Map<String, Double> scores) {
        try {
            // 构建测评数据文本
            StringBuilder scoreData = new StringBuilder();
            for (String dim : DIMENSIONS) {
                String cnName = DIM_CN.get(dim);
                Double score = scores.getOrDefault(dim, 0.0);
                String level = getLevel(score);
                scoreData.append(String.format("- %s（%s）：%.0f分，等级：%s\n", dim, cnName, score, level));
            }

            Map<String, String> params = new HashMap<>();
            params.put("score_data", scoreData.toString());

            String prompt = promptUtil.loadAndRender("assessment_analysis", params);
            String response = deepSeekService.callAPI("你是一位专业的职业能力测评分析师", prompt);
            Map<String, Object> result = deepSeekService.parseJSONResponse(response);

            if (result != null && result.containsKey("strengthAnalysis")) {
                result.put("source", "AI");
                return result;
            }
        } catch (Exception e) {
            log.warn("DeepSeek 分析异常: {}", e.getMessage());
        }
        return null;
    }

    /**
     * 本地兜底算法：基于分数生成结构化的深度分析报告
     * 优势维度 → 生成落地应用方法 + 弥补短板策略
     * 薄弱维度 → 生成三层递进式提升方案（入门→进阶→实战）
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> fallbackAnalysis(Map<String, Double> scores) {
        List<Map<String, Object>> strengthAnalysis = new ArrayList<>();
        List<Map<String, Object>> weaknessAnalysis = new ArrayList<>();
        List<Map<String, Object>> normalAnalysis = new ArrayList<>();

        for (String dim : DIMENSIONS) {
            double score = scores.getOrDefault(dim, 0.0);
            String cnName = DIM_CN.get(dim);
            String level = getLevel(score);

            if (score >= 85) {
                strengthAnalysis.add(buildStrengthItem(dim, cnName, score, level));
            } else if (score < 55) {
                weaknessAnalysis.add(buildWeaknessItem(dim, cnName, score, level));
            } else {
                normalAnalysis.add(buildNormalItem(dim, cnName, score, level));
            }
        }

        // 生成综合评语
        String overallSummary = buildOverallSummary(scores, strengthAnalysis, weaknessAnalysis);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("source", "FALLBACK");
        result.put("strengthAnalysis", strengthAnalysis);
        result.put("weaknessAnalysis", weaknessAnalysis);
        result.put("overallSummary", overallSummary);
        return result;
    }

    /**
     * 构建优势维度分析项 - 落地应用 + 以长补短
     */
    private Map<String, Object> buildStrengthItem(String dim, String cnName, double score, String level) {
        DimConfig config = DIM_CONFIGS.get(dim);

        Map<String, Object> item = new LinkedHashMap<>();
        item.put("dimension", cnName);
        item.put("score", score);
        item.put("level", level);
        item.put("analysis", config.strengthAnalysis);

        List<Map<String, String>> applications = new ArrayList<>();
        for (String[] app : config.applications) {
            applications.add(Map.of("title", app[0], "desc", app[1]));
        }
        item.put("applications", applications);
        item.put("compensateWeakness", config.compensateStrategy);

        return item;
    }

    /**
     * 构建薄弱维度分析项 - 三层递进式提升计划
     */
    private Map<String, Object> buildWeaknessItem(String dim, String cnName, double score, String level) {
        DimConfig config = DIM_CONFIGS.get(dim);

        // 根据分数选择不同的入门难度
        String entryIntensity = score < 30 ? "基础薄弱，需从零开始" : "有一定基础，可加快入门节奏";
        String suggestDuration;
        if (score < 30) {
            suggestDuration = "%s。建议按以下节奏推进：入门2-3周打基础，进阶3-4周巩固，实战4-6周产出成果";
        } else {
            suggestDuration = "%s。建议按以下节奏推进：入门1-2周快速回顾，进阶3-4周提升，实战3-4周产出成果";
        }

        Map<String, Object> item = new LinkedHashMap<>();
        item.put("dimension", cnName);
        item.put("score", score);
        item.put("level", level);
        item.put("analysis", String.format("%s。%s", config.weaknessAnalysis,
                String.format(suggestDuration, entryIntensity)));

        List<Map<String, Object>> plan = new ArrayList<>();

        // 入门夯实
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("level", "入门夯实");
        entry.put("duration", score < 30 ? "2-3周" : "1-2周");
        entry.put("tasks", config.entryTasks);
        plan.add(entry);

        // 进阶提升
        Map<String, Object> advance = new LinkedHashMap<>();
        advance.put("level", "进阶提升");
        advance.put("duration", "3-4周");
        advance.put("tasks", config.advanceTasks);
        plan.add(advance);

        // 实战应用
        Map<String, Object> practice = new LinkedHashMap<>();
        practice.put("level", "实战应用");
        practice.put("duration", score < 30 ? "4-6周" : "3-4周");
        practice.put("tasks", config.practiceTasks);
        plan.add(practice);

        item.put("improvementPlan", plan);
        return item;
    }

    /**
     * 构建中等维度分析项
     */
    private Map<String, Object> buildNormalItem(String dim, String cnName, double score, String level) {
        DimConfig config = DIM_CONFIGS.get(dim);

        Map<String, Object> item = new LinkedHashMap<>();
        item.put("dimension", cnName);
        item.put("score", score);
        item.put("level", level);
        item.put("analysis", config.normalAnalysis);
        item.put("applications", List.of(
                Map.of("title", "针对性强化",
                        "desc", String.format("当前%s处于中等水平，建议聚焦以下方向突破：%s",
                                cnName, String.join("；", config.entryTasks)))
        ));
        item.put("improvementPlan", List.of(
                Map.of("level", "专项提升", "duration", "4-6周",
                        "tasks", config.advanceTasks),
                Map.of("level", "实战检验", "duration", "4-6周",
                        "tasks", config.practiceTasks)
        ));
        return item;
    }

    /**
     * 生成综合评语
     */
    private String buildOverallSummary(Map<String, Double> scores,
                                        List<Map<String, Object>> strengths,
                                        List<Map<String, Object>> weaknesses) {
        double totalScore = scores.values().stream().mapToDouble(Double::doubleValue).average().orElse(0);
        String overallLevel = getLevel(totalScore);

        StringBuilder sb = new StringBuilder();
        sb.append(String.format("综合测评总分%.0f分，等级%s。", totalScore, overallLevel));

        // 汇总优势
        if (!strengths.isEmpty()) {
            List<String> strengthDims = strengths.stream()
                    .map(s -> (String) s.get("dimension"))
                    .collect(Collectors.toList());
            sb.append(String.format("优势领域为%s，建议将这些能力深度结合到实际技术工作中，形成差异化竞争力。",
                    String.join("、", strengthDims)));
        }

        // 汇总薄弱
        if (!weaknesses.isEmpty()) {
            List<String> weaknessDims = weaknesses.stream()
                    .map(s -> (String) s.get("dimension"))
                    .collect(Collectors.toList());
            sb.append(String.format("需重点提升的领域为%s，按上述分层计划坚持执行，预计8-12周可见明显进步。",
                    String.join("、", weaknessDims)));
        }

        // 以长补短建议
        if (!strengths.isEmpty() && !weaknesses.isEmpty()) {
            String topStrength = (String) strengths.get(0).get("dimension");
            String topWeakness = (String) weaknesses.get(0).get("dimension");
            sb.append(String.format("可以利用%s的优势来驱动%s的学习，例如通过实际编码项目来检验逻辑思维，在实践中同步成长。",
                    topStrength, topWeakness));
        }

        if (strengths.isEmpty() && weaknesses.isEmpty()) {
            sb.append("各维度发展较为均衡，建议选择1-2个方向进行深度突破，形成自己的核心竞争力。");
        }

        return sb.toString();
    }

    /**
     * 根据分数计算等级
     */
    private String getLevel(double score) {
        if (score >= 85) return "优秀";
        if (score >= 70) return "良好";
        if (score >= 55) return "一般";
        return "待提升";
    }

    // ==================== 维度配置数据 ====================

    /** 维度配置 */
    private static final Map<String, DimConfig> DIM_CONFIGS;

    static {
        DIM_CONFIGS = new LinkedHashMap<>();

        DIM_CONFIGS.put("PROGRAMMING", new DimConfig(
                // strengthAnalysis
                "编程能力表现突出，具备扎实的代码功底和算法思维，能够独立完成复杂的编码任务。" +
                        "在算法题、代码实现类题目上表现优秀，说明你拥有较强的抽象思维和问题转化能力。",
                // weaknessAnalysis
                "编程能力是技术岗位的核心竞争力，当前水平会直接影响项目交付质量和工作效率。" +
                        "薄弱原因可能包括：缺乏系统性练习、对数据结构和算法理解不够深入、实际编码经验不足。",
                // normalAnalysis
                "编程能力处于中等水平，能完成基本的开发任务，但在算法优化和复杂业务逻辑实现上仍有提升空间。" +
                        "建议通过刻意练习突破瓶颈，从会写代码进阶到写出好代码。",
                // applications (strength)
                new String[][]{
                        {"参与开源项目贡献代码", "选取你熟悉领域的知名开源项目（如Spring Boot、Vue生态），从Good First Issue开始提交PR，通过Code Review提升代码质量意识"},
                        {"搭建个人技术博客+代码库", "将日常学习的技术点用代码实现并发布到GitHub，附详细文档和单元测试，形成可展示的技术资产"},
                        {"担任团队Code Review主力", "主动承担团队代码审查职责，利用你的代码敏感度帮助团队提升整体代码质量，同时锻炼架构视野"}
                },
                // compensateStrategy
                "利用编程优势驱动其他维度学习：通过实现产品功能原型来锻炼产品思维（每做一个功能先写PRD再编码）；" +
                        "通过参与技术方案评审来提升沟通表达（将技术实现逻辑清晰陈述给非技术人员）；" +
                        "通过阅读优秀开源项目源码来拓展技术素养（分析架构设计决策背后的权衡）",
                // entryTasks (weakness)
                List.of(
                        "完成LeetCode热题100中「简单」难度的30题，重点练习数组、字符串、链表基础操作",
                        "阅读《重构：改善既有代码的设计》前6章，对现有代码进行一次重构实践",
                        "使用Git管理一个个人项目，练习分支管理、Code Review流程、CI/CD配置"
                ),
                // advanceTasks (weakness)
                List.of(
                        "完成LeetCode「中等」难度50题，覆盖动态规划、二叉树、回溯、滑动窗口等核心算法",
                        "学习一个设计模式（如策略、观察者、工厂）并用代码实现一个实际业务场景",
                        "阅读一个知名开源项目的核心模块源码（建议MyBatis或Spring IoC），输出技术分析文档"
                ),
                // practiceTasks (weakness)
                List.of(
                        "用Spring Boot + Vue从零开发一个完整CRUD应用（如任务管理系统），包含权限、分页、文件上传",
                        "参与一次公司内部或开源项目的Bug修复，提交至少3个PR并被合并",
                        "对个人项目进行性能优化：添加缓存层（Redis）、数据库索引优化、接口压测并输出优化报告"
                )
        ));

        DIM_CONFIGS.put("LOGIC", new DimConfig(
                "逻辑推理能力表现突出，你具备优秀的分析和归纳能力，能够快速理清复杂问题的因果关系。" +
                        "这种能力在技术方案设计、Bug排查、架构决策中极为关键。",
                "逻辑推理是技术工作的方法论基础，影响你分析需求、设计架构、排查问题的效率和质量。" +
                        "薄弱原因可能包括：缺乏结构化思维训练、不习惯用数据驱动决策、对业务场景的理解停留在表面。",
                "逻辑推理处于中等水平，日常开发中的简单问题可以应对，但面对复杂系统设计或疑难Bug时可能缺乏系统性分析框架。" +
                        "建议通过结构化思维训练实现突破。",
                new String[][]{
                        {"主导技术方案设计文档", "在项目中主动承担方案设计角色，用Mermaid画流程图和时序图，将逻辑推理能力转化为团队可复用的技术文档"},
                        {"搭建问题排查知识库", "将日常遇到的Bug排查过程总结为排查树（决策树），形成团队级的问题诊断工具，发挥逻辑推理的杠杆效应"}
                },
                "利用逻辑优势弥补技术短板：使用5W1H分析法拆解技术问题（What/Why/When/Where/Who/How），" +
                        "将模糊的技术需求转化为可量化的子任务，通过结构化分析降低复杂技术问题的理解成本。" +
                        "在面对不熟悉的技术领域时，先建立知识框架再填充细节，避免盲目学习。",
                List.of(
                        "学习金字塔原理（MECE法则），每天用XMind对一个技术问题进行结构化拆解练习",
                        "阅读《学会提问》并完成书中的批判性思维练习，建立「观点→论据→推理」的分析框架",
                        "参与Code Review时强制自己从三个角度（正确性/性能/可维护性）审查每一段代码"
                ),
                List.of(
                        "学习UML类图和时序图，对工作中遇到的复杂业务场景画出完整的交互流程",
                        "完成3个系统设计案例练习（如设计一个短链接系统、设计一个消息队列），输出包含架构图、数据流、容量估算的完整方案",
                        "参与一次线上故障复盘，用「5 Why分析法」追溯到根因，输出复盘报告"
                ),
                List.of(
                        "独立承担一个跨模块需求的方案设计，产出包含问题分析、方案对比、风险评估的完整技术方案文档",
                        "对现有系统的某个核心链路进行梳理，画出完整的调用链路图并标注潜在风险点",
                        "组织一次技术方案评审会，将你的分析框架应用到团队决策流程中"
                )
        ));

        DIM_CONFIGS.put("PRODUCT", new DimConfig(
                "产品思维能力表现突出，你对用户需求和产品价值有敏锐的感知，善于从用户视角思考问题。" +
                        "这种能力在需求分析、用户体验优化、产品规划中能发挥巨大价值。",
                "产品思维是技术与业务之间的桥梁能力，直接影响你理解需求本质、设计合理方案、交付用户价值的能力。" +
                        "薄弱原因可能包括：过度关注技术实现而忽略用户场景、缺乏产品方法论指导、没有用户调研的实践经验。",
                "产品思维处于中等水平，能够理解基本的产品需求，但未能主动从用户视角优化功能设计。" +
                        "建议通过刻意实践从执行者向思考者转变。",
                new String[][]{
                        {"推动技术产品的用户体验优化", "在每个迭代中，从技术实现反推用户体验改进点：加载速度优化→留存率提升、错误提示优化→用户满意度提升"},
                        {"参与产品评审，提供技术可行性视角", "在产品评审中结合技术实现难度提出「低成本高价值」的优化建议，体现技术+产品的双重价值"}
                },
                "利用产品思维弥补技术短板：当你面对不熟悉的技术领域时，先用产品思维定义「用户故事」——谁在什么场景下有什么痛点，" +
                        "然后再寻找最合适的技术方案。这样即使技术不是最强，你也能交付最有价值的产品。" +
                        "同时通过竞品分析学习行业最佳实践，降低技术选型的试错成本。",
                List.of(
                        "每天深度使用3个同类App/产品，记录5个「用户体验好」和3个「用户体验差」的设计点",
                        "阅读《用户体验要素》并完成一个你常用产品的五层分析（战略层→范围层→结构层→框架层→表现层）",
                        "为自己开发的功能写一份「用户使用说明书」，强迫自己从用户视角描述功能价值"
                ),
                List.of(
                        "完整撰写一份PRD文档（产品需求文档），包含用户故事、原型草图、验收标准",
                        "做一次用户访谈（至少3人），整理访谈记录并提炼出3个核心痛点",
                        "学习A/B测试方法，对你负责的功能提出一个可量化的优化假设并设计实验方案"
                ),
                List.of(
                        "独立主导一个小功能从需求分析到上线的全过程，输出完整的迭代复盘报告",
                        "基于用户反馈数据（埋点、问卷、客服记录），对一个现有功能提出优化方案并推动落地",
                        "参加一次产品Hackathon，在限定时间内完成从问题定义到原型验证的全流程"
                )
        ));

        DIM_CONFIGS.put("TECH", new DimConfig(
                "技术素养表现突出，你拥有较广的技术视野和对技术趋势的敏感度，能够快速学习新技术并将其应用到实际工作中。" +
                        "这是技术人的长期核心竞争力，代表着你的学习能力和技术判断力。",
                "技术素养决定了你的技术天花板，影响技术选型、架构决策和学习效率。" +
                        "薄弱原因可能包括：技术面过于狭窄（只关注当前业务所需）、没有系统性学习计划、缺乏对技术原理的深层理解。",
                "技术素养处于中等水平，对常用技术栈有一定了解但缺乏深度和广度。" +
                        "建议通过系统性的技术广度拓展来实现突破，从「会用」进阶到「懂得为什么」。",
                new String[][]{
                        {"建立技术雷达，驱动团队技术升级", "每月发布团队技术趋势简报，结合业务场景推荐新技术方案，将自己对技术的敏感度转化为团队的技术竞争力"},
                        {"深耕某一技术领域形成专长", "选择1-2个技术方向（如性能优化/云原生/AI应用）深度钻研，通过技术分享和内部培训建立专家形象"}
                },
                "利用技术广度弥补其他短板：当逻辑推理偏弱时，用技术工具（如APM监控、SQL执行计划分析）替代纯脑力分析；" +
                        "当产品思维不足时，通过学习行业头部产品的技术架构来反推产品设计思路。" +
                        "重点关注「技术如何创造业务价值」，而不仅仅是技术本身。",
                List.of(
                        "制定一个技术栈学习地图：从前端到后端到运维，列出你所在技术生态的核心技术点（至少20个）",
                        "每周阅读3篇技术博客/公众号文章，并在个人笔记中用自己的话总结核心思想和应用场景",
                        "学习Docker基础，将你的开发环境容器化，理解容器与虚拟机的区别"
                ),
                List.of(
                        "深入理解一个你常用的框架的核心原理（如Spring IoC/AOP、Vue响应式系统），阅读关键源码并输出分析文章",
                        "搭建一个完整的监控体系（Prometheus + Grafana），为你开发的服务配置关键指标监控和告警",
                        "学习一个云服务商（阿里云/腾讯云/AWS）的核心产品，部署一个完整的三层架构应用到云上"
                ),
                List.of(
                        "对你的项目进行全链路性能优化：前端懒加载→CDN加速→Nginx反向代理→接口缓存→数据库慢查询优化",
                        "参与一次系统架构升级（如单体拆微服务、引入消息队列解耦），负责其中1-2个模块的设计和实现",
                        "组织一次团队技术分享会（30分钟以上），主题为你深入学习的技术方向，准备PPT和Demo"
                )
        ));

        DIM_CONFIGS.put("COMMUNICATION", new DimConfig(
                "沟通表达能力表现突出，你能够逻辑清晰地传达技术观点，在团队协作中表现出色。" +
                        "这是技术岗位中常被低估但极为关键的能力——再好的技术方案也需要让他人理解和认同。",
                "沟通表达直接影响技术方案的推进效率和团队协作质量，沟通不畅会导致需求理解偏差、方案返工、团队摩擦。" +
                        "薄弱原因可能包括：习惯独自编码缺乏交流练习、技术思维强势忽略非技术人员视角、缺少结构化表达方法论。",
                "沟通表达处于中等水平，日常协作可以胜任，但在技术方案宣讲、跨部门协调等场景中可能需要额外准备。" +
                        "建议通过刻意练习将沟通能力从「够用」提升到「有影响力」。",
                new String[][]{
                        {"主导技术分享和内部培训", "利用表达能力将你的技术积累转化为团队知识资产，每月组织一次技术分享会，锻炼演讲和技术传播能力"},
                        {"承担跨部门技术对接角色", "利用沟通优势成为产品、测试、运维团队的技术接口人，减少信息损耗，提升整个团队的交付效率"}
                },
                "利用沟通优势弥补技术短板：通过向技术大牛请教和参与技术社区讨论，加速技术学习曲线；" +
                        "在方案评审中通过清晰的表达引导讨论方向，即使技术方案不够完美也能获得团队信任和改进机会。" +
                        "将每次技术决策的讨论过程记录下来，形成团队的决策日志。",
                List.of(
                        "学习「STAR法则」（情境→任务→行动→结果），在周报或汇报中使用该框架结构化你的工作成果",
                        "每天写一篇技术笔记（200字以上），练习用简洁的文字表达技术概念",
                        "在团队站会上主动复述一遍你理解的需求，请产品经理确认理解是否一致"
                ),
                List.of(
                        "准备一个15分钟的技术分享（可以是某个Bug的排查过程或新技术的学习总结），在团队内部进行首秀",
                        "学习「金字塔原理」，对一个技术文档进行改写：先写结论再展开论点，每条论点有数据或案例支撑",
                        "参与至少3次跨部门需求评审，在会议中至少提出2个问题或建议"
                ),
                List.of(
                        "主导一次技术方案评审会（60分钟），提前准备方案文档、Demo演示，回答至少5个提问",
                        "撰写一篇技术博客发布到掘金/CSDN/知乎等平台，回复评论区的提问并获得至少10个点赞/收藏",
                        "参与开源社区讨论（GitHub Issue/Discussion），对至少3个Issue提出建设性的技术建议"
                )
        ));
    }

    /** 维度配置数据类 */
    private static class DimConfig {
        final String strengthAnalysis;
        final String weaknessAnalysis;
        final String normalAnalysis;
        final String[][] applications;
        final String compensateStrategy;
        final List<String> entryTasks;
        final List<String> advanceTasks;
        final List<String> practiceTasks;

        DimConfig(String strengthAnalysis, String weaknessAnalysis, String normalAnalysis,
                  String[][] applications, String compensateStrategy,
                  List<String> entryTasks, List<String> advanceTasks, List<String> practiceTasks) {
            this.strengthAnalysis = strengthAnalysis;
            this.weaknessAnalysis = weaknessAnalysis;
            this.normalAnalysis = normalAnalysis;
            this.applications = applications;
            this.compensateStrategy = compensateStrategy;
            this.entryTasks = entryTasks;
            this.advanceTasks = advanceTasks;
            this.practiceTasks = practiceTasks;
        }
    }
}
