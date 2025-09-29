# MCP Client 简化部署版本

最简化的 MCP Client 部署方案，只需要 3 个文件即可在云服务器上运行。

## 文件清单

```
mcp-client-simple/
├── Dockerfile              # Docker 镜像构建文件
├── docker-compose.yml      # Docker Compose 配置
├── mcp-client-1.0.0.jar   # 应用 JAR 包（需要手动复制）
└── README.md              # 说明文档
```

## 部署步骤

### 1. 本地打包
在项目根目录执行：
```bash
cd mcp-client
mvn clean package -DskipTests
```

### 2. 准备部署文件
将以下文件上传到云服务器：
- `mcp-client-simple/Dockerfile`
- `mcp-client-simple/docker-compose.yml`
- `mcp-client-simple/mcp-client-1.0.0.jar`

### 3. 云服务器部署
```bash
# 构建并启动
docker-compose up -d --build

# 查看日志
docker-compose logs -f
应用初次启动时，依赖uvx的 bilibili mcp server 需要较长的时间下载启动，日志会卡在这里一会，请耐心等待

# 停止服务
docker-compose down
```

## 服务访问

- **REST API**: http://服务器IP:8086
- **gRPC**: 服务器IP:8686
- **健康检查**: http://服务器IP:8086/actuator/health

## 数据存储

- 使用 SQLite 数据库，数据存储在 Docker 卷中
- 容器重启数据不会丢失
- 如需备份数据，可以备份整个 Docker 卷

## 工具支持

✅ **Python 工具**: 支持 `uv` 和 `uvx` 命令执行 Python 工具  
✅ **Node.js 工具**: 支持 `npm` 和 `npx` 命令执行 Node.js 工具  
✅ **缓存优化**: 工具包缓存持久化，提升执行速度  

## 注意事项

1. 确保云服务器已安装 Docker 和 Docker Compose
2. 确保端口 8086 和 8686 在防火墙中开放
3. JAR 文件名必须是 `mcp-client-1.0.0.jar`
4. 所有配置通过环境变量在 Dockerfile 中设置，无需额外配置文件
5. 首次运行时工具包下载可能较慢，后续会使用缓存加速