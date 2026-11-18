package com.pandago.admin.service;

import com.pandago.admin.vo.AdminUserVO;
import com.pandago.common.api.PageResult;

/** 管理端用户管理（阶段 8）。 */
public interface AdminUserService {

    /** 用户分页，可按用户名模糊筛选 */
    PageResult<AdminUserVO> page(String username, long pageNum, long pageSize);

    /** 启用/禁用用户；禁止禁用自己 */
    void updateStatus(Long userId, Integer status);
}
