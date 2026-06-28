package com.xuelian.career.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xuelian.career.common.BusinessException;
import com.xuelian.career.dto.response.ResumeOptimizeResponse;
import com.xuelian.career.entity.JobPosition;
import com.xuelian.career.entity.ResumeAnalysis;
import com.xuelian.career.mapper.JobPositionMapper;
import com.xuelian.career.mapper.ResumeAnalysisMapper;
import com.xuelian.career.service.DeepSeekService;
import com.xuelian.career.service.ResumeService;
import com.xuelian.career.util.FileUtil;
import com.xuelian.career.util.PromptTemplateUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 简历优化服务实现 - 文件上传 + AI 分析 + 兜底方案
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ResumeServiceImpl implements ResumeService {

    private final ResumeAnalysisMapper analysisMapper;
    private final JobPositionMapper jobPositionMapper;
    private final DeepSeekService deepSeekService;
    private final PromptTemplateUtil promptUtil;
    private final FileUtil fileUtil;
    private final ObjectMapper objectMapper;

    @Override
    public String uploadResume(Long userId, MultipartFile file) {
        return fileUtil.saveFile(file, "resumes/" + userId);
    }

    @Override
    public ResumeOptimizeResponse analyzeResume(Long userId, Long targetJobId, String fileUrl) {
        JobPosition job = targetJobId != null ? jobPositionMapper.selectById(targetJobId) : null;

        try {
            if (deepSeekService.isAvailable()) {
                String template = promptUtil.loadTemplate("resume_optimize");
                Map<String, String> params = new HashMap<>();
                params.put("resume_text", "[简历文件: " + fileUrl + "]");
                params.put("job_jd", job != null ? job.getJd() : "通用岗位");
                params.put("skill_requirements", "");
                String prompt = promptUtil.renderTemplate(template, params);
                String response = deepSeekService.callAPI("你是一位简历优化专家", prompt, 5000L, 512);
                Map<String, Object> result = deepSeekService.parseJSONResponse(response);
                if (result != null) {
                    ResumeOptimizeResponse resp = objectMapper.convertValue(result, ResumeOptimizeResponse.class);
                    saveAnalysis(userId, targetJobId, fileUrl, resp, "AI");
                    return resp;
                }
            }
        } catch (Exception e) {
            log.warn("AI 简历分析失败，使用兜底方案: {}", e.getMessage());
        }

        // 兜底方案
        return fallbackAnalysis(userId, targetJobId, fileUrl);
    }

    /**
     * 兜底方案：预置简历检查清单
     */
    private ResumeOptimizeResponse fallbackAnalysis(Long userId, Long targetJobId, String fileUrl) {
        ResumeOptimizeResponse resp = ResumeOptimizeResponse.builder()
                .score(70.0)
                .dimensionScores(Map.of("completeness", 75.0, "matching", 70.0,
                        "quantification", 65.0, "expression", 70.0))
                .issues(List.of(
                        ResumeOptimizeResponse.IssueItem.builder()
                                .severity("中").category("完整性")
                                .description("建议补充量化的工作成果数据")
                                .suggestion("在项目描述中添加具体的数据指标，如性能提升百分比、用户量级等")
                                .exampleRewrite("优化了SQL查询，将接口响应时间从 3s 降低到 200ms")
                                .build(),
                        ResumeOptimizeResponse.IssueItem.builder()
                                .severity("中").category("匹配度")
                                .description("建议突出与目标岗位匹配的技能和项目经验")
                                .suggestion("将与目标岗位最相关的经验放在简历最前面")
                                .exampleRewrite("")
                                .build()
                ))
                .optimizedSnippets(List.of())
                .summary("简历整体质量良好，建议补充量化数据和强调岗位匹配度")
                .source("FALLBACK")
                .createdAt(LocalDateTime.now())
                .build();

        saveAnalysis(userId, targetJobId, fileUrl, resp, "FALLBACK");
        return resp;
    }

    @Override
    public ResumeOptimizeResponse getAnalysis(Long analysisId) {
        ResumeAnalysis analysis = analysisMapper.selectById(analysisId);
        if (analysis == null) return null;
        try {
            return objectMapper.readValue(analysis.getResultJson(), ResumeOptimizeResponse.class);
        } catch (Exception e) { return null; }
    }

    @Override
    public List<ResumeOptimizeResponse> getHistory(Long userId) {
        return analysisMapper.selectList(new LambdaQueryWrapper<ResumeAnalysis>()
                        .eq(ResumeAnalysis::getUserId, userId)
                        .orderByDesc(ResumeAnalysis::getCreatedAt))
                .stream().map(a -> {
                    try {
                        ResumeOptimizeResponse resp = objectMapper.readValue(a.getResultJson(), ResumeOptimizeResponse.class);
                        resp.setId(a.getId());
                        return resp;
                    } catch (Exception e) { return null; }
                }).filter(Objects::nonNull).collect(Collectors.toList());
    }

    private void saveAnalysis(Long userId, Long targetJobId, String fileUrl,
                              ResumeOptimizeResponse resp, String source) {
        try {
            ResumeAnalysis analysis = new ResumeAnalysis();
            analysis.setUserId(userId);
            analysis.setTargetJobId(targetJobId);
            analysis.setFileUrl(fileUrl);
            analysis.setScore(resp.getScore());
            analysis.setResultJson(objectMapper.writeValueAsString(resp));
            analysis.setCreatedAt(LocalDateTime.now());
            analysisMapper.insert(analysis);
        } catch (Exception e) { log.warn("保存简历分析记录失败", e); }
    }
}
