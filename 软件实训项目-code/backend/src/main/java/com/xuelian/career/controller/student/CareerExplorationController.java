package com.xuelian.career.controller.student;

import com.xuelian.career.common.Result;
import com.xuelian.career.dto.request.CareerExplorationRequest;
import com.xuelian.career.dto.response.CareerDirectionResponse;
import com.xuelian.career.service.CareerExplorationService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * AI职业方向探索控制器（学生端）
 */
@RestController
@RequestMapping("/api/student/career")
@RequiredArgsConstructor
public class CareerExplorationController {

    private final CareerExplorationService explorationService;

    /** POST /api/student/career/explore */
    @PostMapping("/explore")
    public Result<CareerDirectionResponse> explore(@RequestBody CareerExplorationRequest request,
                                                    HttpServletRequest httpRequest) {
        Long userId = (Long) httpRequest.getAttribute("userId");
        return Result.success(explorationService.explore(userId, request));
    }

    /** GET /api/student/career/explore/history */
    @GetMapping("/explore/history")
    public Result<List<CareerDirectionResponse>> getHistory(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        return Result.success(explorationService.getHistory(userId));
    }
}
