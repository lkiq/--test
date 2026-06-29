package com.xuelian.career.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xuelian.career.dto.request.CareerExplorationRequest;
import com.xuelian.career.dto.response.CareerDirectionResponse;
import com.xuelian.career.entity.*;
import com.xuelian.career.mapper.*;
import com.xuelian.career.service.CareerExplorationService;
import com.xuelian.career.service.DeepSeekService;
import com.xuelian.career.util.PromptTemplateUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * AI职业方向探索服务实现 - 支持意图分流：职业方向推荐 / 通用行业咨询
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CareerExplorationServiceImpl implements CareerExplorationService {

    private final CareerProfileMapper profileMapper;
    private final AssessmentResultMapper assessmentResultMapper;
    private final JobPositionMapper jobPositionMapper;
    private final RecommendationRecordMapper recordMapper;
    private final DeepSeekService deepSeekService;
    private final PromptTemplateUtil promptUtil;
    private final ObjectMapper objectMapper;

    /** 意图：职业方向推荐 */
    private static final String INTENT_RECOMMENDATION = "RECOMMENDATION";
    /** 意图：通用行业咨询 */
    private static final String INTENT_GENERAL = "GENERAL";

    @Override
    public CareerDirectionResponse explore(Long userId, CareerExplorationRequest req) {
        try {
            // 获取用户画像和测评结果
            CareerProfile profile = profileMapper.selectOne(
                    new LambdaQueryWrapper<CareerProfile>().eq(CareerProfile::getUserId, userId));
            AssessmentResult latestResult = assessmentResultMapper.selectOne(
                    new LambdaQueryWrapper<AssessmentResult>().eq(AssessmentResult::getUserId, userId)
                            .orderByDesc(AssessmentResult::getCreatedAt).last("LIMIT 1"));
            List<JobPosition> positions = jobPositionMapper.selectList(
                    new LambdaQueryWrapper<JobPosition>().eq(JobPosition::getIsDeleted, 0));

            // API 不可用时直接兜底
            if (!deepSeekService.isAvailable()) {
                return fallbackRecommend(userId, req, profile, latestResult, positions);
            }

            // 1. 轻量意图识别：判断用户是在做岗位推荐还是通用咨询
            String intent = classifyIntent(req.getPreferences());
            if (INTENT_GENERAL.equals(intent)) {
                // 2. 通用行业咨询：自由文本回答
                CareerDirectionResponse generalResp = answerGeneralQuestion(userId, req, profile, latestResult, positions);
                if (generalResp != null) {
                    return generalResp;
                }
                // 通用问答失败时降级到职业推荐逻辑
            }

            // 3. 职业方向推荐：保持原有 directions JSON 格式与业务逻辑
            return doCareerRecommendation(userId, req, profile, latestResult, positions);

        } catch (Exception e) {
            log.error("职业方向探索异常", e);
            return fallbackSimple();
        }
    }

    /**
     * 职业方向推荐核心逻辑（原 explore 中的 AI 推荐流程）
     * 保持原有 prompt 与 JSON 解析逻辑不变
     */
    private CareerDirectionResponse doCareerRecommendation(Long userId, CareerExplorationRequest req,
                                                           CareerProfile profile, AssessmentResult latestResult,
                                                           List<JobPosition> positions) {
        try {
            String template = promptUtil.loadTemplate("career_exploration");
            Map<String, String> params = buildCareerParams(req, profile, latestResult, positions);
            String prompt = promptUtil.renderTemplate(template, params);

            // 对话式场景不使用缓存：用户每次输入不同问题，应实时生成不同回复
            String response = deepSeekService.callAPI("你是一位资深的职业规划导师", prompt, 8000L, 768);
            Map<String, Object> result = deepSeekService.parseJSONResponse(response);
            if (result != null && result.containsKey("directions")) {
                CareerDirectionResponse resp = objectMapper.convertValue(result, CareerDirectionResponse.class);
                resp.setSource("AI");
                resp.setCreatedAt(LocalDateTime.now());
                saveRecord(userId, "CAREER_EXPLORATION", req.getPreferences(), result, "AI");
                return resp;
            }
            log.warn("职业推荐 AI 返回结果缺少 directions 字段，使用兜底方案");
        } catch (Exception e) {
            log.warn("AI 职业探索失败，使用兜底方案: {}", e.getMessage());
        }
        return fallbackRecommend(userId, req, profile, latestResult, positions);
    }

    /**
     * 通用行业咨询回答
     * 返回 CareerDirectionResponse：answer 放入 overallAnalysis，directions 为空
     */
    private CareerDirectionResponse answerGeneralQuestion(Long userId, CareerExplorationRequest req,
                                                          CareerProfile profile, AssessmentResult latestResult,
                                                          List<JobPosition> positions) {
        try {
            String question = req.getPreferences() != null ? req.getPreferences() : "";
            if (question.isBlank()) {
                log.warn("通用咨询问题为空，降级到职业推荐");
                return null;
            }
            String template = promptUtil.loadTemplate("career_general_qa");
            Map<String, String> params = buildCareerParams(req, profile, latestResult, positions);
            // 通用咨询 prompt 使用 {{question}} 占位当前问题
            params.put("question", question);
            String prompt = promptUtil.renderTemplate(template, params);

            String response = deepSeekService.callAPI("你是一位资深的职业规划导师", prompt, 8000L, 768);
            Map<String, Object> result = deepSeekService.parseJSONResponse(response);
            if (result != null && result.containsKey("answer")) {
                String answer = (String) result.get("answer");
                CareerDirectionResponse resp = CareerDirectionResponse.builder()
                        .overallAnalysis(answer)
                        .directions(new ArrayList<>())
                        .source("AI")
                        .createdAt(LocalDateTime.now())
                        .build();
                saveRecord(userId, "CAREER_EXPLORATION", req.getPreferences(),
                        Map.of("answer", answer, "intent", INTENT_GENERAL), "AI");
                return resp;
            }
            log.warn("通用咨询 AI 返回结果缺少 answer 字段，降级到职业推荐");
        } catch (Exception e) {
            log.warn("通用咨询 AI 调用失败，降级到职业推荐: {}", e.getMessage());
        }
        return null;
    }

    /**
     * 轻量意图识别：调用 DeepSeek 判断用户问题属于 RECOMMENDATION 还是 GENERAL
     * 分类失败时默认返回 RECOMMENDATION，兼容存量行为
     */
    private String classifyIntent(String question) {
        try {
            String q = question != null ? question : "";
            Map<String, String> params = new HashMap<>();
            params.put("question", q);
            String prompt = promptUtil.loadAndRender("career_intent_classifier", params);

            // 轻量调用：快速分类，max_tokens 128 防止中文输出被截断
            String response = deepSeekService.callAPI("你是一位意图分类专家", prompt, 3000L, 128);
            Map<String, Object> result = deepSeekService.parseJSONResponse(response);
            log.info("意图识别结果: question={}, response={}, result={}", q, response, result);
            if (result != null && result.get("intent") instanceof String intent) {
                if (INTENT_GENERAL.equalsIgnoreCase(intent)) {
                    return INTENT_GENERAL;
                }
            }
        } catch (Exception e) {
            log.warn("意图识别失败，默认按职业推荐处理: {}", e.getMessage());
        }
        return INTENT_RECOMMENDATION;
    }

    /**
     * 构建职业探索相关 prompt 参数（职业推荐与通用咨询共用）
     */
    private Map<String, String> buildCareerParams(CareerExplorationRequest req,
                                                  CareerProfile profile, AssessmentResult latestResult,
                                                  List<JobPosition> positions) {
        Map<String, String> params = new HashMap<>();
        try {
            params.put("profile_json", objectMapper.writeValueAsString(profile));
        } catch (Exception e) {
            params.put("profile_json", "（未填写）");
        }
        try {
            params.put("assessment_json", objectMapper.writeValueAsString(latestResult));
        } catch (Exception e) {
            params.put("assessment_json", "（未测评）");
        }
        params.put("preferences", req.getPreferences() != null ? req.getPreferences() : "");
        try {
            params.put("job_positions", objectMapper.writeValueAsString(positions.stream()
                    .map(p -> Map.of("id", p.getId(), "title", p.getTitle(), "direction", p.getDirection()))
                    .collect(Collectors.toList())));
        } catch (Exception e) {
            params.put("job_positions", "[]");
        }
        params.put("conversation_history", buildConversationHistory(req.getHistory()));
        return params;
    }

    /**
     * 规则推荐兜底：测评分数排名 + 技能标签匹配
     */
    private CareerDirectionResponse fallbackRecommend(Long userId, CareerExplorationRequest req,
                                                        CareerProfile profile, AssessmentResult result,
                                                        List<JobPosition> positions) {
        List<CareerDirectionResponse.DirectionItem> items = new ArrayList<>();
        // 简单按评分推荐前5个岗位
        for (int i = 0; i < Math.min(5, positions.size()); i++) {
            JobPosition p = positions.get(i);
            items.add(CareerDirectionResponse.DirectionItem.builder()
                    .jobTitle(p.getTitle())
                    .direction(p.getDirection())
                    .matchScore(70 + (5 - i) * 5)
                    .reason("根据你的测评结果和岗位需求匹配")
                    .learningPriority(i < 2 ? "高" : "中")
                    .growthPath("建议从基础技能开始，逐步深入到项目实战")
                    .build());
        }

        // 兜底文案根据问题类型区分，避免任何输入都出现固定"技术基础薄弱"描述
        String question = req.getPreferences() != null ? req.getPreferences() : "";
        String overallAnalysis;
        if (isLikelyGeneralQuestion(question)) {
            overallAnalysis = "当前无法获取 AI 分析，建议换个方式提问或稍后重试。";
        } else {
            overallAnalysis = "基于你的能力测评结果，系统为你推荐以下职业方向（当前为兜底推荐）";
        }

        CareerDirectionResponse resp = CareerDirectionResponse.builder()
                .directions(items)
                .overallAnalysis(overallAnalysis)
                .source("FALLBACK")
                .createdAt(LocalDateTime.now())
                .build();

        saveRecord(userId, "CAREER_EXPLORATION", req.getPreferences(),
                Map.of("directions", items, "overallAnalysis", overallAnalysis), "FALLBACK");
        return resp;
    }

    /**
     * 判断用户问题是否更像通用咨询而非职业推荐
     */
    private boolean isLikelyGeneralQuestion(String question) {
        if (question == null || question.isBlank()) return false;
        String q = question.toLowerCase();
        String[] generalKeywords = {"趋势", "发展", "前景", "薪资", "工资", "面试", "简历", "学习", "怎么学",
                "如何准备", "行业", "公司", "企业", "offer", "跳槽", "转行", "加班", "福利"};
        for (String kw : generalKeywords) {
            if (q.contains(kw)) return true;
        }
        return false;
    }

    private CareerDirectionResponse fallbackSimple() {
        return CareerDirectionResponse.builder()
                .directions(new ArrayList<>())
                .overallAnalysis("AI服务暂时不可用，请稍后重试")
                .source("FALLBACK")
                .createdAt(LocalDateTime.now())
                .build();
    }

    @Override
    public List<CareerDirectionResponse> getHistory(Long userId) {
        return recordMapper.selectList(new LambdaQueryWrapper<RecommendationRecord>()
                        .eq(RecommendationRecord::getUserId, userId)
                        .eq(RecommendationRecord::getType, "CAREER_EXPLORATION")
                        .orderByDesc(RecommendationRecord::getCreatedAt))
                .stream().map(r -> {
                    try {
                        CareerDirectionResponse resp = objectMapper.readValue(r.getResultJson(), CareerDirectionResponse.class);
                        resp.setRecordId(r.getId());
                        return resp;
                    } catch (Exception e) { return null; }
                }).filter(Objects::nonNull).collect(Collectors.toList());
    }

    private void saveRecord(Long userId, String type, String input, Map<String, Object> result, String source) {
        try {
            RecommendationRecord record = new RecommendationRecord();
            record.setUserId(userId);
            record.setType(type);
            record.setInputText(input);
            record.setResultJson(objectMapper.writeValueAsString(result));
            record.setSource(source);
            record.setCreatedAt(LocalDateTime.now());
            recordMapper.insert(record);
        } catch (Exception e) {
            log.warn("保存推荐记录失败: {}", e.getMessage());
        }
    }

    /**
     * 将对话历史列表格式化为文本，供 AI prompt 使用
     */
    private String buildConversationHistory(List<Map<String, String>> history) {
        if (history == null || history.isEmpty()) return "（无）";
        StringBuilder sb = new StringBuilder();
        for (Map<String, String> msg : history) {
            String role = msg.getOrDefault("role", "user");
            String content = msg.getOrDefault("content", "");
            // role: user -> "用户", assistant -> "AI导师"
            String label = "user".equals(role) ? "用户" : "AI导师";
            sb.append(label).append("：").append(content).append("\n");
        }
        return sb.toString();
    }
}
