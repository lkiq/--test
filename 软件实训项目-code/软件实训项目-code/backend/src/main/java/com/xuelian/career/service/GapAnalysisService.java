package com.xuelian.career.service;

import com.xuelian.career.dto.response.GapReportResponse;

/**
 * 能力差距分析服务接口
 */
public interface GapAnalysisService {
    GapReportResponse analyze(Long userId, Long jobId);
    GapReportResponse getReport(Long recordId);
}
