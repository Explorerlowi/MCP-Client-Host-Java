FROM eclipse-temurin:17-jre-jammy

# 健康检查工具
RUN apt-get update && \
    apt-get install -y --no-install-recommends curl && \
    rm -rf /var/lib/apt/lists/*

# 非 root 运行
RUN useradd -r -s /usr/sbin/nologin -u 10001 mcphost

WORKDIR /app
# 复制可运行的 Jar（注意文件名与路径）
COPY --chown=mcphost:mcphost mcp-host-1.0.0.jar /app/app.jar

USER mcphost
EXPOSE 8087

# 合理内存设置（容器内识别 cgroup）
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75.0"


# 用环境变量控制 profile（不在这里写死 -Dspring.profiles.active）
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
