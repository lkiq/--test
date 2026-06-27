package com.xuelian.career.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xuelian.career.dto.response.JobMatchResponse;
import com.xuelian.career.entity.*;
import com.xuelian.career.mapper.*;
import com.xuelian.career.service.JobMatchingService;
import com.xuelian.career.vo.SkillGapVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 岗位匹配推荐服务实现 - 加权评分算法（技能50% + 测评30% + 城市薪资20%）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class JobMatchingServiceImpl implements JobMatchingService {

    private final JobPositionMapper jobPositionMapper;
    private final JobSkillRequirementMapper jobSkillRequirementMapper;
    private final SkillMapper skillMapper;
    private final CareerProfileMapper profileMapper;
    private final AssessmentResultMapper assessmentResultMapper;
    private final ObjectMapper objectMapper;

    @Override
    public List<JobMatchResponse> recommendJobs(Long userId) {
        // 获取用户画像
        CareerProfile profile = profileMapper.selectOne(
                new LambdaQueryWrapper<CareerProfile>().eq(CareerProfile::getUserId, userId));
        List<String> userSkills = parseSkills(profile != null ? profile.getSkillTags() : null);

        // 获取最新测评
        AssessmentResult latestResult = assessmentResultMapper.selectOne(
                new LambdaQueryWrapper<AssessmentResult>().eq(AssessmentResult::getUserId, userId)
                        .orderByDesc(AssessmentResult::getCreatedAt).last("LIMIT 1"));

        // 获取所有岗位
        List<JobPosition> jobs = jobPositionMapper.selectList(
                new LambdaQueryWrapper<JobPosition>().eq(JobPosition::getIsDeleted, 0));

        List<JobMatchResponse> results = new ArrayList<>();
        for (JobPosition job : jobs) {
            // 获取岗位技能要求
            List<JobSkillRequirement> requirements = jobSkillRequirementMapper.selectList(
                    new LambdaQueryWrapper<JobSkillRequirement>().eq(JobSkillRequirement::getJobId, job.getId()));
            List<Long> skillIds = requirements.stream().map(JobSkillRequirement::getSkillId).collect(Collectors.toList());
            List<Skill> skills = skillIds.isEmpty() ? new ArrayList<>() : skillMapper.selectBatchIds(skillIds);
            Map<Long, Skill> skillMap = skills.stream().collect(Collectors.toMap(Skill::getId, s -> s));

            // 计算技能匹配分（50%）
            double skillScore = 0;
            int matchedCount = 0;
            for (JobSkillRequirement req : requirements) {
                Skill skill = skillMap.get(req.getSkillId());
                if (skill != null && userSkills.contains(skill.getName())) {
                    matchedCount++;
                }
            }
            skillScore = requirements.isEmpty() ? 50 :
                    50.0 * matchedCount / requirements.size();

            // 计算测评适配分（30%）
            double assessmentScore = latestResult != null ? latestResult.getTotalScore() * 0.3 : 15;

            // 计算城市薪资分（20%）
            double locationScore = 20;
            if (profile != null && profile.getExpectedCity() != null &&
                    profile.getExpectedCity().equals(job.getCity())) {
                locationScore = 20;
            } else {
                locationScore = 10;
            }

            double totalScore = skillScore + assessmentScore + locationScore;

            // 技能标签和缺口
            List<JobMatchResponse.SkillTagVO> skillTags = new ArrayList<>();
            List<SkillGapVO> skillGaps = new ArrayList<>();
            for (JobSkillRequirement req : requirements) {
                Skill skill = skillMap.get(req.getSkillId());
                if (skill == null) continue;
                if (userSkills.contains(skill.getName())) {
                    skillTags.add(JobMatchResponse.SkillTagVO.builder()
                            .skillName(skill.getName()).status("mastered").build());
                } else {
                    skillTags.add(JobMatchResponse.SkillTagVO.builder()
                            .skillName(skill.getName()).status("missing").build());
                    skillGaps.add(SkillGapVO.builder()
                            .skillName(skill.getName()).requiredLevel(req.getRequiredLevel())
                            .userLevel("未掌握").gapDegree("需要学习").build());
                }
            }

            results.add(JobMatchResponse.builder()
                    .jobId(job.getId()).title(job.getTitle()).direction(job.getDirection())
                    .jd(job.getJd()).city(job.getCity()).salaryRange(job.getSalaryRange())
                    .matchScore(Math.round(totalScore * 10.0) / 10.0)
                    .skillScore(Math.round(skillScore * 10.0) / 10.0)
                    .assessmentScore(Math.round(assessmentScore * 10.0) / 10.0)
                    .locationScore(locationScore)
                    .skillTags(skillTags).skillGaps(skillGaps)
                    .build());
        }

        // 按匹配度降序排列
        results.sort((a, b) -> Double.compare(b.getMatchScore(), a.getMatchScore()));
        return results;
    }

    @Override
    public JobPosition getJobDetail(Long jobId) {
        return jobPositionMapper.selectById(jobId);
    }

    @Override
    public List<JobPosition> searchJobs(String keyword, String city) {
        LambdaQueryWrapper<JobPosition> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(JobPosition::getIsDeleted, 0);
        if (keyword != null && !keyword.isEmpty()) {
            wrapper.and(w -> w.like(JobPosition::getTitle, keyword)
                    .or().like(JobPosition::getDirection, keyword)
                    .or().like(JobPosition::getJd, keyword));
        }
        if (city != null && !city.isEmpty()) {
            wrapper.eq(JobPosition::getCity, city);
        }
        return jobPositionMapper.selectList(wrapper);
    }

    /**
     * 解析技能标签 JSON 为列表
     */
    @SuppressWarnings("unchecked")
    private List<String> parseSkills(String skillTagsJson) {
        try {
            if (skillTagsJson == null || skillTagsJson.isEmpty()) return new ArrayList<>();
            return objectMapper.readValue(skillTagsJson, List.class);
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }
}
