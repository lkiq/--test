package com.xuelian.career.service;

import com.xuelian.career.dto.response.JobMatchResponse;
import com.xuelian.career.entity.JobPosition;
import java.util.List;

/**
 * 岗位匹配推荐服务接口
 */
public interface JobMatchingService {
    List<JobMatchResponse> recommendJobs(Long userId);
    JobPosition getJobDetail(Long jobId);
    List<JobPosition> searchJobs(String keyword, String city);
}
