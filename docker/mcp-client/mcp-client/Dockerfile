# mcp-client/Dockerfile
FROM eclipse-temurin:17-jre-jammy

ENV DEBIAN_FRONTEND=noninteractive \
    PIP_DISABLE_PIP_VERSION_CHECK=1 \
    PIP_NO_CACHE_DIR=1

# 可在构建时通过 --build-arg 覆盖 PyPI 源
# 例如：--build-arg PIP_INDEX_URL=https://pypi.org/simple
ARG PIP_INDEX_URL=https://mirrors.aliyun.com/pypi/simple/

# 1) 基础工具 + Python3/pip + curl + gnupg
RUN apt-get update && \
    apt-get install -y --no-install-recommends \
      ca-certificates curl gnupg bash python3 python3-pip && \
    rm -rf /var/lib/apt/lists/*

# 2) 安装 Node.js（优先 NodeSource 20，失败回退到系统源 nodejs+npm）
RUN set -eux; \
    curl -fsSL https://deb.nodesource.com/setup_20.x -o /tmp/nodesource.sh || true; \
    if [ -s /tmp/nodesource.sh ]; then \
        bash /tmp/nodesource.sh && apt-get update && apt-get install -y --no-install-recommends nodejs; \
    else \
        echo "NodeSource 不可用，回退到系统源 nodejs+npm"; \
        apt-get update && apt-get install -y --no-install-recommends nodejs npm; \
    fi; \
    node --version || true; \
    npm --version || true; \
    rm -rf /var/lib/apt/lists/*

# 3) 安装 uv（同时提供 uv 与 uvx）
RUN python3 -m pip install --no-cache-dir -i ${PIP_INDEX_URL} uv && \
    uv --version && uvx --version

# 4) 创建带 HOME 的普通用户（供 uvx / npx 缓存使用）
RUN useradd -m -u 10002 -s /bin/bash mcpclient && \
    mkdir -p /home/mcpclient/.cache /home/mcpclient/.npm /home/mcpclient/.local/bin && \
    chown -R mcpclient:mcpclient /home/mcpclient

# 5) 运行期环境变量
ENV HOME=/home/mcpclient \
    XDG_CACHE_HOME=/home/mcpclient/.cache \
    PATH="${PATH}:/home/mcpclient/.local/bin" \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75.0"

WORKDIR /app
# 注意：确保构建上下文中存在该 JAR
COPY --chown=mcpclient:mcpclient mcp-client-1.0.0.jar /app/app.jar

USER mcpclient

EXPOSE 8086 9090


# 用环境变量控制 Spring Profile（不要在这里写死 -Dspring.profiles.active）
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
