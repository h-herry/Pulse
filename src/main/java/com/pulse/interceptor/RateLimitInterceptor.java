package com.pulse.interceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 认证接口固定窗口限流，缓解暴力破解 / 撞库。
 *
 * <p>单实例内存计数，按「客户端 IP + 接口路径」维度，窗口 60s 内最多 10 次。
 * 多实例部署应换为 Redis 共享计数（bucket4j + Redis），当前形态已覆盖单 JAR 部署场景。
 *
 * <p>可通过 {@code app.rate-limit.enabled} 关闭（默认开启）；测试 profile 置 false，
 * 避免回环 IP 在共享上下文内累计请求触发限流、干扰集成测试。
 */
@Component
public class RateLimitInterceptor implements HandlerInterceptor {

    private static final int MAX_REQUESTS = 10;
    private static final long WINDOW_MS = 60_000;

    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();
    private final boolean enabled;

    public RateLimitInterceptor(@Value("${app.rate-limit.enabled:true}") boolean enabled) {
        this.enabled = enabled;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws IOException {
        if (!enabled) {
            return true;
        }
        String key = clientIp(request) + "::" + request.getRequestURI();
        Bucket bucket = buckets.computeIfAbsent(key, k -> new Bucket());

        synchronized (bucket) {
            long now = System.currentTimeMillis();
            if (now - bucket.windowStart > WINDOW_MS) {
                bucket.count = 0;
                bucket.windowStart = now;
            }
            if (bucket.count >= MAX_REQUESTS) {
                response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
                response.setContentType("application/json;charset=UTF-8");
                response.getWriter().write(
                        "{\"code\":429,\"message\":\"请求过于频繁，请稍后再试\",\"data\":null}");
                return false;
            }
            bucket.count++;
        }
        return true;
    }

    /**
     * 取限流维度的客户端标识。
     *
     * <p>部署形态为 nginx（{@code $proxy_add_x_forwarded_for}）+ {@code server.forward-headers-strategy=native}：
     * Tomcat 的 RemoteIpValve 已沿可信代理链从右向左解析出真实客户端 IP 并写入 {@code remoteAddr}，
     * 同时把可信代理段从 X-Forwarded-For 中剥离——此时残留在该头里的最左侧值是客户端自带的、可任意伪造的内容。
     * 因此优先采用 {@code remoteAddr}；仅当其缺失时，才退化为取 XFF 链中**最后一个**条目
     * （最接近可信代理的一跳，伪造代价最高），避免攻击者靠伪造 XFF 首项无限刷新限流桶绕过限流。
     */
    private String clientIp(HttpServletRequest request) {
        String remoteAddr = request.getRemoteAddr();
        if (remoteAddr != null && !remoteAddr.isBlank()) {
            return remoteAddr;
        }
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            String[] hops = xff.split(",");
            return hops[hops.length - 1].trim();
        }
        return "unknown";
    }

    private static final class Bucket {
        int count = 0;
        long windowStart = System.currentTimeMillis();
    }
}
