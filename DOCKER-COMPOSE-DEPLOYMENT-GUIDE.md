# Docker Compose 部署指南

本指南将帮助您快速部署和管理 MCP Host Client 项目。

## 📋 前置要求

- Docker 20.10+
- Docker Compose 2.0+
- 至少 4GB 可用内存
- 端口 3000、8080、9090 未被占用

## 🚀 快速开始

### 1. 准备配置文件

首次部署前，需要准备配置文件：

```bash
# 进入项目目录
cd mcp-host-client

# 复制配置文件模板
cp docker/mcp-client/docker/application-docker.yml.example docker/mcp-client/docker/application-docker.yml
cp docker/mcp-client/docker/application-docker-client.yml.example docker/mcp-client/docker/application-docker-client.yml
cp docker/mcp-client/.env.example docker/mcp-client/.env
```

### 2. 启动服务

```bash
# 进入 docker 目录
cd docker/mcp-client

# 启动所有服务（后台运行）
docker-compose up -d

# 或者重新构建并启动
docker-compose up --build -d
```

### 3. 验证部署

```bash
# 查看服务状态
docker-compose ps

# 查看所有服务日志
docker-compose logs
```

访问以下地址验证服务：
- 前端界面：http://localhost:3000
- MCP Host API：http://localhost:8080
- MCP Client gRPC：localhost:8686

## 🛠️ 常用管理命令

### 服务控制

```bash
# 停止所有服务
docker-compose down

# 停止所有服务并删除数据卷（谨慎使用）
docker-compose down -v

# 重启所有服务
docker-compose restart

# 停止特定服务
docker-compose stop mcp-host
docker-compose stop mcp-client
docker-compose stop mcp-ui

# 启动特定服务
docker-compose start mcp-host

# 重启特定服务
docker-compose restart mcp-host
```

### 构建和更新

```bash
# 重新构建所有服务
docker-compose build

# 重新构建特定服务
docker-compose build mcp-host

# 停止并移除容器（保留网络、数据卷）
docker-compose rm -f mcp-host

# 重新构建并启动特定服务
docker-compose up --build -d mcp-host

# 强制重新创建容器
docker-compose up --force-recreate -d
```

## 🔍 调试和日志

### 查看日志

```bash
# 查看所有服务日志（实时）
docker-compose logs -f

# 查看特定服务日志
docker-compose logs -f mcp-host
docker-compose logs -f mcp-client
docker-compose logs -f mcp-ui

# 查看最近100行日志
docker-compose logs --tail=100 mcp-host

# 查看特定时间段的日志
docker-compose logs --since="2024-01-01T00:00:00" mcp-host
```

### 容器调试

```bash
# 进入容器内部
docker exec -it mcp-client bash
docker exec -it mcp-host bash
docker exec -it mcp-ui sh

# 在容器内执行命令后退出
exit

# 查看容器资源使用情况
docker stats

# 查看容器详细信息
docker inspect mcp-client_mcp-host_1
```

### 网络调试

```bash
# 查看 Docker 网络
docker network ls

# 查看网络详情
docker network inspect mcp-client_default

# 测试容器间连通性（在容器内执行）
docker exec -it mcp-host ping mcp-client
docker exec -it mcp-client ping mcp-host
```

## 🐛 常见问题排查

### 1. 端口冲突

```bash
# 检查端口占用
netstat -ano | findstr :3000
netstat -ano | findstr :8080
netstat -ano | findstr :8686

# Windows 杀死进程
taskkill /PID <PID> /F
```

### 2. 服务无法启动

```bash
# 查看详细错误信息
docker-compose logs mcp-host

# 检查配置文件
docker-compose config

# 清理并重新启动
docker-compose down
docker system prune -f
docker-compose up --build -d
```

### 3. 数据库连接问题

```bash
# 检查数据库容器状态
docker-compose ps mysql

# 查看数据库日志
docker-compose logs mysql

# 进入数据库容器
docker exec -it mcp-client_mysql_1 mysql -u root -p
```

### 4. gRPC 连接失败

```bash
# 检查 mcp-client 是否正常启动
docker-compose logs mcp-client | grep "gRPC server started"

# 检查网络连通性
docker exec -it mcp-host ping mcp-client

# 验证端口监听
docker exec -it mcp-client netstat -tlnp | grep 8686
```

## 📊 性能监控

### 资源使用情况

```bash
# 实时监控容器资源
docker stats

# 查看磁盘使用
docker system df

# 清理未使用的资源
docker system prune -a
```

### 健康检查

```bash
# 检查服务健康状态
curl -f http://localhost:8080/actuator/health
curl -f http://localhost:3000

# 查看应用指标（如果启用）
curl http://localhost:8080/actuator/metrics
```

## 🔧 配置管理

### 环境变量

编辑 `.env` 文件来修改环境变量：

```bash
# 编辑环境变量
notepad docker/mcp-client/.env

# 重新加载配置
docker-compose down
docker-compose up -d
```

### 配置文件热更新

```bash
# 修改配置文件后重启特定服务
docker-compose restart mcp-host

# 或者重新加载配置（如果支持）
docker exec -it mcp-host kill -HUP 1
```

## 🚨 紧急操作

### 完全重置

```bash
# 停止所有服务并删除所有数据
docker-compose down -v --remove-orphans

# 清理所有相关镜像
docker rmi $(docker images "mcp-*" -q)

# 重新构建和启动
docker-compose up --build -d
```

### 备份和恢复

```bash
# 备份数据库
docker exec mcp-client_mysql_1 mysqldump -u root -p mcp_db > backup.sql

# 恢复数据库
docker exec -i mcp-client_mysql_1 mysql -u root -p mcp_db < backup.sql
```

## 📝 最佳实践

1. **定期备份**：定期备份重要数据和配置文件
2. **监控日志**：使用 `docker-compose logs -f` 监控服务状态
3. **资源清理**：定期运行 `docker system prune` 清理未使用资源
4. **版本控制**：将配置文件变更纳入版本控制
5. **环境隔离**：为不同环境使用不同的配置文件

## 🆘 获取帮助

如果遇到问题，请按以下步骤排查：

1. 查看服务日志：`docker-compose logs -f [service-name]`
2. 检查服务状态：`docker-compose ps`
3. 验证配置文件：`docker-compose config`
4. 检查网络连通性：`docker exec -it [container] ping [target]`
5. 查看资源使用：`docker stats`

---

💡 **提示**：建议将此指南保存为书签，以便快速查阅常用命令。