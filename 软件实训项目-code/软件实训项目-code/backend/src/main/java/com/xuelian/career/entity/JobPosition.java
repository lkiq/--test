package com.xuelian.career.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

/**
 * 岗位表 - 存储平台中的就业岗位信息
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("job_position")
public class JobPosition {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 岗位名称 */
    private String title;

    /** 岗位方向：后端开发/前端开发/数据/产品/测试等 */
    private String direction;

    /** 岗位描述（JD） */
    private String jd;

    /** 工作城市 */
    private String city;

    /** 薪资范围 */
    private String salaryRange;

    /** 逻辑删除标记 */
    @TableLogic
    private Integer isDeleted;

    /** 创建时间 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
