package com.pandago.common.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 健康检查接口。
 *
 * <p>用于阶段 1 验证后端已正常启动、统一响应结构生效。
 * 路径遵循 TECH_DESIGN 约定的 /api/v1 基础路径。</p>
 */
@RestController
@RequestMapping("/api/v1")
public class HealthController {

    @GetMapping("/health")
    public Result<Map<String, Object>> health() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("application", "PandaGo");
        data.put("status", "UP");
        data.put("phase", "1 - 项目初始化与基础框架");
        return Result.ok(data);
    }
}
