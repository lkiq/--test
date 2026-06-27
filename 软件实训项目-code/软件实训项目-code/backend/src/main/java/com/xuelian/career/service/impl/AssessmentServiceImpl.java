package com.xuelian.career.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.xuelian.career.dto.request.AssessmentSubmitRequest;
import com.xuelian.career.dto.response.AssessmentReportResponse;
import com.xuelian.career.entity.AssessmentQuestion;
import com.xuelian.career.entity.AssessmentResult;
import com.xuelian.career.mapper.AssessmentQuestionMapper;
import com.xuelian.career.mapper.AssessmentResultMapper;
import com.xuelian.career.service.AssessmentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 能力测评服务实现 - 题目生成、评分计算、报告生成
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AssessmentServiceImpl implements AssessmentService {

    private final AssessmentQuestionMapper questionMapper;
    private final AssessmentResultMapper resultMapper;

    /** 五个测评维度 */
    private static final String[] DIMENSIONS = {"PROGRAMMING", "LOGIC", "PRODUCT", "TECH", "COMMUNICATION"};
    /** 每个维度抽取题目数 */
    private static final int QUESTIONS_PER_DIMENSION = 5;

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

        // 构建题目ID → 题目的映射
        List<Long> questionIds = request.getAnswers().stream()
                .map(AssessmentSubmitRequest.AnswerItem::getQuestionId)
                .collect(Collectors.toList());
        List<AssessmentQuestion> questions = questionMapper.selectBatchIds(questionIds);
        Map<Long, AssessmentQuestion> questionMap = questions.stream()
                .collect(Collectors.toMap(AssessmentQuestion::getId, q -> q));

        // 逐题判分
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
            int maxPossible = dimensionCounts.getOrDefault(dim, 25); // 5题 × 5分
            percentageScores.put(dim, maxPossible > 0 ? Math.round(total / maxPossible * 100.0) : 0.0);
        }

        // 3. 计算综合总分（五维度平均）
        double totalScore = percentageScores.values().stream()
                .mapToDouble(Double::doubleValue)
                .average()
                .orElse(0.0);

        // 4. 构建测评结果
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
        resultMapper.insert(result);

        // 5. 构建响应
        return buildReport(result, percentageScores);
    }

    @Override
    public AssessmentReportResponse getResult(Long resultId) {
        AssessmentResult result = resultMapper.selectById(resultId);
        if (result == null) return null;

        Map<String, Double> percentageScores = buildScoreMap(result);
        return buildReport(result, percentageScores);
    }

    @Override
    public List<AssessmentReportResponse> getHistory(Long userId) {
        LambdaQueryWrapper<AssessmentResult> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AssessmentResult::getUserId, userId)
                .orderByDesc(AssessmentResult::getCreatedAt);
        List<AssessmentResult> results = resultMapper.selectList(wrapper);

        return results.stream()
                .map(r -> buildReport(r, buildScoreMap(r)))
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
     * 构建测评报告响应
     */
    private AssessmentReportResponse buildReport(AssessmentResult result, Map<String, Double> scores) {
        // 计算等级
        String level = getLevel(result.getTotalScore());
        Map<String, String> dimensionLevels = new LinkedHashMap<>();
        for (Map.Entry<String, Double> entry : scores.entrySet()) {
            dimensionLevels.put(entry.getKey(), getLevel(entry.getValue()));
        }

        // 找出优势维度和薄弱维度
        String strengths = scores.entrySet().stream()
                .filter(e -> e.getValue() >= 85)
                .map(e -> e.getKey())
                .collect(Collectors.joining("、"));
        if (strengths.isEmpty()) strengths = "无显著优势维度";

        String weaknesses = scores.entrySet().stream()
                .filter(e -> e.getValue() < 55)
                .map(e -> e.getKey())
                .collect(Collectors.joining("、"));
        if (weaknesses.isEmpty()) weaknesses = "无显著薄弱维度";

        // 维度名称映射
        Map<String, String> dimNames = new LinkedHashMap<>();
        dimNames.put("PROGRAMMING", "编程能力");
        dimNames.put("LOGIC", "逻辑推理");
        dimNames.put("PRODUCT", "产品思维");
        dimNames.put("TECH", "技术素养");
        dimNames.put("COMMUNICATION", "沟通表达");

        Map<String, Double> namedScores = new LinkedHashMap<>();
        scores.forEach((k, v) -> namedScores.put(dimNames.getOrDefault(k, k), v));

        return AssessmentReportResponse.builder()
                .id(result.getId())
                .type(result.getType())
                .dimensionScores(namedScores)
                .totalScore(result.getTotalScore())
                .level(level)
                .dimensionLevels(dimensionLevels)
                .strengths(strengths)
                .weaknesses(weaknesses)
                .createdAt(result.getCreatedAt())
                .build();
    }

    /**
     * 根据分数计算等级
     * 优秀 ≥85 / 良好 ≥70 / 一般 ≥55 / 待提升 <55
     */
    private String getLevel(double score) {
        if (score >= 85) return "优秀";
        if (score >= 70) return "良好";
        if (score >= 55) return "一般";
        return "待提升";
    }
}
