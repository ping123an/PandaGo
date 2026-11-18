package com.pandago.address.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 收货地址，与 user_address 表对应。
 *
 * <p>表中 default_flag 是 MySQL 生成列，仅用于数据库层唯一约束，
 * 不参与业务读写，因此不在 Entity 中声明。</p>
 */
@Data
@TableName("user_address")
public class UserAddress {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String receiverName;

    private String phone;

    private String province;

    private String city;

    private String district;

    private String detailAddress;

    /** 1 默认 / 0 非默认 */
    private Integer isDefault;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    private Integer deleted;
}
