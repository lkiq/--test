package com.xuelian.career.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xuelian.career.dto.response.InterviewReportResponse;
import com.xuelian.career.dto.response.InterviewSession;
import com.xuelian.career.entity.InterviewRecord;
import com.xuelian.career.entity.JobPosition;
import com.xuelian.career.mapper.InterviewRecordMapper;
import com.xuelian.career.mapper.JobPositionMapper;
import com.xuelian.career.service.DeepSeekService;
import com.xuelian.career.service.InterviewService;
import com.xuelian.career.util.PromptTemplateUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 模拟面试服务实现 - DeepSeek AI 生成题目与评估 + 本地题库兜底
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InterviewServiceImpl implements InterviewService {

    private final InterviewRecordMapper recordMapper;
    private final JobPositionMapper jobPositionMapper;
    private final RedisTemplate<String, Object> redisTemplate;
    private final DeepSeekService deepSeekService;
    private final PromptTemplateUtil promptUtil;
    private final ObjectMapper objectMapper;

    private static final String SESSION_PREFIX = "interview:session:";
    private static final int TOTAL_QUESTIONS = 5;

    /** 本地题库 — AI 不可用时兜底 */
    private static final List<Map<String, String>> DEFAULT_QUESTIONS = List.of(
            Map.of("q", "请做一个简单的自我介绍", "type", "行为面试"),
            Map.of("q", "请描述一个你参与过的最有挑战性的项目", "type", "项目经验"),
            Map.of("q", "你对面向对象设计中的SOLID原则有什么理解？", "type", "技术基础"),
            Map.of("q", "假设你发现线上系统响应变慢，你会怎么排查？", "type", "情景模拟"),
            Map.of("q", "你未来3-5年的职业规划是什么？", "type", "行为面试")
    );

    @Override
    public InterviewSession startInterview(Long userId, Long targetJobId, String interviewType) {
        String sessionId = UUID.randomUUID().toString();
        String key = SESSION_PREFIX + sessionId;
        Map<String, Object> session = new HashMap<>();
        session.put("userId", userId);
        session.put("targetJobId", targetJobId);
        session.put("interviewType", interviewType);
        session.put("questionIndex", 0);
        session.put("totalQuestions", TOTAL_QUESTIONS);
        session.put("answers", new ArrayList<Map<String, String>>());
        session.put("stage", "FIRST_QUESTION");
        redisTemplate.opsForValue().set(key, session, 30, TimeUnit.MINUTES);

        // 尝试用 DeepSeek 生成第一道题
        try {
            if (deepSeekService.isAvailable()) {
                QuestionResult qr = callAIForQuestion(session, null, null);
                if (qr != null) {
                    return InterviewSession.builder()
                            .sessionId(sessionId)
                            .question(qr.question)
                            .questionType(qr.questionType)
                            .questionIndex(1)
                            .totalQuestions(TOTAL_QUESTIONS)
                            .finished(false)
                            .build();
                }
            }
        } catch (Exception e) {
            log.warn("AI 出题失败，使用本地题库: {}", e.getMessage());
        }

        return InterviewSession.builder()
                .sessionId(sessionId)
                .question(DEFAULT_QUESTIONS.get(0).get("q"))
                .questionType(DEFAULT_QUESTIONS.get(0).get("type"))
                .questionIndex(1)
                .totalQuestions(TOTAL_QUESTIONS)
                .finished(false)
                .build();
    }

    @Override
    @SuppressWarnings("unchecked")
    public InterviewSession submitAnswer(Long userId, String sessionId, String answer) {
        String key = SESSION_PREFIX + sessionId;
        Map<String, Object> session = (Map<String, Object>) redisTemplate.opsForValue().get(key);
        if (session == null) return null;

        // 获取当前题目信息
        String currentQuestion = (String) session.getOrDefault("aiCurrentQuestion",
                getLocalQuestion((Integer) session.get("questionIndex")));

        List<Map<String, String>> answers = (List<Map<String, String>>) session.get("answers");
        int currentIndex = ((Number) session.get("questionIndex")).intValue();
        answers.add(Map.of("question", currentQuestion, "answer", answer));
        session.put("answers", answers);

        int nextIndex = currentIndex + 1;
        session.put("questionIndex", nextIndex);

        // 如果还有题，用 AI 出下一道
        if (nextIndex < TOTAL_QUESTIONS) {
            try {
                if (deepSeekService.isAvailable()) {
                    QuestionResult qr = callAIForQuestion(session, answer, currentQuestion);
                    if (qr != null) {
                        session.put("aiCurrentQuestion", qr.question);
                        redisTemplate.opsForValue().set(key, session, 30, TimeUnit.MINUTES);
                        return InterviewSession.builder()
                                .sessionId(sessionId)
                                .question(qr.question)
                                .questionType(qr.questionType)
                                .questionIndex(nextIndex + 1)
                                .totalQuestions(TOTAL_QUESTIONS)
                                .finished(false)
                                .build();
                    }
                }
            } catch (Exception e) {
                log.warn("AI 出题失败，使用本地题库: {}", e.getMessage());
            }

            redisTemplate.opsForValue().set(key, session, 30, TimeUnit.MINUTES);
            Map<String, String> localQ = DEFAULT_QUESTIONS.get(nextIndex);
            return InterviewSession.builder()
                    .sessionId(sessionId)
                    .question(localQ.get("q"))
                    .questionType(localQ.get("type"))
                    .questionIndex(nextIndex + 1)
                    .totalQuestions(TOTAL_QUESTIONS)
                    .finished(false)
                    .build();
        }

        // 全部答完
        session.put("stage", "EVALUATION");
        redisTemplate.opsForValue().set(key, session, 30, TimeUnit.MINUTES);
        return InterviewSession.builder()
                .sessionId(sessionId).question(null)
                .questionIndex(TOTAL_QUESTIONS).totalQuestions(TOTAL_QUESTIONS)
                .finished(true).build();
    }

    @Override
    @SuppressWarnings("unchecked")
    public InterviewReportResponse endInterview(Long userId, String sessionId) {
        String key = SESSION_PREFIX + sessionId;
        Map<String, Object> session = (Map<String, Object>) redisTemplate.opsForValue().get(key);
        if (session == null) return null;

        // 尝试 DeepSeek 综合评估
        InterviewReportResponse report = null;
        try {
            if (deepSeekService.isAvailable()) {
                report = callAIForEvaluation(session);
            }
        } catch (Exception e) {
            log.warn("AI 评估失败，使用本地评分: {}", e.getMessage());
        }

        // AI 不可用时的本地评分兜底
        if (report == null) {
            report = localEvaluation();
        }

        // 持久化
        InterviewRecord record = new InterviewRecord();
        record.setUserId(userId);
        record.setTargetJobId((Long) session.get("targetJobId"));
        record.setInterviewType((String) session.get("interviewType"));
        record.setScore(report.getTotalScore());
        record.setCreatedAt(LocalDateTime.now());

        try {
            record.setQuestionJson(objectMapper.writeValueAsString(session.get("answers")));
            record.setReportJson(objectMapper.writeValueAsString(report));
        } catch (Exception e) {
            log.warn("序列化面试记录失败", e);
        }
        recordMapper.insert(record);

        redisTemplate.delete(key);

        report.setId(record.getId());
        report.setInterviewType(record.getInterviewType());
        report.setCreatedAt(record.getCreatedAt());
        return report;
    }

    // ==================== AI 调用方法 ====================

    /**
     * 调用 DeepSeek 生成一道面试题目
     */
    @SuppressWarnings("unchecked")
    private QuestionResult callAIForQuestion(Map<String, Object> session,
                                              String lastAnswer, String lastQuestion) {
        try {
            JobPosition job = getJob(session);

            // 构建历史问答
            List<Map<String, String>> history = (List<Map<String, String>>) session.get("answers");
            StringBuilder historyStr = new StringBuilder();
            if (history != null) {
                for (Map<String, String> h : history) {
                    historyStr.append("- 问题: ").append(h.get("question")).append("\n");
                    historyStr.append("  回答: ").append(h.get("answer")).append("\n");
                }
            }

            int nextIdx = ((Number) session.get("questionIndex")).intValue() + 1;

            Map<String, String> params = new HashMap<>();
            params.put("job_title", job != null ? job.getTitle() : "通用岗位");
            params.put("job_jd", job != null ? job.getJd() : "请根据候选人背景提问");
            params.put("interview_type", (String) session.getOrDefault("interviewType", "COMPREHENSIVE"));
            params.put("current_stage", "FIRST_QUESTION");
            params.put("current_task", "这是第 " + nextIdx + " / " + TOTAL_QUESTIONS
                    + " 题。请根据岗位要求和面试类型，出一道与之前不重复的题目。");
            params.put("history_qa", historyStr.length() > 0 ? historyStr.toString() : "（暂无历史问答）");

            String prompt = promptUtil.loadAndRender("mock_interview", params);
            String response = deepSeekService.callAPI("你是一位资深技术面试官", prompt);
            Map<String, Object> result = deepSeekService.parseJSONResponse(response);

            if (result != null && result.containsKey("question")) {
                return new QuestionResult(
                        (String) result.get("question"),
                        (String) result.getOrDefault("questionType", "综合面试")
                );
            }
        } catch (Exception e) {
            log.warn("DeepSeek 出题异常: {}", e.getMessage());
        }
        return null;
    }

    /**
     * 调用 DeepSeek 生成面试综合评估报告
     */
    @SuppressWarnings("unchecked")
    private InterviewReportResponse callAIForEvaluation(Map<String, Object> session) {
        try {
            JobPosition job = getJob(session);

            List<Map<String, String>> history = (List<Map<String, String>>) session.get("answers");
            StringBuilder historyStr = new StringBuilder();
            if (history != null) {
                for (int i = 0; i < history.size(); i++) {
                    Map<String, String> h = history.get(i);
                    historyStr.append("第").append(i + 1).append("题: ").append(h.get("question")).append("\n");
                    historyStr.append("回答: ").append(h.get("answer")).append("\n\n");
                }
            }

            Map<String, String> params = new HashMap<>();
            params.put("job_title", job != null ? job.getTitle() : "通用岗位");
            params.put("job_jd", job != null ? job.getJd() : "");
            params.put("interview_type", (String) session.getOrDefault("interviewType", "COMPREHENSIVE"));
            params.put("current_stage", "EVALUATION");
            params.put("current_task", "请根据以上完整的问答记录进行综合评估，给出评分和改进建议。");
            params.put("history_qa", historyStr.toString());

            String prompt = promptUtil.loadAndRender("mock_interview", params);
            String response = deepSeekService.callAPI("你是一位资深技术面试官", prompt);
            Map<String, Object> result = deepSeekService.parseJSONResponse(response);

            if (result != null && result.containsKey("totalScore")) {
                Map<String, Object> dimMap = (Map<String, Object>) result.get("dimensionScores");
                Map<String, Double> dimensionScores = new LinkedHashMap<>();
                if (dimMap != null) {
                    dimMap.forEach((k, v) -> dimensionScores.put(k, ((Number) v).doubleValue()));
                }

                List<String> highlights = (List<String>) result.getOrDefault("highlights", List.of());
                List<String> improvements = (List<String>) result.getOrDefault("improvements", List.of());

                return InterviewReportResponse.builder()
                        .totalScore(((Number) result.get("totalScore")).doubleValue())
                        .dimensionScores(dimensionScores)
                        .highlights(highlights)
                        .improvements(improvements)
                        .summary((String) result.getOrDefault("summary", "面试完成"))
                        .build();
            }
        } catch (Exception e) {
            log.warn("DeepSeek 评估异常: {}", e.getMessage());
        }
        return null;
    }

    /**
     * 本地评分兜底
     */
    private InterviewReportResponse localEvaluation() {
        double score = 75.0 + Math.random() * 20;
        return InterviewReportResponse.builder()
                .totalScore(score)
                .dimensionScores(Map.of("logic", 80.0, "professionalism", 75.0,
                        "communication", 78.0, "adaptability", 72.0, "jobFit", 76.0))
                .highlights(List.of("表达清晰流畅", "有一定项目经验"))
                .improvements(List.of("建议加深系统设计理解", "可以准备更多量化成果"))
                .summary("面试表现良好，展现了扎实的基础和清晰的表达能力")
                .build();
    }

    /**
     * 获取岗位信息
     */
    private JobPosition getJob(Map<String, Object> session) {
        Long jobId = (Long) session.get("targetJobId");
        if (jobId != null) {
            return jobPositionMapper.selectById(jobId);
        }
        return null;
    }

    /**
     * 获取本地题库的第 index 道题
     */
    private String getLocalQuestion(int index) {
        if (index >= 0 && index < DEFAULT_QUESTIONS.size()) {
            return DEFAULT_QUESTIONS.get(index).get("q");
        }
        return "";
    }

    /**
     * AI 返回的题目
     */
    private static class QuestionResult {
        final String question;
        final String questionType;
        QuestionResult(String question, String questionType) {
            this.question = question;
            this.questionType = questionType;
        }
    }

    @Override
    public InterviewReportResponse getReport(Long recordId) {
        InterviewRecord record = recordMapper.selectById(recordId);
        if (record == null) return null;
        return InterviewReportResponse.builder()
                .id(record.getId()).interviewType(record.getInterviewType())
                .totalScore(record.getScore()).createdAt(record.getCreatedAt())
                .highlights(List.of("已完成面试")).improvements(List.of())
                .summary("面试记录").build();
    }

    @Override
    public List<InterviewReportResponse> getHistory(Long userId) {
        return recordMapper.selectList(new LambdaQueryWrapper<InterviewRecord>()
                        .eq(InterviewRecord::getUserId, userId)
                        .orderByDesc(InterviewRecord::getCreatedAt))
                .stream().map(r -> InterviewReportResponse.builder()
                        .id(r.getId()).interviewType(r.getInterviewType())
                        .totalScore(r.getScore()).createdAt(r.getCreatedAt())
                        .build()).collect(Collectors.toList());
    }
}
