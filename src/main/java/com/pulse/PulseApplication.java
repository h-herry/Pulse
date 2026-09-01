package com.pulse;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Pulse 脉搏 后端应用启动类。
 * 职责：基于 Spring Boot 启动 Web 服务，启用 MyBatis Mapper 扫描（com.pulse.mapper）与定时任务调度（@EnableScheduling）。
 * 安全/租户说明：本类仅负责装配容器；JWT 无状态认证、X-Team-ID 多租户隔离与授权判定由 JwtAuthFilter、TenantInterceptor 在请求链路中完成。
 */
@SpringBootApplication
@MapperScan("com.pulse.mapper")
@EnableScheduling
public class PulseApplication {
    public static void main(String[] args) {
        SpringApplication.run(PulseApplication.class, args);
    }
}
