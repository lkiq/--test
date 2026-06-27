package com.xuelian.career.controller.student;

import com.xuelian.career.common.Result;
import com.xuelian.career.dto.response.ResumeOptimizeResponse;
import com.xuelian.career.service.ResumeService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

/**
 * 简历优化控制器（学生端）
 */
@RestController
@RequestMapping("/api/student/resume")
@RequiredArgsConstructor
public class ResumeController {

    private final ResumeService resumeService;

    /** POST /api/student/resume/upload */
    @PostMapping("/upload")
    public Result<String> upload(HttpServletRequest request, @RequestParam("file") MultipartFile file) {
        Long userId = (Long) request.getAttribute("userId");
        String fileUrl = resumeService.uploadResume(userId, file);
        return Result.success("上传成功", fileUrl);
    }

    /** POST /api/student/resume/analyze */
    @PostMapping("/analyze")
    public Result<ResumeOptimizeResponse> analyze(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        Long targetJobId = body.get("targetJobId") != null ? Long.valueOf(body.get("targetJobId").toString()) : null;
        String fileUrl = (String) body.get("fileUrl");
        return Result.success(resumeService.analyzeResume(userId, targetJobId, fileUrl));
    }

    /** GET /api/student/resume/analysis/{id} */
    @GetMapping("/analysis/{id}")
    public Result<ResumeOptimizeResponse> getAnalysis(@PathVariable Long id) {
        ResumeOptimizeResponse resp = resumeService.getAnalysis(id);
        return resp != null ? Result.success(resp) : Result.notFound("分析记录不存在");
    }

    /** GET /api/student/resume/history */
    @GetMapping("/history")
    public Result<List<ResumeOptimizeResponse>> getHistory(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        return Result.success(resumeService.getHistory(userId));
    }
}
