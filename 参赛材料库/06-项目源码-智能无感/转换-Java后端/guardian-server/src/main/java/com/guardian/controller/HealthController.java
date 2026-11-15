package com.guardian.controller;

import com.guardian.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 健康检查
 *
 * 用于启动脚本轮询、运维探活。
 * 会真实探测 MySQL 和 Redis，而不是只返回 "UP"。
 */
@Slf4j
@RestController
@RequestMapping("/health")
@RequiredArgsConstructor
public class HealthController {

    private final JdbcTemplate jdbc;
    private final StringRedisTemplate redis;

    @Value("${guardian.ai.ollama.enabled:false}")
    private boolean ollamaEnabled;
    @Value("${guardian.ai.coze.enabled:false}")
    private boolean cozeEnabled;

    /** 简单探活（不查依赖，供负载均衡用） */
    @GetMapping("/ping")
    public ApiResponse<String> ping() {
        return ApiResponse.ok("pong");
    }

    /** 完整健康检查 */
    @GetMapping
    public ApiResponse<Map<String, Object>> health() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("service", "guardian-server");
        data.put("time", LocalDateTime.now().toString());

        // ---------- MySQL ----------
        Map<String, Object> mysql = new LinkedHashMap<>();
        try {
            Integer tables = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE()",
                    Integer.class);
            mysql.put("connected", true);
            mysql.put("tables", tables);

            // 抽查核心表能否查询
            Integer users = jdbc.queryForObject("SELECT COUNT(*) FROM users", Integer.class);
            mysql.put("users", users);
        } catch (Exception e) {
            mysql.put("connected", false);
            mysql.put("error", e.getMessage());
            log.warn("MySQL 健康检查失败: {}", e.getMessage());
        }
        data.put("mysql", mysql);

        // ---------- Redis ----------
        Map<String, Object> redisInfo = new LinkedHashMap<>();
        try {
            String pong = redis.getConnectionFactory().getConnection().ping();
            redisInfo.put("connected", "PONG".equalsIgnoreCase(pong));
        } catch (Exception e) {
            redisInfo.put("connected", false);
            redisInfo.put("error", e.getMessage());
        }
        data.put("redis", redisInfo);

        // ---------- AI ----------
        Map<String, Object> ai = new LinkedHashMap<>();
        if (ollamaEnabled) {
            ai.put("provider", "ollama");
        } else if (cozeEnabled) {
            ai.put("provider", "coze");
        } else {
            ai.put("provider", "rule");
        }
        ai.put("fallback", "rule");   // 无论主用哪个，兜底永远是规则库
        data.put("ai", ai);

        // ---------- 总体状态 ----------
        boolean mysqlOk = Boolean.TRUE.equals(mysql.get("connected"));
        boolean redisOk = Boolean.TRUE.equals(redisInfo.get("connected"));
        data.put("status", (mysqlOk && redisOk) ? "UP" : "DEGRADED");

        return ApiResponse.ok(data);
    }
}
