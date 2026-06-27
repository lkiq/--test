package com.xuelian.career.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 职业方向探索响应
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CareerDirectionResponse {
    private Long recordId;
    /** 综合分析 */
    private String overallAnalysis;
    /** 推荐方向列表 */
    private List<DirectionItem> directions;
    /** 结果来源 */
    private String source;
    private LocalDateTime createdAt;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DirectionItem {
        private String jobTitle;
        private String direction;
        private Integer matchScore;
        private String reason;
        private String learningPriority;
        private String growthPath;
    }
}
