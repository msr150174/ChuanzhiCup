package com.guardian.controller;

import com.guardian.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 根路径接口
 *
 * 原 NestJS 项目在根路径有 /api/hello，这里保留以兼容前端探活。
 *
 * ★ 注意：不能放在 HealthController（它挂在 /health 下），
 *   否则路径会变成 /api/health/hello。
 */
@RestController
@RequiredArgsConstructor
public class RootController {

    /** 根路径提示 */
    @GetMapping("/hello")
    public ApiResponse<Map<String, Object>> hello() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("service", "guardian-server");
        data.put("message", "AI 节律守护后端已就绪");
        data.put("version", "1.0.0");
        data.put("time", LocalDateTime.now().toString());
        data.put("docs", "/api/health");
        return ApiResponse.ok(data);
    }

    /** 根路径（不带子路径） */
    @GetMapping("")
    public ApiResponse<Map<String, Object>> root() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("service", "guardian-server");
        data.put("message", "AI 节律守护后端");
        data.put("endpoints", new String[]{
                "/api/health", "/api/hello", "/api/auth/login", "/api/checkin/today",
                "/api/guardian/dashboard", "/api/ai/chat", "/api/statistics/overview"
        });
        return ApiResponse.ok(data);
    }
}
