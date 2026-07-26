# syntax=docker/dockerfile:1.7

# Aquafish 前端构建阶段：严格使用 pnpm 锁文件生成后台静态资源。
# 多架构发布时只在构建机架构执行一次测试和构建；生成的静态资源与目标 CPU 无关。
FROM --platform=$BUILDPLATFORM node:22-alpine AS admin-builder
WORKDIR /workspace/admin
RUN corepack enable
COPY admin/package.json admin/pnpm-lock.yaml admin/pnpm-workspace.yaml ./
RUN pnpm install --frozen-lockfile
COPY admin/ ./
RUN pnpm test && pnpm build

# Aquafish 后端构建阶段：把上一步 admin/dist 嵌入 Spring Boot 单 JAR。
# Java 字节码同样与目标 CPU 无关，避免在 arm64 模拟器中重复执行全工程测试。
FROM --platform=$BUILDPLATFORM eclipse-temurin:21-jdk AS app-builder
WORKDIR /workspace
COPY app/ ./app/
COPY --from=admin-builder /workspace/admin/dist ./admin/dist/
RUN chmod +x ./app/gradlew \
    && cd app \
    && ./gradlew clean test :boot:bootJar \
        -PincludeAdminDist=true \
        -PaquafishReleaseBuild=true \
        --no-daemon --console=plain

# 运行阶段不携带源码和构建工具，只保留 Java 21 与 Aquafish JAR。
FROM eclipse-temurin:21-jre-noble
WORKDIR /app

LABEL org.opencontainers.image.title="Aquafish" \
    org.opencontainers.image.description="CMS, forum and AI extensible publishing platform" \
    org.opencontainers.image.source="https://github.com/yubboo/aquafish-dev"

# curl 用于 Docker/1Panel 健康检查；util-linux 提供降权启动所需的 setpriv。
RUN apt-get update \
    && apt-get install --yes --no-install-recommends curl util-linux \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --system --gid 10001 aquafish \
    && useradd --system --uid 10001 --gid 10001 \
        --create-home --home-dir /app aquafish \
    && mkdir -p /app/workdir \
    && chown -R aquafish:aquafish /app

COPY --from=app-builder --chown=aquafish:aquafish /workspace/app/boot/build/libs/aquafish.jar /app/aquafish.jar
COPY --chmod=755 scripts/docker-entrypoint.sh /usr/local/bin/aquafish-entrypoint

EXPOSE 8520
VOLUME ["/app/workdir"]

ENTRYPOINT ["/usr/local/bin/aquafish-entrypoint"]
CMD ["java", "-jar", "/app/aquafish.jar"]
