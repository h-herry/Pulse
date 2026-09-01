import type { NextConfig } from "next";

/**
 * Pulse 脉搏 · 前端（Next.js）
 *
 * 部署形态：静态导出（output: 'export'）→ 产物放入 Spring Boot 的
 * src/main/resources/static，由 java -jar 单一 Fat JAR 托管，保留
 * 「一条命令启动」的产品核心价值。
 *
 * 开发态：next dev 通过 rewrites 把 /api/v1、/ws、/uploads 代理到后端 8080。
 * （静态导出时会忽略 rewrites，故仅在 development 返回，避免导出构建报错。）
 */
const BACKEND_ORIGIN =
  process.env.NEXT_PUBLIC_BACKEND_ORIGIN ?? "http://localhost:8080";

const nextConfig: NextConfig = {
  // 全站静态导出：所有路由必须是静态段，团队/项目上下文由客户端 store 持有（不进 URL）
  output: "export",
  // 产出 out/<route>/index.html，便于 Spring Boot 静态资源解析目录默认页
  trailingSlash: true,
  // 导出模式不支持 Next 图片优化
  images: { unoptimized: true },
  reactStrictMode: true,

  // 仅开发态注入代理：生产是静态导出 + JAR 同源部署，不存在跨域
  ...(process.env.NODE_ENV === "development"
    ? {
        async rewrites() {
          return [
            { source: "/api/v1/:path*", destination: `${BACKEND_ORIGIN}/api/v1/:path*` },
            { source: "/ws/:path*", destination: `${BACKEND_ORIGIN}/ws/:path*` },
            { source: "/uploads/:path*", destination: `${BACKEND_ORIGIN}/uploads/:path*` },
          ];
        },
      }
    : {}),
};

export default nextConfig;
