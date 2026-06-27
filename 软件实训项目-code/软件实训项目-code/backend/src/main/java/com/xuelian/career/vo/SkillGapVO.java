package com.xuelian.career.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 技能缺口 VO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SkillGapVO {
    private String skillName;
    private String userLevel;
    private String requiredLevel;
    private String gapDegree;
}
