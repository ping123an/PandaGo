package com.pandago.admin.service;

import com.pandago.admin.vo.AdminDashboardVO;

/** 平台管理工作台统计服务。 */
public interface AdminDashboardService {

    /** 当前管理员可见的全平台运营数据；身份与角色由实现内部校验。 */
    AdminDashboardVO dashboard();
}
