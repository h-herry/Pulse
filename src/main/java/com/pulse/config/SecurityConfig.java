package com.pulse.config;

import com.pulse.security.JwtAuthFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.util.matcher.RegexRequestMatcher;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

/**
 * Spring Security 主配置。
 *
 * <p>核心策略：
 * <ul>
 *   <li>无状态（STATELESS）：不创建 HttpSession，不启用 CSRF（令牌即凭证）；</li>
 *   <li>{@link JwtAuthFilter} 在 {@code UsernamePasswordAuthenticationFilter} 之前插入，
 *       完成 JWT 校验与上下文装配；</li>
 *   <li>按路由放行：静态资源、认证端点、WebSocket 升级、上传目录、管理后台等；</li>
 *   <li>注入 HSTS / CSP 等安全响应头；CORS 来源由 {@code cors.allowed-origins} 驱动；</li>
 *   <li>Swagger/OpenAPI 仅在 {@code SWAGGER_ENABLED=true} 时放行，生产默认关闭。</li>
 * </ul>
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;

    @Value("${cors.allowed-origins}")
    private String allowedOrigins;

    @Value("${SWAGGER_ENABLED:false}")
    private boolean swaggerEnabled;

    public SecurityConfig(JwtAuthFilter jwtAuthFilter) {
        this.jwtAuthFilter = jwtAuthFilter;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .csrf(csrf -> csrf.disable())
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> {
                // Swagger/OpenAPI 仅当显式开启（SWAGGER_ENABLED=true）时放行，生产默认关闭
                if (swaggerEnabled) {
                    auth.requestMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll();
                }
                // 健康检查端点对探针（k8s/LB/docker-compose healthcheck）放行，无鉴权；
                // 配合 management.endpoint.health.show-details=never，不泄露敏感信息
                auth.requestMatchers("/actuator/**").permitAll()
                .requestMatchers("/api/v1/auth/**").permitAll()
                .requestMatchers("/ws/**").permitAll()
                .requestMatchers("/uploads/**").permitAll()
                // /icon.svg 为 Next.js App Router 的站点图标（源文件 app/icon.svg）
                .requestMatchers("/", "/index.html", "/static/**", "/assets/**",
                        "/favicon.ico", "/favicon.svg", "/icon.svg").permitAll()
                // Next.js 静态导出产物：JS/CSS 在 /_next/**，路由级 RSC 载荷为 .txt。
                // 前端由 Vue（产物在 /assets/**）换为 Next 后必须放行，否则整站白屏（403）。
                // 注意：Spring PathPattern 不允许 ** 之后再有内容（/**/*.txt 会抛
                // PatternParseException），故 .txt 用正则匹配器放行。
                // 关键：浏览器拉取 RSC 飞行数据必带 ?_rsc=<hash> 查询参数，正则须容忍
                // 查询串，否则带参请求不匹配 → 被 anyRequest().authenticated() 拦成 403，
                // 导致客户端软导航（点链接切换页面）失败、前端报「网络失败」。
                .requestMatchers("/_next/**").permitAll()
                .requestMatchers(RegexRequestMatcher.regexMatcher(".*\\.txt.*")).permitAll()
                // SPA 深链的转发目标：SpaController 会把 /board 转给 /board/index.html。
                // Spring Security 6 默认同时过滤 FORWARD 派发，若不放行转发目标，
                // 深链会被 anyRequest().authenticated() 拦成 403。
                // 注：/**/index.html 非法（** 后不能再有内容），故用单段通配 /*/index.html。
                .requestMatchers("/*/index.html").permitAll()
                // 前端 SPA 路由（深链直访不再 403）；实际渲染由 SpaController 转发到 index.html
                // 重构后路由均为静态段，团队/项目上下文不再进入 URL
                .requestMatchers(
                        "/login", "/register", "/forgot-password",
                        "/overview", "/my-tasks", "/board", "/iterations", "/reports",
                        "/timeline", "/team", "/members", "/admin", "/settings", "/onboarding",
                        // 兼容尾斜杠直链（Next 静态导出产出 /xxx/index.html）
                        "/login/", "/register/", "/forgot-password/",
                        "/overview/", "/my-tasks/", "/board/", "/iterations/", "/reports/",
                        "/timeline/", "/team/", "/members/", "/admin/", "/settings/", "/onboarding/"
                ).permitAll()
                .anyRequest().authenticated();
            })
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
            .headers(headers -> headers
                .httpStrictTransportSecurity(hsts -> hsts
                    .includeSubDomains(true)
                    .maxAgeInSeconds(31536000)
                    .preload(true))
                .contentSecurityPolicy(csp -> csp.policyDirectives(
                    "default-src 'self'; " +
                    // 关键：Next.js 静态导出会在每个页面内联数个 <script>（RSC 飞行数据
                    // self.__next_f.push(...)），且不带 nonce/hash。此处若只写 'self'，
                    // 浏览器会拦掉这些内联脚本 → React 无法水合 → 页面永久停在 loading
                    // （表现为一直转圈、无法使用）。故必须放行 'unsafe-inline'。
                    "script-src 'self' 'unsafe-inline'; " +
                    "style-src 'self' 'unsafe-inline' https://fonts.googleapis.com; " +
                    // 移除开发残留的 127.0.0.1；仅允许同源与 data: 图片
                    "img-src 'self' data:; " +
                    "font-src 'self' data: https://fonts.googleapis.com https://fonts.gstatic.com; " +
                    // 同源 API + WebSocket：wss: 供 HTTPS 生产，ws: 供纯 HTTP 本地访问。
                    // （HTTPS 页面下浏览器的混合内容策略本身就会阻断 ws://，故不削弱生产安全。）
                    "connect-src 'self' ws: wss:; " +
                    "object-src 'none'; " +
                    "frame-ancestors 'none'; " +
                    "base-uri 'self'"))
            );

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        List<String> origins = Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(o -> !o.isBlank())
                .toList();
        config.setAllowedOrigins(origins);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);
        config.setExposedHeaders(List.of("Authorization", "X-Team-ID"));

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
