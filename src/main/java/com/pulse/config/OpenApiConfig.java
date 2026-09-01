package com.pulse.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI（Swagger）文档配置。
 * 对外暴露 Pulse API 的接口元信息，并声明「Bearer」HTTP 安全方案，
 * 便于前端在 Swagger UI 中填写 JWT 后调试受保护接口。
 */
@Configuration
public class OpenApiConfig {

    /**
     * 构建 OpenAPI 文档对象：标题、版本、API 描述，并登记 Bearer(JWT) 安全方案。
     *
     * @return OpenAPI 文档定义
     */
    @Bean
    public OpenAPI pulseOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Pulse API")
                        .version("1.0.0")
                        .description("Pulse 轻量化任务管理平台 API"))
                .addSecurityItem(new SecurityRequirement().addList("Bearer"))
                .components(new io.swagger.v3.oas.models.Components()
                        .addSecuritySchemes("Bearer", new SecurityScheme()
                                .name("Bearer")
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")));
    }
}
