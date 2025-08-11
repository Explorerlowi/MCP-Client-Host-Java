# MCP Host Client

一个基于Spring Boot的MCP（Model Context Protocol）主机客户端系统，提供完整的MCP服务器管理和工具调用功能。

## 项目结构

```
mcp-host-client/
├── mcp-host/          # MCP主机服务 (端口: 8087)
├── mcp-client/        # MCP客户端服务 (端口: 8086)
├── mcp-ui/           # Web前端界面
├── mcp-service.proto # gRPC协议定义
└── README.md
```

## 快速开始

### 1. 环境要求

- Java 17+
- Maven 3.6+
- Node.js 16+ (用于前端)
- MySQL 8.0+ (用于mcp-host)

### 2. 配置文件设置

#### MCP Host 配置

复制配置模板并修改：
```bash
cp mcp-host/src/main/resources/application.yml.example mcp-host/src/main/resources/application.yml
```

修改 `application.yml` 中的以下配置：
- 数据库连接信息
- LLM API密钥（OpenAI、通义千问等）

#### MCP Client 配置

复制配置模板并修改：
```bash
cp mcp-client/src/main/resources/application.yml.example mcp-client/src/main/resources/application.yml
```

MCP Client 使用 H2 内存数据库，通常不需要修改配置。如需自定义，可修改以下配置：
- 服务端口（默认：8086）
- gRPC端口（默认：9090）
- 日志级别

#### MCP 服务器配置

复制配置模板并修改：
```bash
cp mcp-config.json.example mcp-config.json
```

可在 `mcp-config.json` 中配置你的MCP服务器，也可在前端界面中进行配置。

### 3. 构建项目

在启动服务之前，需要先构建各个模块：

#### 构建MCP Client
```bash
cd mcp-client
mvn clean install
```

#### 构建MCP Host
```bash
cd mcp-host
mvn clean install
```

### 4. 启动服务

#### 启动MCP Client
```bash
cd mcp-client
mvn spring-boot:run
```

#### 启动MCP Host
```bash
cd mcp-host
mvn spring-boot:run
```

#### 启动前端界面
```bash
cd mcp-ui
```

```bash
npm install
```

```bash
npm run dev
```

### 5. 访问应用

- 前端界面: http://localhost:5173
- MCP Host API: http://localhost:8087
- MCP Client API: http://localhost:8086

## 功能特性

### MCP Host
- 智能对话接口
- LLM集成（OpenAI、通义千问）
- 工具调用管理
- 提示模板系统

### MCP Client
- MCP服务器管理
- 工具发现和调用
- 资源管理
- 健康状态监控

### Web界面
- 服务器配置管理
- 实时状态监控
- 工具调用测试
- 响应式设计

## API文档

详细的API文档请参考：
- [MCP Client REST API指南](mcp-client/REST-API-GUIDE.md)
- [gRPC超时配置](mcp-host/GRPC-TIMEOUT-CONFIGURATION.md)

## 开发指南

### 构建项目
```bash
# 开发环境构建（包含依赖安装）
cd mcp-client && mvn clean install
cd ../mcp-host && mvn clean install

# 生产环境构建（打包）
cd mcp-client && mvn clean package
cd ../mcp-host && mvn clean package

# 构建前端
cd mcp-ui
npm run build
```

### 运行测试
```bash
mvn test
```

## 配置说明

### 敏感信息处理

项目使用以下方式处理敏感信息：
- 配置文件模板（`.example`后缀）
- `.gitignore`忽略实际配置文件
- 环境变量支持

### 支持的MCP服务器类型

- **STDIO**: 通过标准输入输出通信
- **HTTP**: 通过HTTP协议通信（开发中）

## 故障排除

### 常见问题

1. **数据库连接失败**
   - 检查MySQL服务是否启动
   - 验证数据库连接配置

2. **MCP服务器启动失败**
   - 检查命令路径是否正确
   - 验证环境变量配置
   - 查看服务器日志

3. **API调用超时**
   - 调整gRPC超时配置
   - 检查网络连接

## 贡献指南

1. Fork项目
2. 创建功能分支
3. 提交更改
4. 推送到分支
5. 创建Pull Request

## 许可证

本项目采用MIT许可证 - 详见LICENSE文件

## 联系方式

如有问题或建议，请创建Issue或联系项目维护者。