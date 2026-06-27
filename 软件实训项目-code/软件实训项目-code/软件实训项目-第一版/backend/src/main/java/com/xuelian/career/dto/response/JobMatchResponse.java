package com.xuelian.career.dto.response;

import com.xuelian.career.vo.SkillGapVO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

/**
 * 岗位匹配推荐响应
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JobMatchResponse {
    private Long jobId;
    private String title;
    private String direction;
    private String jd;
    private String city;
    private String salaryRange;
    /** 综合匹配度 */
    private Double matchScore;
    /** 技能匹配分 */
    private Double skillScore;
    /** 测评适配分 */
    private Double assessmentScore;
    /** 城市薪资分 */
    private Double locationScore;
    /** 匹配的技能标签 */
    private List<SkillTagVO> skillTags;
    /** 技能缺口列表 */
    private List<SkillGapVO> skillGaps;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SkillTagVO {
        private String skillName;
        /** mastered / learning / missing */
        private String status;
    }
}
