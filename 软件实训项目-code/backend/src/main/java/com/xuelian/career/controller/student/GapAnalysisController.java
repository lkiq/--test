package com.xuelian.career.controller.student;

import com.xuelian.career.common.Result;
import com.xuelian.career.dto.response.GapReportResponse;
import com.xuelian.career.service.GapAnalysisService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 能力差距分析控制器（学生端）
 */
@RestController
@RequestMapping("/api/student/gap")
@RequiredArgsConstructor
public class GapAnalysisController {

    private final GapAnalysisService gapAnalysisService;

    /** POST /api/student/gap/analyze/{jobId} */
    @PostMapping("/analyze/{jobId}")
    public Result<GapReportResponse> analyze(@PathVariable Long jobId, HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        GapReportResponse report = gapAnalysisService.analyze(userId, jobId);
        return report != null ? Result.success(report) : Result.notFound("岗位不存在");
    }

    /** GET /api/student/gap/report/{id} */
    @GetMapping("/report/{id}")
    public Result<GapReportResponse> getReport(@PathVariable Long id) {
        GapReportResponse report = gapAnalysisService.getReport(id);
        return report != null ? Result.success(report) : Result.notFound("报告不存在");
    }
}
