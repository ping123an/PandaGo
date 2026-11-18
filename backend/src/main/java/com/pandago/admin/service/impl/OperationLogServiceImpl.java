package com.pandago.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.pandago.admin.entity.OperationLog;
import com.pandago.admin.mapper.OperationLogMapper;
import com.pandago.admin.service.OperationLogService;
import com.pandago.common.api.PageResult;
import com.pandago.common.enums.RoleCode;
import com.pandago.security.LoginUserPrincipal;
import com.pandago.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/** 操作日志实现：操作人身份取自 JWT 验签后的 SecurityContext。 */
@Service
@RequiredArgsConstructor
public class OperationLogServiceImpl implements OperationLogService {

    private final OperationLogMapper operationLogMapper;

    @Override
    public void record(String module, String operation, String targetType, Long targetId, String detail) {
        LoginUserPrincipal principal = SecurityUtils.requireCurrentUser();
        OperationLog log = new OperationLog();
        log.setOperatorId(principal.getUserId());
        log.setOperatorName(principal.getUsername());
        log.setModule(module);
        log.setOperation(operation);
        log.setTargetType(targetType);
        log.setTargetId(targetId);
        log.setDetail(detail);
        operationLogMapper.insert(log);
    }

    @Override
    public void save(OperationLog log) {
        operationLogMapper.insert(log);
    }

    @Override
    public PageResult<OperationLog> page(String module, long pageNum, long pageSize) {
        SecurityUtils.requireRole(RoleCode.ADMIN);
        IPage<OperationLog> page = operationLogMapper.selectPage(
                new Page<>(pageNum, pageSize),
                new LambdaQueryWrapper<OperationLog>()
                        .eq(StringUtils.hasText(module), OperationLog::getModule, module)
                        .orderByDesc(OperationLog::getCreatedAt));
        return PageResult.of(page.getRecords(), page.getTotal(), pageNum, pageSize, page.getPages());
    }
}
