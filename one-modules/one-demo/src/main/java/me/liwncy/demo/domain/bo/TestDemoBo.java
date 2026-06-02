package me.liwncy.demo.domain.bo;

import com.baomidou.mybatisplus.annotation.TableName;
import me.liwncy.common.mybatis.core.domain.BaseEntity;
import lombok.*;

import java.time.LocalDateTime;
import java.time.LocalDate;
import java.time.LocalTime;

/**
* 测试单表业务对象 test_demo
*
* @author wanchang.li
* @date 2026-06-02 15:19:16
*/
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class TestDemoBo extends BaseEntity {

    /**
    * 主键
    */
    private Long id;
    /**
    * 租户编号
    */
    private String tenantId;
    /**
    * 部门id
    */
    private Long deptId;
    /**
    * 用户id
    */
    private Long userId;
    /**
    * 排序号
    */
    private Integer orderNum;
    /**
    * key键
    */
    private String testKey;
    /**
    * 值
    */
    private String value;
    /**
    * 版本
    */
    private Integer version;
    /**
    * 创建部门
    */
    private Long createDept;
    /**
    * 创建时间
    */
    private LocalDateTime createTime;
    /**
    * 创建人
    */
    private Long createBy;
    /**
    * 更新时间
    */
    private LocalDateTime updateTime;
    /**
    * 更新人
    */
    private Long updateBy;
    /**
    * 删除标志
    */
    private Integer delFlag;
}
