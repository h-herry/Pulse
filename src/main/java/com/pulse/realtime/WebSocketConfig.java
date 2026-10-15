package com.pulse.realtime;

import com.pulse.realtime.RealtimeChannelInterceptor;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.*;
import org.springframework.messaging.simp.config.ChannelRegistration;

/**
 * WebSocket / STOMP 消息代理配置。
 *
 * <p>启用 {@code @EnableWebSocketMessageBroker}，注册 SockJS 端点 {@code /ws}，
 * 设置应用目的前缀 {@code /app}（客户端发往服务端的消息）与简单 broker 前缀 {@code /topic}（服务端广播）。
 * 并将 {@link RealtimeChannelInterceptor} 挂到入站信道，统一做认证与团队隔离。
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final RealtimeChannelInterceptor interceptor;

    @Value("${cors.allowed-origins:http://localhost:5173,http://127.0.0.1:5173}")
    private String[] allowedOrigins;

    public WebSocketConfig(RealtimeChannelInterceptor interceptor) {
        this.interceptor = interceptor;
    }

    /**
     * 注册 STOMP 端点：暴露 {@code /ws} 并开启 SockJS 降级，
     * 仅允许 {@code cors.allowed-origins} 配置的前端源建立实时连接。
     *
     * @param registry STOMP 端点注册器
     */
    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // 允许配置的跨域源；同时放行同源（自托管：localhost / 127.0.0.1 任意端口），
        // 否则前端与后端同域部署时 WebSocket 握手会被 allowedOrigins 拒绝。
        String[] wsAllowed = Arrays.copyOf(allowedOrigins, allowedOrigins.length + 2);
        wsAllowed[allowedOrigins.length] = "http://localhost:*";
        wsAllowed[allowedOrigins.length + 1] = "http://127.0.0.1:*";
        registry.addEndpoint("/ws").setAllowedOriginPatterns(wsAllowed).withSockJS();
    }

    /**
     * 配置消息代理：客户端发往 {@code /app} 前缀的消息映射到 {@code @MessageMapping} 处理器，
     * 服务端经 {@code /topic} 前缀向订阅者广播消息。
     *
     * @param registry 消息代理注册器
     */
    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.setApplicationDestinationPrefixes("/app");
        registry.enableSimpleBroker("/topic");
    }

    /**
     * 在客户端入站信道挂接 {@link RealtimeChannelInterceptor}，
     * 于 CONNECT / SUBSCRIBE 阶段执行 JWT 认证与团队隔离校验。
     *
     * @param registration 入站信道注册器
     */
    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(interceptor);
    }
}
