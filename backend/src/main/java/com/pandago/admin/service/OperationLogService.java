package com.pandago.admin.service;

import com.pandago.admin.entity.OperationLog;
import com.pandago.common.api.PageResult;

/**
 * 操作日志写入与查询（阶段 8）。
 *
 * <p>独立 Bean 便于各业务 Service 复用；记录操作人来自 SecurityContext，
 * 不接受前端传入的 operatorId。</p>
 */
public interface OperationLogService {

    /** 记录一条平台操作日志 */
    void record(String module, String operation, String targetType, Long targetId, String detail);

    /** 写入完整日志对象（测试或需要自定义操作人时使用） */
    void save(OperationLog log);

    /** 分页查询操作日志（可按模块筛选） */
    PageResult<OperationLog> page(String module, long pageNum, long pageSize);
}
