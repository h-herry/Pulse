# Pulse 生产镜像
# 构建链路（解决 R2）：
#   1. node 阶段独立执行 npm ci && npm run build（Next.js 静态导出 -> frontend/out）
#   2. 将 frontend/out 同步到 src/main/resources/static，保持 java -jar 单 JAR 部署
#   3. 后端 mvn package 仅打包，不在生命周期内触发 npm
FROM node:20-bookworm

# 安装 JDK17 + Maven
RUN apt-get update && apt-get install -y --no-install-recommends \
    openjdk-17-jdk \
    maven \
    curl \
    && rm -rf /var/lib/apt/lists/*

ENV JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64

WORKDIR /app

# 复制全部源码（.dockerignore 已排除 node_modules / target）
COPY . .

# 构建前端（Next.js 静态导出到 frontend/out），再同步进 Spring Boot 静态资源目录
RUN cd frontend && npm ci && npm run build
RUN rm -rf src/main/resources/static/* && cp -R frontend/out/. src/main/resources/static/

# 打包后端（static 已就位，maven 不再触发 npm；含测试）
RUN mvn -q package

# 创建非 root 用户 pulse（uid 1000）并赋予 /app 所有权，降低容器逃逸影响面；
# /app/uploads 同步建好并归属 pulse，使挂载的上传卷在首次 copy-up 后对非 root 可写。
RUN useradd -m -u 1000 pulse \
    && chown -R pulse:pulse /app \
    && mkdir -p /app/uploads \
    && chown pulse:pulse /app/uploads

EXPOSE 8080
USER pulse
ENTRYPOINT ["java", "-jar", "target/pulse-1.0.0.jar"]
