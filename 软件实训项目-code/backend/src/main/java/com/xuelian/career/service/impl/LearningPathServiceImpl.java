package com.xuelian.career.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.xuelian.career.entity.*;
import com.xuelian.career.mapper.*;
import com.xuelian.career.service.LearningPathService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 学习路径服务实现 - 四阶段生成（BASIC→FRAMEWORK→PROJECT→INTERVIEW）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LearningPathServiceImpl implements LearningPathService {

    private final LearningPathMapper pathMapper;
    private final LearningTaskMapper taskMapper;
    private final LearningResourceMapper resourceMapper;
    private final JobSkillRequirementMapper requirementMapper;
    private final SkillMapper skillMapper;

    private static final String[] STAGES = {"BASIC", "FRAMEWORK", "PROJECT", "INTERVIEW"};

    @Override
    public LearningPath generatePath(Long userId) {
        // 获取活跃路径并归档
        LearningPath existing = pathMapper.selectOne(
                new LambdaQueryWrapper<LearningPath>().eq(LearningPath::getUserId, userId)
                        .eq(LearningPath::getStatus, "ACTIVE"));
        if (existing != null) {
            existing.setStatus("ARCHIVED");
            pathMapper.updateById(existing);
        }

        // 创建新路径
        LearningPath path = new LearningPath();
        path.setUserId(userId);
        path.setDailyHours(2.0);
        path.setTotalDays(30);
        path.setStatus("ACTIVE");
        path.setCreatedAt(LocalDateTime.now());
        pathMapper.insert(path);

        // 获取所有技能要求生成任务
        List<Long> skillIds = requirementMapper.selectList(null).stream()
                .map(JobSkillRequirement::getSkillId).distinct().limit(8).collect(Collectors.toList());
        List<Skill> skills = skillIds.isEmpty() ? new ArrayList<>() : skillMapper.selectBatchIds(skillIds);

        int order = 0;
        LocalDate today = LocalDate.now();
        for (String stage : STAGES) {
            for (Skill skill : skills) {
                List<LearningResource> resources = resourceMapper.selectList(
                        new LambdaQueryWrapper<LearningResource>()
                                .eq(LearningResource::getSkillId, skill.getId())
                                .eq(LearningResource::getStage, stage).last("LIMIT 2"));
                for (LearningResource res : resources) {
                    LearningTask task = new LearningTask();
                    task.setPathId(path.getId());
                    task.setUserId(userId);
                    task.setSkillId(skill.getId());
                    task.setTitle(res.getTitle());
                    task.setDescription(res.getDescription());
                    task.setResourceUrl(res.getUrl());
                    task.setStage(stage);
                    task.setStatus("PENDING");
                    task.setSortOrder(order++);
                    task.setDueDate(today.plusDays(order));
                    task.setCreatedAt(LocalDateTime.now());
                    taskMapper.insert(task);
                }
            }
        }
        return path;
    }

    @Override
    public LearningPath getPath(Long userId) {
        return pathMapper.selectOne(new LambdaQueryWrapper<LearningPath>()
                .eq(LearningPath::getUserId, userId)
                .eq(LearningPath::getStatus, "ACTIVE"));
    }

    @Override
    public void updateTaskStatus(Long taskId, String status) {
        LearningTask task = taskMapper.selectById(taskId);
        if (task != null) {
            task.setStatus(status);
            taskMapper.updateById(task);
        }
    }

    @Override
    public List<LearningTask> getTasks(Long userId) {
        return taskMapper.selectList(new LambdaQueryWrapper<LearningTask>()
                .eq(LearningTask::getUserId, userId)
                .orderByAsc(LearningTask::getSortOrder));
    }

    @Override
    public List<LearningResource> listResources(Long skillId, String stage) {
        LambdaQueryWrapper<LearningResource> wrapper = new LambdaQueryWrapper<>();
        if (skillId != null) wrapper.eq(LearningResource::getSkillId, skillId);
        if (stage != null && !stage.isEmpty()) wrapper.eq(LearningResource::getStage, stage);
        return resourceMapper.selectList(wrapper);
    }
}
