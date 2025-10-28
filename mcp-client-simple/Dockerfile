# 简化版 MCP Client Dockerfile
# 直接在 Java 镜像中安装 Python 3.12
# 使用 Ubuntu 24.04 (Noble) 以获得官方 Python 3.12 支持

FROM eclipse-temurin:17-jre-noble

ENV DEBIAN_FRONTEND=noninteractive \
    PIP_DISABLE_PIP_VERSION_CHECK=1 \
    PIP_NO_CACHE_DIR=1

# 可在构建时通过 --build-arg 覆盖 PyPI 源
ARG PIP_INDEX_URL=https://mirrors.aliyun.com/pypi/simple/

# 1) 安装基础工具和 Python 3.12（noble 官方支持）
RUN apt-get update && \
    apt-get install -y --no-install-recommends \
      ca-certificates curl gnupg bash \
      python3.12 python3.12-venv python3.12-dev python3-pip && \
    rm -rf /var/lib/apt/lists/*

# 2) 设置 Python 符号链接
RUN ln -sf /usr/bin/python3.12 /usr/bin/python3 && \
    ln -sf /usr/bin/python3.12 /usr/bin/python && \
    python3 --version && \
    pip3 --version

# 3) 安装 Node.js（优先 NodeSource 20，失败回退到系统源 nodejs+npm）
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

# 4) 安装 uv（同时提供 uv 与 uvx）
# 在 Docker 容器中使用 --break-system-packages 是安全的
RUN python3 -m pip install --break-system-packages --no-cache-dir -i ${PIP_INDEX_URL} uv && \
    uv --version && uvx --version

# 5) 创建带 HOME 的普通用户（供 uvx / npx 缓存使用）
RUN useradd -m -u 10002 -s /bin/bash mcpclient && \
    mkdir -p /home/mcpclient/.cache /home/mcpclient/.npm /home/mcpclient/.local/bin && \
    chown -R mcpclient:mcpclient /home/mcpclient

WORKDIR /app

# 创建数据目录用于SQLite数据库
RUN mkdir -p /app/data && chown -R mcpclient:mcpclient /app /app/data

# 复制JAR文件
COPY mcp-client-1.0.0.jar /app/app.jar
RUN chown mcpclient:mcpclient /app/app.jar

USER mcpclient

# 设置环境变量
ENV HOME=/home/mcpclient \
    XDG_CACHE_HOME=/home/mcpclient/.cache \
    PATH="${PATH}:/home/mcpclient/.local/bin" \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75.0" \
    SPRING_DATASOURCE_URL=jdbc:sqlite:/app/data/mcp.db \
    SPRING_DATASOURCE_DRIVER_CLASS_NAME=org.sqlite.JDBC \
    SPRING_JPA_HIBERNATE_DDL_AUTO=update \
    SPRING_JPA_PROPERTIES_HIBERNATE_DIALECT=org.hibernate.community.dialect.SQLiteDialect

EXPOSE 8086 8686

ENTRYPOINT ["java", "-jar", "/app/app.jar"]