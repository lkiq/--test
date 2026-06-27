package com.xuelian.career.dto.request;

import lombok.Data;

/**
 * AI职业方向探索请求
 */
@Data
public class CareerExplorationRequest {
    /** 用户偏好描述 */
    private String preferences;
}
