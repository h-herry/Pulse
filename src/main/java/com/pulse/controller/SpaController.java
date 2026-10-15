package com.pulse.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * SPA 深链支持。
 *
 * <p>前端使用 history 路由模式，直接访问 /login、/board 等客户端路由时，
 * 浏览器会向后端请求该路径。Spring Security 已将这些路由设为 permitAll，
 * 本控制器再把请求转发到该路由对应的 index.html，避免直链 404/白屏。
 *
 * <p><b>关键：必须转发到「路由自己的」 index.html，而不是根 /index.html。</b>
 * Next.js 静态导出会为每个路由生成独立 HTML（out/board/index.html 等），
 * 内含该页的 RSC 载荷；若统一回落到根 index.html，深链首屏会渲染成
 * 根页面内容（曾导致所有路由都显示首页模板）。
 *
 * <p><b>前端重构（Vue → Next.js 静态导出）后的变化：</b>
 * 团队/项目上下文不再编码进 URL（原 /team/{teamId}/project/{pid}/board），
 * 所有路由均为静态段，路由清单见下方注解。
 *
 * <p>这里刻意<b>不使用</b> /** 通配：通配控制器映射优先级高于静态资源处理器，
 * 会吞掉 /_next/static/** 等构建产物导致白屏。枚举法可预测且安全。
 *
 * <p>/api、/ws、/uploads、/static、/assets 由安全过滤器与静态资源处理器处理。
 */
@Controller
public class SpaController {

    /**
     * 客户端路由（与 frontend/app 下的页面一一对应）。
     *
     * <p>注意：注解元素必须是编译期常量，而 Java 中 <code>static final String[]</code>
     * 并非常量表达式（会报「String[] 无法转换为 String」），故此处直接内联。
     */
    @GetMapping({
            // 认证
            "/login", "/register", "/forgot-password",
            // 应用主视图（单层导航，团队/项目由顶部切换器驱动，不再编码进 URL）
            "/overview", "/my-tasks", "/board", "/iterations", "/reports",
            "/timeline", "/team", "/members", "/admin", "/settings", "/onboarding",
            // 尾斜杠形式：next.config 启用了 trailingSlash:true，站内链接形如 /board/。
            // 而 Spring Framework 6 默认关闭尾斜杠匹配（useTrailingSlashMatch=false），
            // /board/ 不会命中上面的 /board，会落到静态资源处理器并抛
            // NoResourceFoundException（表现为刷新/直链该路由时 500）。
            // 故必须显式登记尾斜杠形式；forwardToIndex 会规整掉尾斜杠再转发。
            "/login/", "/register/", "/forgot-password/",
            "/overview/", "/my-tasks/", "/board/", "/iterations/", "/reports/",
            "/timeline/", "/team/", "/members/", "/admin/", "/settings/", "/onboarding/"
    })
    public String forwardToIndex(HttpServletRequest request) {
        // 去掉 contextPath，并规整尾斜杠，得到形如 /board 的路径
        String path = request.getRequestURI();
        String contextPath = request.getContextPath();
        if (contextPath != null && !contextPath.isEmpty() && path.startsWith(contextPath)) {
            path = path.substring(contextPath.length());
        }
        while (path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }
        return "forward:" + path + "/index.html";
    }

    /**
     * 兜底转发：覆盖未被上方枚举的合法路由（未来新增页面、深链、动态段），
     * 转发到该路径自己的 index.html，避免落到 Spring Security 返回 403 白屏。
     *
     * <p>正则排除：含文件后缀（.js/.css/.ico…）的路径，以及 api/ws/uploads/static/
     * assets/_next/actuator/v3/swagger 等前缀，确保静态资源与接口不受影响。
     * 已枚举的路由由更具体的映射优先匹配，不会与本方法冲突。
     */
    @GetMapping("/{path:^(?!.*\\.)(?!api|ws|uploads|assets|static|_next|actuator|v3|swagger).*$}")
    public String forwardCatchAll() {
        // 未知/未来路由回落到根 index.html（SPA 外壳），由前端客户端路由决定渲染或 404，
        // 避免转发到不存在的 <path>/index.html 导致 500。已枚举路由由更具体的映射优先匹配。
        return "forward:/index.html";
    }
}
