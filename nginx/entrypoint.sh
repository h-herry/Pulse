#!/bin/sh
set -e
# 生产必须提供真实证书；缺失则直接失败退出，避免静默裸奔（不再自动生成自签名证书）。
if [ ! -f /etc/nginx/certs/fullchain.pem ] || [ ! -f /etc/nginx/certs/privkey.pem ]; then
  echo "ERROR: 未提供 TLS 证书（/etc/nginx/certs/fullchain.pem 与 privkey.pem 缺失）。请挂载真实证书后重试。" >&2
  exit 1
fi
exec nginx -g 'daemon off;'
