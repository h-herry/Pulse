# Pulse 脉搏

> 面向团队的协作式项目管理平台：看板 · 任务 · 迭代 · 报表 · 实时协作 · AI 增强。
> 单仓库（Spring Boot 后端 + Next.js 前端），可自托管，Docker Compose 一键上线。

---

## ✨ 特性

- **项目与任务**：看板拖拽、子任务、依赖、标签、附件、评论、活动日志、自定义字段、保存视图。
- **迭代与报表**：迭代规划、项目进度、成员效率、燃尽图。
- **团队与权限**：三种角色——**用户 / 组长 / 管理员**；团队级多租户隔离 + 系统级全局视图。
- **实时协作**：看板在线成员、编辑指示、任务移动/更新即时广播；站内通知实时推送。
- **AI 增强**：用户自带 LLM Key（加密存储，数据主权归用户）。
- **安全基线**：无默认密钥（缺失即启动失败）、初始管理员随机口令、全站 HTTPS、JWT 无状态认证、WebSocket 鉴权与隔离。

---

## 🏗 技术栈

| 层 | 技术 |
|---|---|
| 后端 | Java 17 · Spring Boot 3 · Spring Security 6 · MyBatis-Plus 3 · MySQL 8 · Spring WebSocket(STOMP) · JWT |
| 前端 | Next.js 16（静态导出）· React 19 · TypeScript · Tailwind CSS v4 · TanStack Query · Zustand · Radix UI · dnd-kit · STOMP/SockJS |
| 部署 | 单 Fat JAR（内嵌前端）· Docker 多阶段构建 · docker-compose（db + app + nginx TLS） |

---

## 📁 项目结构

```
pulse/
├── src/                    后端源码（com.pulse：controller/service/mapper/entity/security/realtime/scheduler/common/config）
├── frontend/              Next.js 前端（静态导出至 frontend/out）
├── docs/                  设计文档.md · 技术文档.md
├── nginx/                 TLS 反向代理配置与自签名证书入口
├── scripts/               build.sh（一体化构建）· gen-key.sh（生成加密主密钥）
├── docker-compose.yml     db + app + proxy 编排
├── Dockerfile             多阶段生产镜像
└── pom.xml                后端构建
```

---

## 🚀 快速开始

### 方式一：Docker Compose（生产推荐）

```bash
# 1. 准备环境变量
cp .env.example .env
./scripts/gen-key.sh                 # 生成 PULSE_ENCRYPTION_KEY 并写入 .env
# 再为 JWT_SECRET 生成密钥并写入 .env：
openssl rand -hex 32

# 2. 准备 TLS 证书（生产）
mkdir -p certs && cp your-fullchain.pem certs/fullchain.pem && cp your-privkey.pem certs/privkey.pem
# （缺失时 nginx 会生成自签名证书，仅适用于内网/测试）

# 3. 启动
docker compose up -d --build
```

- 访问：`https://<你的域名>`（80 自动跳转 HTTPS）。
- 首次启动日志会打印初始管理员账号 `admin@pulse.dev` 与一次性随机口令，**请立即登录并改密**。

### 方式二：本地开发

```bash
# 后端
./mvnw spring-boot:run

# 前端（独立 dev server，默认 http://localhost:5173）
cd frontend && npm ci && npm run dev
```

### 一体化构建（产出单 JAR）

```bash
./scripts/build.sh                 # 前端构建 -> 同步 static；后端 mvn package
# 产物：target/pulse-1.0.0.jar（内含前端静态资源）
SKIP_FRONTEND=1 ./scripts/build.sh # 仅打包后端
```

---

## 🔑 环境变量（关键项）

| 变量 | 说明 | 必填 |
|---|---|---|
| `MYSQL_ROOT_PASSWORD` | 数据库 root 口令 | ✅ |
| `JWT_SECRET` | JWT 签名密钥（`openssl rand -hex 32`） | ✅ |
| `PULSE_ENCRYPTION_KEY` | LLM Key 加密主密钥（`./scripts/gen-key.sh`） | ✅ |
| `CORS_ALLOWED_ORIGINS` | 前端域名（逗号分隔） | 可选 |
| `AI_BASE_URL` / `AI_MODEL` | 默认 LLM 服务（openai 兼容） | 可选 |
| `SWAGGER_ENABLED` | 开启 API 文档（默认 `false`） | 可选 |

> 缺失任一必填密钥，应用将 **fail-fast 拒绝启动**，避免带病上线。

---

## 👥 角色模型

| 角色 | 作用域 | 能力 |
|---|---|---|
| **用户 (USER)** | 团队内 | 执行任务、查看项目/迭代/成员/标签 |
| **组长 (LEAD)** | 团队内 | 成员/项目/迭代/任务/标签管理（不跨团队） |
| **管理员** | 全局 | 跨团队查询、用户管理、审计日志（上帝视角） |

团队内角色在「团队设置」中管理；全局管理员在「管理后台」中设置。

---

## 📚 文档

- [设计文档](docs/设计文档.md) — 产品定位、领域模型、权限模型、安全与实时设计。
- [技术文档](docs/技术文档.md) — 技术栈、代码结构、API 约定、认证授权、构建与部署。

---

## ⚠️ 部署提示

- 上传目录已挂载 `pulse-uploads` 卷，容器重建不丢文件。
- 数据库已挂载 `pulse-db` 卷；`schema.sql` 幂等，可重复启动。
- 生产务必提供真实 TLS 证书，并修改初始管理员口令。

---

## 📄 License

内部项目，详见团队内部许可约定。
