#!/usr/bin/env bash
#
# 生成 Pulse 加密主密钥（AES-256-GCM 所需 32 字节）
#
# 用法：
#   ./scripts/gen-key.sh
#
# 输出 64 位十六进制（32 字节），将其作为 PULSE_ENCRYPTION_KEY 注入部署环境。
# 缺失 / 非法会导致服务启动失败（fail-fast，避免带病上线）。
#
set -euo pipefail

if command -v openssl >/dev/null 2>&1; then
  KEY=$(openssl rand -hex 32)
else
  # 回退：使用 /dev/urandom（类 Unix 系统）
  KEY=$(head -c 32 /dev/urandom | xxd -p | tr -d '\n')
fi

echo "PULSE_ENCRYPTION_KEY=$KEY"
echo
echo "将其写入 .env（勿提交到仓库）："
echo "  echo \"PULSE_ENCRYPTION_KEY=$KEY\" >> .env"
