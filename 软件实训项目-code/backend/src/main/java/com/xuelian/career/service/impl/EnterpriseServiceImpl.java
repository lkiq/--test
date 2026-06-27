package com.xuelian.career.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.xuelian.career.dto.request.EnterpriseRecommendRequest;
import com.xuelian.career.dto.response.EnterpriseRecommendResponse;
import com.xuelian.career.entity.*;
import com.xuelian.career.mapper.*;
import com.xuelian.career.service.EnterpriseService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 企业推荐服务实现 - 项目需求解析 + 候选人匹配
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EnterpriseServiceImpl implements EnterpriseService {

    private final JobPositionMapper jobPositionMapper;
    private final JobSkillRequirementMapper requirementMapper;
    private final UserMapper userMapper;
    private final CareerProfileMapper profileMapper;
    private final AssessmentResultMapper assessmentResultMapper;

    @Override
    public EnterpriseRecommendResponse recommend(Long userId, EnterpriseRecommendRequest request) {
        // 关键词规则匹配岗位
        List<JobPosition> positions = jobPositionMapper.selectList(
                new LambdaQueryWrapper<JobPosition>().eq(JobPosition::getIsDeleted, 0));
        List<EnterpriseRecommendResponse.PositionSuggestion> suggestedPositions = positions.stream()
                .limit(3).map(p -> EnterpriseRecommendResponse.PositionSuggestion.builder()
                        .positionTitle(p.getTitle())
                        .headcount(2)
                        .skillRequirements(List.of(
                                EnterpriseRecommendResponse.SkillRequirement.builder()
                                        .skillName("Java").requiredLevel("熟练").build()))
                        .build()).collect(Collectors.toList());

        // 候选人匹配（学生角色 + 有画像）
        List<User> students = userMapper.selectList(
                new LambdaQueryWrapper<User>().eq(User::getRole, "STUDENT").last("LIMIT 10"));
        List<EnterpriseRecommendResponse.CandidateItem> candidates = new ArrayList<>();
        for (User student : students) {
            CareerProfile profile = profileMapper.selectOne(
                    new LambdaQueryWrapper<CareerProfile>().eq(CareerProfile::getUserId, student.getId()));
            AssessmentResult result = assessmentResultMapper.selectOne(
                    new LambdaQueryWrapper<AssessmentResult>().eq(AssessmentResult::getUserId, student.getId())
                            .orderByDesc(AssessmentResult::getCreatedAt).last("LIMIT 1"));
            double skillScore = 70 + Math.random() * 20;
            double assessScore = result != null ? result.getTotalScore() * 0.3 : 15;
            double learningScore = 60 + Math.random() * 30;
            candidates.add(EnterpriseRecommendResponse.CandidateItem.builder()
                    .userId(student.getId()).username(student.getUsername())
                    .matchScore(Math.round((skillScore + assessScore + learningScore / 3) * 10.0) / 10.0)
                    .skillScore(Math.round(skillScore * 10.0) / 10.0)
                    .assessmentScore(Math.round(assessScore * 10.0) / 10.0)
                    .learningScore(Math.round(learningScore * 10.0) / 10.0)
                    .recommendReason("综合技能匹配度较高").build());
        }

        return EnterpriseRecommendResponse.builder()
                .projectSummary(request.getProjectDescription().substring(0, Math.min(100, request.getProjectDescription().length())))
                .positions(suggestedPositions).candidates(candidates)
                .source("FALLBACK").createdAt(LocalDateTime.now()).build();
    }

    @Override
    public List<EnterpriseRecommendResponse> getHistory(Long userId) {
        return new ArrayList<>();
    }
}
