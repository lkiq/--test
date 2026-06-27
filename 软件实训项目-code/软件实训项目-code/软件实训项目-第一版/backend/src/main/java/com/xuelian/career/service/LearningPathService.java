package com.xuelian.career.service;

import com.xuelian.career.entity.LearningPath;
import com.xuelian.career.entity.LearningResource;
import com.xuelian.career.entity.LearningTask;
import java.util.List;

/**
 * 学习路径服务接口
 */
public interface LearningPathService {
    LearningPath generatePath(Long userId);
    LearningPath getPath(Long userId);
    void updateTaskStatus(Long taskId, String status);
    List<LearningTask> getTasks(Long userId);
    List<LearningResource> listResources(Long skillId, String stage);
}
