#!/bin/sh
set -e

# 仅首次初始化数据库（/docker-entrypoint-initdb.d 由 mysql 官方镜像在空数据目录时执行）时运行。
# 创建最小权限应用账号 pulse_app；root 仅用于初始化，不交付给应用使用。
mysql --user=root --password="$MYSQL_ROOT_PASSWORD" <<SQL
CREATE USER IF NOT EXISTS 'pulse_app'@'%' IDENTIFIED BY '$PULSE_DB_PASSWORD';
GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, TEMPORARY TABLES ON pulse.* TO 'pulse_app'@'%';
FLUSH PRIVILEGES;
SQL
