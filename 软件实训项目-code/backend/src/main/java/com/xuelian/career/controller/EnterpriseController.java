package com.xuelian.career.controller;

import com.xuelian.career.common.Result;
import com.xuelian.career.dto.request.EnterpriseRecommendRequest;
import com.xuelian.career.dto.response.EnterpriseRecommendResponse;
import com.xuelian.career.service.EnterpriseService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 企业端控制器
 */
@RestController
@RequestMapping("/api/enterprise")
@RequiredArgsConstructor
public class EnterpriseController {

    private final EnterpriseService enterpriseService;

    /** POST /api/enterprise/recommend */
    @PostMapping("/recommend")
    public Result<EnterpriseRecommendResponse> recommend(@RequestBody EnterpriseRecommendRequest request,
                                                          HttpServletRequest httpRequest) {
        Long userId = (Long) httpRequest.getAttribute("userId");
        if (request.getProjectDescription() == null || request.getProjectDescription().length() < 20) {
            return Result.badRequest("项目描述至少需要20个字符");
        }
        return Result.success(enterpriseService.recommend(userId, request));
    }

    /** GET /api/enterprise/recommend/history */
    @GetMapping("/recommend/history")
    public Result<List<EnterpriseRecommendResponse>> getHistory(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        return Result.success(enterpriseService.getHistory(userId));
    }
}
