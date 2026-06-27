package com.xuelian.career.dto.request;

import lombok.Data;

/**
 * 企业推荐请求
 */
@Data
public class EnterpriseRecommendRequest {
    private String projectDescription;
    private String filters;
}
