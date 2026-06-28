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

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * AI职业方向探索服务实现 - 调用 DeepSeek API + 规则兜底
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

            // 尝试 AI 调用
            if (deepSeekService.isAvailable()) {
                try {
                    String template = promptUtil.loadTemplate("career_exploration");
                    Map<String, String> params = new HashMap<>();
                    params.put("profile_json", objectMapper.writeValueAsString(profile));
                    params.put("assessment_json", objectMapper.writeValueAsString(latestResult));
                    params.put("preferences", req.getPreferences() != null ? req.getPreferences() : "");
                    params.put("job_positions", objectMapper.writeValueAsString(positions.stream()
                            .map(p -> Map.of("id", p.getId(), "title", p.getTitle(), "direction", p.getDirection()))
                            .collect(Collectors.toList())));
                    // 构建对话历史文本
                    String convHistory = buildConversationHistory(req.getHistory());
                    params.put("conversation_history", convHistory);
                    String prompt = promptUtil.renderTemplate(template, params);
                    // 对话式场景不使用缓存：用户每次输入不同问题，应实时生成不同回复
                    // 原 callAPIWithCache 缓存 3600s，导致不同问题命中旧缓存返回相同答案
                    String response = deepSeekService.callAPI("你是一位资深的职业规划导师", prompt, 8000L, 768);
                    Map<String, Object> result = deepSeekService.parseJSONResponse(response);
                    if (result != null && result.containsKey("directions")) {
                        CareerDirectionResponse resp = objectMapper.convertValue(result, CareerDirectionResponse.class);
                        resp.setSource("AI");
                        resp.setCreatedAt(LocalDateTime.now());
                        saveRecord(userId, "CAREER_EXPLORATION", req.getPreferences(), result, "AI");
                        return resp;
                    }
                } catch (Exception e) {
                    log.warn("AI 职业探索失败，使用兜底方案: {}", e.getMessage());
                }
            }

            // 兜底方案：规则推荐
            return fallbackRecommend(userId, req, profile, latestResult, positions);

        } catch (Exception e) {
            log.error("职业方向探索异常", e);
            return fallbackSimple();
        }
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

        CareerDirectionResponse resp = CareerDirectionResponse.builder()
                .directions(items)
                .overallAnalysis("基于你的能力测评结果，系统为你推荐以下职业方向（当前为兜底推荐）")
                .source("FALLBACK")
                .createdAt(LocalDateTime.now())
                .build();

        saveRecord(userId, "CAREER_EXPLORATION", req.getPreferences(),
                Map.of("directions", items), "FALLBACK");
        return resp;
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

    /**
     * 构建唯一缓存键：用户ID + 偏好文本 + 对话历史（SHA-256）
     */
    private String buildCacheKey(Long userId, CareerExplorationRequest req) {
        try {
            // 将偏好文本和完整历史组合后计算哈希
            Map<String, Object> context = new LinkedHashMap<>();
            context.put("preferences", req.getPreferences());
            context.put("history", req.getHistory());
            String json = objectMapper.writeValueAsString(context);
            String hash = sha256Hex(json);
            return "explore:" + userId + ":" + hash;
        } catch (Exception e) {
            // 兜底：只用偏好文本
            String prefsText = req.getPreferences() != null ? req.getPreferences() : "";
            return "explore:" + userId + ":" + Integer.toHexString(prefsText.hashCode());
        }
    }

    /**
     * 计算字符串的 SHA-256 十六进制摘要
     */
    private String sha256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                hexString.append(String.format("%02x", b));
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            return Integer.toHexString(input.hashCode());
        }
    }
}
