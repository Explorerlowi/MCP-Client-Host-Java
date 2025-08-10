# MCP Client 工具调用处理流程

以下是 mcp-client 接收到工具调用请求时的详细处理流程图：

```mermaid
sequenceDiagram
    participant Host as mcp-host
    participant GrpcService as MCPClientServiceImpl
    participant Registry as MCPServerRegistry
    participant RetryManager as ConnectionRetryManager
    participant Client as MCPClient
    participant Server as MCP Server

    Note over Host,Server: 工具调用请求处理流程
    
    Host->>+GrpcService: callTool(MCPToolRequest)
    Note right of GrpcService: 解析请求参数<br/>- serverName<br/>- toolName<br/>- arguments
    
    GrpcService->>GrpcService: 记录请求日志
    Note right of GrpcService: 日志: "收到工具调用请求"
    
    GrpcService->>+Registry: getClient(serverName)
    
    Registry->>Registry: 检查客户端缓存
    Note right of Registry: clients.get(serverId)
    
    alt 客户端不存在或连接断开
        Registry->>+RetryManager: canRetry(serverId)
        RetryManager-->>-Registry: 返回重试状态
        
        alt 不允许重试
            Registry-->>GrpcService: 抛出异常或返回断开的客户端
        else 允许重试
            Registry->>Registry: 获取服务器配置
            Note right of Registry: specs.get(serverId)
            
            alt 配置不存在
                Registry-->>GrpcService: 抛出 McpServerNotFoundException
            else 配置存在
                Registry->>Registry: buildClient(spec)
                Note right of Registry: 根据传输类型创建客户端<br/>- STDIO<br/>- SSE<br/>- STREAMABLE_HTTP
                
                Registry->>Registry: 存储客户端
                Note right of Registry: clients.put(serverId, client)
                
                Registry->>+RetryManager: recordSuccess(serverId)
                RetryManager-->>-Registry: 重置重试计数
                
                Registry->>Registry: 记录成功日志
            end
        end
    end
    
    Registry-->>-GrpcService: 返回 MCPClient
    
    GrpcService->>+Client: callTool(toolName, arguments)
    
    Note over Client: 根据客户端类型执行不同逻辑
    
    alt STDIO 客户端
        Client->>Client: buildToolCallRequest()
        Note right of Client: 构建 JSON-RPC 请求<br/>{"jsonrpc": "2.0", "method": "tools/call"}
        
        Client->>+Server: sendRequest(jsonRequest)
        Server-->>-Client: JSON-RPC 响应
        
        Client->>Client: parseToolResult()
        Note right of Client: 解析响应结果
        
    else SSE 客户端
        Client->>Client: buildToolCallRequest()
        Client->>+Server: HTTP POST 到消息端点
        Server-->>-Client: JSON 响应
        Client->>Client: parseToolResult()
        
    else Streamable HTTP 客户端
        Client->>Client: buildToolCallRequest()
        Client->>+Server: HTTP POST 请求
        Server-->>-Client: JSON 响应
        Client->>Client: parseToolResult()
    end
    
    Client-->>-GrpcService: 返回 MCPToolResult
    
    GrpcService->>GrpcService: 构建 gRPC 响应
    Note right of GrpcService: MCPToolResponse.newBuilder()<br/>- success<br/>- result<br/>- error
    
    GrpcService->>GrpcService: 记录完成日志
    Note right of GrpcService: 日志: "工具调用完成"
    
    GrpcService->>Host: responseObserver.onNext(response)
    GrpcService->>Host: responseObserver.onCompleted()
    GrpcService-->>-Host: 完成响应
    
    Note over Host,Server: 异常处理流程
    
    alt 服务器未找到异常
        GrpcService->>Host: responseObserver.onError(NOT_FOUND)
    else 其他异常
        GrpcService->>+RetryManager: recordFailure(serverId)
        RetryManager-->>-GrpcService: 记录失败次数
        GrpcService->>Host: responseObserver.onError(INTERNAL)
    end
```

## 关键组件说明

### 1. MCPClientServiceImpl
- **职责**: gRPC 服务实现，处理来自 mcp-host 的工具调用请求
- **主要方法**: `callTool(MCPToolRequest, StreamObserver<MCPToolResponse>)`
- **异常处理**: 捕获并转换为适当的 gRPC 状态码

### 2. MCPServerRegistry
- **职责**: 管理 MCP 服务器配置和客户端连接
- **连接管理**: 使用 `ConcurrentHashMap` 缓存客户端连接
- **重连机制**: 检测断开连接并自动重建客户端

### 3. ConnectionRetryManager
- **职责**: 管理连接重试逻辑
- **策略**: 指数退避算法 (1s → 2s → 4s → ... → 60s)
- **阈值**: 连续失败 10 次后停止重试

### 4. MCPClient 实现类
- **MCPStdioClient**: 通过标准输入输出与子进程通信
- **MCPSseClient**: 通过 Server-Sent Events 与 HTTP 服务器通信
- **MCPStreamableHttpClient**: 通过 HTTP 流式请求与服务器通信

### 5. 工具调用协议
- **格式**: JSON-RPC 2.0
- **方法**: `tools/call`
- **参数**: 工具名称和参数映射
- **响应**: 包含成功状态、结果或错误信息

## 错误处理机制

1. **服务器未找到**: 返回 `NOT_FOUND` 状态
2. **连接失败**: 记录失败次数，触发重试机制
3. **工具调用失败**: 返回 `INTERNAL` 状态，包含详细错误信息
4. **超时处理**: gRPC 客户端设置超时时间，防止长时间阻塞

## 性能优化

1. **连接复用**: 缓存客户端连接，避免重复创建
2. **异步处理**: 使用 gRPC 的 StreamObserver 进行异步响应
3. **并发安全**: 使用 `ConcurrentHashMap` 保证线程安全
4. **智能重试**: 基于指数退避的重试策略，避免频繁重连