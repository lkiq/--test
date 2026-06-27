package com.xuelian.career.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xuelian.career.dto.response.GapReportResponse;
import com.xuelian.career.entity.*;
import com.xuelian.career.mapper.*;
import com.xuelian.career.service.GapAnalysisService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 能力差距分析服务实现 - 对比用户技能水平与岗位要求，计算差距
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GapAnalysisServiceImpl implements GapAnalysisService {

    private final JobPositionMapper jobPositionMapper;
    private final JobSkillRequirementMapper requirementMapper;
    private final SkillMapper skillMapper;
    private final CareerProfileMapper profileMapper;
    private final AssessmentResultMapper assessmentResultMapper;
    private final RecommendationRecordMapper recordMapper;
    private final ObjectMapper objectMapper;

    /** 技能等级映射为数值 */
    private static final Map<String, Integer> LEVEL_MAP = Map.of(
            "未掌握", 0, "了解", 1, "掌握", 2, "熟练", 3, "精通", 4
    );

    @Override
    public GapReportResponse analyze(Long userId, Long jobId) {
        JobPosition job = jobPositionMapper.selectById(jobId);
        if (job == null) return null;

        // 获取用户画像
        CareerProfile profile = profileMapper.selectOne(
                new LambdaQueryWrapper<CareerProfile>().eq(CareerProfile::getUserId, userId));
        List<String> userSkillsList = parseSkills(profile != null ? profile.getSkillTags() : null);

        // 获取岗位技能要求
        List<JobSkillRequirement> requirements = requirementMapper.selectList(
                new LambdaQueryWrapper<JobSkillRequirement>().eq(JobSkillRequirement::getJobId, jobId));
        List<Long> skillIds = requirements.stream().map(JobSkillRequirement::getSkillId).collect(Collectors.toList());
        List<Skill> skills = skillIds.isEmpty() ? new ArrayList<>() : skillMapper.selectBatchIds(skillIds);
        Map<Long, Skill> skillMap = skills.stream().collect(Collectors.toMap(Skill::getId, s -> s));

        // 获取最新测评结果
        AssessmentResult latestResult = assessmentResultMapper.selectOne(
                new LambdaQueryWrapper<AssessmentResult>().eq(AssessmentResult::getUserId, userId)
                        .orderByDesc(AssessmentResult::getCreatedAt).last("LIMIT 1"));

        // 构建技能对比
        Map<String, String> userSkills = new LinkedHashMap<>();
        Map<String, String> requiredSkills = new LinkedHashMap<>();
        List<GapReportResponse.GapItem> gaps = new ArrayList<>();

        for (JobSkillRequirement req : requirements) {
            Skill skill = skillMap.get(req.getSkillId());
            if (skill == null) continue;

            String skillName = skill.getName();
            String requiredLevel = req.getRequiredLevel();
            String userLevel = userSkillsList.contains(skillName) ? "掌握" : "未掌握";

            int userLevelNum = LEVEL_MAP.getOrDefault(userLevel, 0);
            int requiredLevelNum = LEVEL_MAP.getOrDefault(requiredLevel, 2);
            int gap = requiredLevelNum - userLevelNum;

            String gapDegree;
            int priority;
            if (gap <= 0) {
                gapDegree = "完全达标";
                priority = 0;
            } else if (gap == 1) {
                gapDegree = "基本达标";
                priority = 1;
            } else if (gap <= 2) {
                gapDegree = "需要提升";
                priority = 2;
            } else {
                gapDegree = "严重不足";
                priority = 3;
            }

            requiredSkills.put(skillName, requiredLevel);
            userSkills.put(skillName, userLevel);
            gaps.add(GapReportResponse.GapItem.builder()
                    .skillName(skillName).userLevel(userLevel).requiredLevel(requiredLevel)
                    .gapDegree(gapDegree).priority(priority).build());
        }

        // 按优先级排序
        gaps.sort((a, b) -> Integer.compare(b.getPriority(), a.getPriority()));

        // 计算综合匹配度
        long metCount = gaps.stream().filter(g -> g.getPriority() == 0).count();
        double overallMatch = gaps.isEmpty() ? 0 : Math.round(100.0 * metCount / gaps.size());

        // 雷达图数据
        List<String> radarDims = gaps.stream().map(GapReportResponse.GapItem::getSkillName).collect(Collectors.toList());
        List<Double> userValues = gaps.stream().map(g -> (double) LEVEL_MAP.getOrDefault(g.getUserLevel(), 0) * 25.0).collect(Collectors.toList());
        List<Double> requiredValues = gaps.stream().map(g -> (double) LEVEL_MAP.getOrDefault(g.getRequiredLevel(), 0) * 25.0).collect(Collectors.toList());

        // 保存记录
        saveRecord(userId, jobId, overallMatch, gaps);

        return GapReportResponse.builder()
                .jobId(jobId).jobTitle(job.getTitle()).overallMatch(overallMatch)
                .userSkills(userSkills).requiredSkills(requiredSkills).gaps(gaps)
                .radarChart(GapReportResponse.RadarChartData.builder()
                        .dimensions(radarDims).userValues(userValues).requiredValues(requiredValues).build())
                .suggestions("建议优先提升优先级较高的技能缺口").build();
    }

    @Override
    public GapReportResponse getReport(Long recordId) {
        RecommendationRecord record = recordMapper.selectById(recordId);
        if (record == null) return null;
        try {
            return objectMapper.readValue(record.getResultJson(), GapReportResponse.class);
        } catch (Exception e) { return null; }
    }

    private void saveRecord(Long userId, Long jobId, double score, List<GapReportResponse.GapItem> gaps) {
        try {
            RecommendationRecord record = new RecommendationRecord();
            record.setUserId(userId);
            record.setType("GAP_ANALYSIS");
            record.setInputText(String.valueOf(jobId));
            record.setResultJson(objectMapper.writeValueAsString(Map.of("score", score, "gaps", gaps)));
            record.setSource("RULE");
            record.setCreatedAt(LocalDateTime.now());
            recordMapper.insert(record);
        } catch (Exception e) { log.warn("保存差距分析记录失败", e); }
    }

    @SuppressWarnings("unchecked")
    private List<String> parseSkills(String skillTagsJson) {
        try {
            if (skillTagsJson == null || skillTagsJson.isEmpty()) return new ArrayList<>();
            return objectMapper.readValue(skillTagsJson, new TypeReference<List<String>>() {});
        } catch (Exception e) { return new ArrayList<>(); }
    }
}
