#!/usr/bin/env bash
#
# Pulse 生产构建脚本（本地 / CI 通用）
#
# 设计：前端与后端解耦构建（解决 R2 构建链路脆弱性）
#   1. 前端：npm ci + npm run build -> 输出到 src/main/resources/static
#   2. 后端：mvn package（不再在生命周期内触发 npm）-> 可执行 jar 内含 static
#
# 用法：
#   ./scripts/build.sh            # 完整构建（含前端）
#   SKIP_FRONTEND=1 ./scripts/build.sh   # 仅打包后端（前端已先行构建过）
#
set -euo pipefail

cd "$(dirname "$0")/.."   # 切到 pulse 根目录

if [ "${SKIP_FRONTEND:-}" != "1" ]; then
  echo "==> [1/2] 构建前端 (npm ci && npm run build) -> src/main/resources/static"
  ( cd frontend && npm ci && npm run build )

  # Next.js 静态导出产物在 frontend/out，同步进 Spring Boot 的静态资源目录
  # （保留 java -jar 单一 Fat JAR 部署形态，无需 Node 服务器）
  STATIC_DIR="src/main/resources/static"
  echo "==> 同步 frontend/out -> ${STATIC_DIR}"
  rm -rf "${STATIC_DIR:?}/"*
  cp -R frontend/out/. "${STATIC_DIR}/"
  echo "==> 已同步：" && ls "${STATIC_DIR}"
else
  echo "==> 跳过前端构建 (SKIP_FRONTEND=1)"
fi

echo "==> [2/2] 打包后端 (mvn package，含测试)"
./mvnw -q package || mvn -q package

echo "==> 完成：target/pulse-1.0.0.jar"
