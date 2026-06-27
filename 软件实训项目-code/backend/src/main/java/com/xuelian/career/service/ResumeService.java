package com.xuelian.career.service;

import com.xuelian.career.dto.response.ResumeOptimizeResponse;
import org.springframework.web.multipart.MultipartFile;
import java.util.List;

/**
 * 简历优化服务接口
 */
public interface ResumeService {
    String uploadResume(Long userId, MultipartFile file);
    ResumeOptimizeResponse analyzeResume(Long userId, Long targetJobId, String fileUrl);
    ResumeOptimizeResponse getAnalysis(Long analysisId);
    List<ResumeOptimizeResponse> getHistory(Long userId);
}
