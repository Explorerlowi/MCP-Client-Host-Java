# gRPC 超时配置说明

## 概述

本文档说明了 MCP Host 项目中 gRPC 调用超时时间的配置方法。

## 问题背景

原先 MCP Host 在进行 RPC 调用 MCP Client 时使用硬编码的 30 秒超时时间，在某些复杂操作或网络环境较差的情况下可能导致超时。

## 解决方案

### 1. 配置文件修改

在 `application.yml` 中添加了 gRPC 超时配置：

```yaml
mcp:
  client:
    grpc:
      host: localhost
      port: 9090
      timeout-seconds: 120  # gRPC 调用超时时间（秒），默认120秒
```

### 2. 代码修改

#### GrpcClientConfig.java
- 添加了 `@Value("${mcp.client.grpc.timeout-seconds:120}")` 配置注入
- 将所有 gRPC 存根的超时时间从硬编码 30 秒改为配置值
- 包括：阻塞存根、异步存根、Future 存根

#### MCPHostServiceImpl.java
- 添加了超时配置注入
- 将工具调用的超时时间从硬编码 30 秒改为配置值
- 增加了超时时间的日志输出

#### MCPSystemPromptBuilderImpl.java
- 添加了超时配置注入
- 将获取工具列表的超时时间从硬编码 30 秒改为配置值

## 配置参数说明

| 参数 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `mcp.client.grpc.timeout-seconds` | int | 120 | gRPC 调用超时时间（秒） |

## 使用方法

### 修改超时时间

在 `application.yml` 中修改 `timeout-seconds` 值：

```yaml
mcp:
  client:
    grpc:
      timeout-seconds: 180  # 设置为 3 分钟
```

### 环境变量配置

也可以通过环境变量设置：

```bash
export MCP_CLIENT_GRPC_TIMEOUT_SECONDS=180
```

### JVM 参数配置

或通过 JVM 参数设置：

```bash
java -Dmcp.client.grpc.timeout-seconds=180 -jar mcp-host.jar
```

## 技术实现细节

### 1. 配置注入
使用 Spring 的 `@Value` 注解注入配置值，支持默认值设置。

### 2. gRPC 存根配置
所有 gRPC 存根都使用 `withDeadlineAfter(timeoutSeconds, TimeUnit.SECONDS)` 设置超时。

### 3. 向后兼容
如果未配置 `timeout-seconds`，默认使用 120 秒，比原来的 30 秒更宽松。

## 重要说明

1. **超时时间建议**：根据实际业务需求设置，一般建议 60-300 秒
2. **网络环境**：网络较差的环境建议设置更长的超时时间
3. **工具复杂度**：复杂工具操作可能需要更长的执行时间
4. **资源消耗**：过长的超时时间可能导致资源占用时间过长

## 测试验证

启动应用后，查看日志确认超时配置生效：

```
创建 MCP Client 服务 gRPC 阻塞存根，调用超时: 120秒
创建 MCP Client 服务 gRPC 异步存根，调用超时: 120秒
创建 MCP Client 服务 gRPC Future 存根，调用超时: 120秒
```

## 故障排除

### 1. 配置不生效
- 检查 `application.yml` 格式是否正确
- 确认配置路径 `mcp.client.grpc.timeout-seconds` 正确

### 2. 仍然超时
- 增加超时时间配置值
- 检查网络连接状况
- 查看 MCP Client 服务状态

### 3. 日志确认
查看应用启动日志和调用日志，确认超时配置已应用。