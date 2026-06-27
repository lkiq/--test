package com.xuelian.career.controller.student;

import com.xuelian.career.common.Result;
import com.xuelian.career.entity.LearningPath;
import com.xuelian.career.entity.LearningResource;
import com.xuelian.career.entity.LearningTask;
import com.xuelian.career.service.LearningPathService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 学习路径控制器（学生端）
 */
@RestController
@RequestMapping("/api/student/learning")
@RequiredArgsConstructor
public class LearningPathController {

    private final LearningPathService learningPathService;

    /** POST /api/student/learning/generate */
    @PostMapping("/generate")
    public Result<LearningPath> generate(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        return Result.success(learningPathService.generatePath(userId));
    }

    /** GET /api/student/learning/path */
    @GetMapping("/path")
    public Result<LearningPath> getPath(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        LearningPath path = learningPathService.getPath(userId);
        return path != null ? Result.success(path) : Result.success("暂无学习路径", null);
    }

    /** PUT /api/student/learning/tasks/{id} */
    @PutMapping("/tasks/{id}")
    public Result<Void> updateTaskStatus(@PathVariable Long id, @RequestBody Map<String, String> body) {
        learningPathService.updateTaskStatus(id, body.get("status"));
        return Result.success();
    }

    /** GET /api/student/learning/tasks */
    @GetMapping("/tasks")
    public Result<List<LearningTask>> getTasks(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        return Result.success(learningPathService.getTasks(userId));
    }

    /** GET /api/student/learning/resources?skill=&stage= */
    @GetMapping("/resources")
    public Result<List<LearningResource>> listResources(@RequestParam(required = false) Long skill,
                                                         @RequestParam(required = false) String stage) {
        return Result.success(learningPathService.listResources(skill, stage));
    }
}
