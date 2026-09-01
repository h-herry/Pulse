package com.pulse.config;

import com.pulse.interceptor.RateLimitInterceptor;
import com.pulse.interceptor.TenantInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Paths;

/**
 * Web MVC 配置。
 *
 * <p>注册两个拦截器（限流拦截器优先于多租户拦截器，尽早拒绝过量请求），
 * 并将 {@code /uploads/**} 映射到文件存储目录（{@code app.upload.dir}），使上传文件可经 HTTP 访问。
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final TenantInterceptor tenantInterceptor;
    private final RateLimitInterceptor rateLimitInterceptor;
    private final String uploadDir;

    public WebMvcConfig(TenantInterceptor tenantInterceptor,
                        RateLimitInterceptor rateLimitInterceptor,
                        @Value("${app.upload.dir:uploads}") String uploadDir) {
        this.tenantInterceptor = tenantInterceptor;
        this.rateLimitInterceptor = rateLimitInterceptor;
        this.uploadDir = uploadDir;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 限流优先于业务拦截器，尽早拒绝过量请求
        registry.addInterceptor(rateLimitInterceptor)
                .addPathPatterns(
                        "/api/v1/auth/login",
                        "/api/v1/auth/register",
                        "/api/v1/auth/forgot-password",
                        // 刷新令牌与改密同属凭据类端点：前者可被用于枚举/滥用长期令牌，
                        // 后者可被用于旧密码爆破，均需与登录同等限流
                        "/api/v1/auth/refresh",
                        "/api/v1/auth/change-password",
                        "/api/v1/auth/reset-password");
        registry.addInterceptor(tenantInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns("/swagger-ui/**", "/v3/api-docs/**");
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // 上传文件落到与存储目录一致的位置，避免与 app.upload.dir 配置不一致
        String location = Paths.get(uploadDir).toAbsolutePath().toString() + "/";
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations("file:" + location);
    }
}
