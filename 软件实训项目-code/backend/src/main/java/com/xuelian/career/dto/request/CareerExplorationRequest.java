package com.xuelian.career.dto.request;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * AI职业方向探索请求
 */
@Data
public class CareerExplorationRequest {
    /** 用户偏好描述 */
    private String preferences;

    /** 对话历史（可选），每项包含 role 和 content */
    private List<Map<String, String>> history;
}
