package com.guardian;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * AI 节律守护 · 后端服务
 *
 * 技术栈：Spring Boot 3.2.5 + MyBatis + MySQL 8 + Redis
 * 双 AI：Ollama（本地）→ Coze（云端）→ 规则库（兜底）
 */
@SpringBootApplication
@MapperScan("com.guardian.mapper")
@EnableAsync
@EnableScheduling
public class GuardianApplication {

    public static void main(String[] args) {
        SpringApplication.run(GuardianApplication.class, args);
        System.out.println("""

                ============================================================
                  AI 节律守护 · 后端服务已启动
                  ------------------------------------------------------------
                  接口地址: http://localhost:3000/api
                  健康检查: http://localhost:3000/api/health
                ============================================================
                """);
    }
}
