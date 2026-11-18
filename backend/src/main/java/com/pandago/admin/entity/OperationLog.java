package com.pandago.admin.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 平台操作日志实体（阶段 8）：记录管理端关键操作的操作人、动作、对象与意见。 */
@Data
@TableName("operation_log")
public class OperationLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long operatorId;

    private String operatorName;

    /** PRODUCT / CATEGORY / USER / ORDER / SHIPMENT */
    private String module;

    /** AUDIT / SHIP / ENABLE / DISABLE / CREATE / UPDATE ... */
    private String operation;

    private String targetType;

    private Long targetId;

    private String detail;

    private LocalDateTime createdAt;
}
