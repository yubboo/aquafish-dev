# syntax=docker/dockerfile:1.7

# ============================================================
# Aquafish 管理端构建阶段
#
# 管理端静态资源与目标服务器 CPU 架构无关，
# 因此固定在 GitHub Actions 构建机架构执行一次。
# ============================================================
FROM --platform=$BUILDPLATFORM node:22-alpine AS admin-builder

WORKDIR /workspace/admin

# 启用 Node.js 自带的 Corepack，用于管理 pnpm。
RUN corepack enable

# 先复制依赖描述文件，充分利用 Docker 构建缓存。
COPY admin/package.json \
     admin/pnpm-lock.yaml \
     admin/pnpm-workspace.yaml \
     ./

# 严格按照锁文件安装依赖。
RUN pnpm install --frozen-lockfile

# 复制管理端源码并执行测试、正式构建。
COPY admin/ ./

RUN pnpm test \
    && pnpm build


# ============================================================
# Aquafish Java 后端构建阶段
#
# 将管理端 dist 嵌入 Spring Boot 单 JAR。
# Java 字节码与目标服务器 CPU 架构无关，
# 无需分别在 amd64、arm64 模拟环境重复编译。
# ============================================================
FROM --platform=$BUILDPLATFORM eclipse-temurin:21-jdk AS app-builder

WORKDIR /workspace

# 复制后端 Gradle 工程。
COPY app/ ./app/

# 正式构建任务会校验 Dockerfile。
COPY Dockerfile ./Dockerfile

# 复制全部 1Panel 应用版本。
#
# 不再硬编码：
# packaging/1panel/aquafish/0.0.3
#
# 后续增加 0.0.4、0.0.5 时，
# Dockerfile 不需要进行任何修改。
COPY packaging/1panel/aquafish/ ./packaging/1panel/aquafish/

# 嵌入管理端正式构建产物。
COPY --from=admin-builder /workspace/admin/dist ./admin/dist/

# 执行测试并生成 Aquafish 单 JAR。
RUN chmod +x ./app/gradlew \
    && cd app \
    && ./gradlew clean test :boot:bootJar \
        -PincludeAdminDist=true \
        -PaquafishReleaseBuild=true \
        --no-daemon \
        --console=plain


# ============================================================
# Aquafish 最终运行镜像
#
# 不包含源码、Node.js、Gradle 和 JDK，
# 只保留 Java 21 JRE 与 Aquafish JAR。
# ============================================================
FROM eclipse-temurin:21-jre-noble

WORKDIR /app

LABEL org.opencontainers.image.title="Aquafish" \
      org.opencontainers.image.description="CMS, forum and AI extensible publishing platform" \
      org.opencontainers.image.source="https://github.com/yubboo/aquafish-dev"

# curl：
# 用于 Docker 与 1Panel 健康检查。
#
# util-linux：
# 提供容器入口脚本降权运行所需的 setpriv。
RUN apt-get update \
    && apt-get install --yes --no-install-recommends \
        curl \
        util-linux \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --system --gid 10001 aquafish \
    && useradd \
        --system \
        --uid 10001 \
        --gid 10001 \
        --create-home \
        --home-dir /app \
        aquafish \
    && mkdir -p /app/workdir \
    && chown -R aquafish:aquafish /app

# 复制 Spring Boot 单 JAR。
COPY --from=app-builder \
     --chown=aquafish:aquafish \
     /workspace/app/boot/build/libs/aquafish.jar \
     /app/aquafish.jar

# 复制容器启动入口。
COPY --chmod=755 \
     scripts/docker-entrypoint.sh \
     /usr/local/bin/aquafish-entrypoint

EXPOSE 8520

# Aquafish 用户数据持久化目录。
VOLUME ["/app/workdir"]

ENTRYPOINT ["/usr/local/bin/aquafish-entrypoint"]

CMD ["java", "-jar", "/app/aquafish.jar"]