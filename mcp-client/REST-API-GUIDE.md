# MCP Client REST API 指南

本文档描述了 MCP Client 项目中新增的 REST API 接口，这些接口与现有的 gRPC 服务提供相同的功能。

## 概述

为了提供更灵活的访问方式，我们为现有的 gRPC 服务添加了对应的 REST API 接口。这些 REST API 与 gRPC 服务提供完全相同的功能，但使用标准的 HTTP 协议。

## gRPC 与 REST API 对应关系

### 1. 工具调用服务 (MCPToolController)

#### gRPC: CallTool
- **gRPC 方法**: `CallTool(MCPToolRequest) returns (MCPToolResponse)`
- **REST API**: `POST /api/mcp/tools/call`
- **功能**: 调用指定服务器的指定工具

**请求示例**:
```json
{
  "serverName": "cs-calculator",
  "toolName": "add",
  "arguments": {
    "a": "5",
    "b": "3"
  }
}
```

**响应示例**:
```json
{
  "success": true,
  "result": "8",
  "error": ""
}
```

#### gRPC: GetTools (指定服务器)
- **gRPC 方法**: `GetTools(GetToolsRequest) returns (GetToolsResponse)` (with server_name)
- **REST API**: `GET /api/mcp/tools/server/{serverName}`
- **功能**: 获取指定服务器的工具列表

**响应示例**:
```json
[
  {
    "name": "add",
    "description": "Add two numbers",
    "serverName": "cs-calculator",
    "inputSchema": "{\"type\":\"object\",\"properties\":{\"a\":{\"type\":\"number\"},\"b\":{\"type\":\"number\"}}}",
    "outputSchema": "{\"type\":\"number\"}"
  }
]
```

#### gRPC: GetTools (所有服务器)
- **gRPC 方法**: `GetTools(GetToolsRequest) returns (GetToolsResponse)` (without server_name)
- **REST API**: `GET /api/mcp/tools/all`
- **功能**: 获取所有服务器的工具列表

### 2. 服务器健康检查服务 (MCPServerController)

#### gRPC: GetServerHealth
- **gRPC 方法**: `GetServerHealth(GetServerHealthRequest) returns (GetServerHealthResponse)`
- **REST API**: `GET /api/mcp/servers/{id}/health`
- **功能**: 获取指定服务器的健康状态

**响应示例**:
```json
{
  "serverName": "cs-calculator",
  "connected": true,
  "status": "HEALTHY",
  "lastCheckTime": 1704067200000
}
```

#### 额外的 REST API
- **REST API**: `GET /api/mcp/servers/health/grpc-format`
- **功能**: 获取所有服务器的健康状态（gRPC 格式）

## 现有的 REST API 接口

除了新增的工具调用相关接口，项目中还包含以下现有的 REST API：

### 服务器管理
- `GET /api/mcp/servers` - 获取所有服务器列表
- `POST /api/mcp/servers` - 添加新服务器
- `PUT /api/mcp/servers/{id}` - 更新服务器配置
- `DELETE /api/mcp/servers/{id}` - 删除服务器
- `GET /api/mcp/servers/health` - 获取所有服务器健康状态
- `POST /api/mcp/servers/{id}/reconnect` - 重新连接服务器
- `POST /api/mcp/servers/{id}/shutdown` - 关闭服务器连接
- `GET /api/mcp/servers/stats` - 获取连接统计信息
- `POST /api/mcp/servers/{id}/test` - 测试服务器连接

### 工具管理
- `GET /api/mcp/servers/{id}/tools` - 获取指定服务器的工具
- `GET /api/mcp/servers/tools` - 获取所有服务器的工具

### 资源管理
- `GET /api/mcp/servers/{id}/resources` - 获取指定服务器的资源
- `GET /api/mcp/servers/resources` - 获取所有服务器的资源

### 提示模板管理
- `GET /api/mcp/servers/{id}/prompts` - 获取指定服务器的提示模板
- `GET /api/mcp/servers/prompts` - 获取所有服务器的提示模板

## 使用建议

1. **工具调用**: 使用新的 `/api/mcp/tools/call` 接口进行工具调用，它提供了与 gRPC 相同的功能但更易于集成
2. **健康检查**: 使用 `/api/mcp/servers/{id}/health` 获取单个服务器状态，使用 `/api/mcp/servers/health/grpc-format` 获取所有服务器状态
3. **工具列表**: 使用 `/api/mcp/tools/server/{serverName}` 获取特定服务器工具，使用 `/api/mcp/tools/all` 获取所有工具

## 错误处理

所有 REST API 都遵循标准的 HTTP 状态码：
- `200 OK` - 请求成功
- `404 Not Found` - 服务器或资源未找到
- `500 Internal Server Error` - 服务器内部错误

对于工具调用，即使工具执行失败，HTTP 状态码仍为 200，但响应中的 `success` 字段为 `false`，`error` 字段包含错误信息。

## 端口信息

- **mcp-client**: 运行在端口 8086
- **mcp-host**: 运行在端口 8087

确保在调用 API 时使用正确的端口。