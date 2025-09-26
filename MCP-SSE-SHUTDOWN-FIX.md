# MCP SSE 客户端关闭时重连问题修复

## 问题描述

在应用关闭时，SSE 客户端仍会尝试重新连接，导致以下问题：

1. **应用关闭时出现重连日志**：
   ```
   2025-09-26T15:58:27.195+08:00  INFO 243100 --- [mcp-client] [      Thread-23] c.mcp.client.service.impl.MCPSseClient   : 尝试重新连接 SSE [zhipu-web-search-sse] - 第 1 次尝试
   ```

2. **连接错误和异常堆栈**：
   ```
   2025-09-26T15:58:27.193+08:00 ERROR 243100 --- [mcp-client] [ctor-http-nio-4] c.mcp.client.service.impl.MCPSseClient   : SSE 连接错误 [zhipu-web-search-sse]: 200 OK from GET https://open.bigmodel.cn/api/mcp/web_search/sse
   ```

3. **应用无法优雅关闭**：重连线程阻止应用正常退出

## 根本原因分析

### 1. 缺少应用级别的关闭处理

**MCPServerRegistryImpl** 负责管理所有 MCP 客户端，但缺少 `@PreDestroy` 方法来在应用关闭时主动关闭所有客户端连接。

### 2. SSE 连接关闭时的重连逻辑问题

在 **MCPSseClient** 中，当 SSE 连接正常关闭时（`doOnComplete`），仍然会触发重连：

```java
.doOnComplete(() -> {
    log.info("SSE 连接已关闭: {}", spec.getId());
    transitionToDisconnected(null, true, false); // true 参数触发重连
});
```

### 3. 时序问题

应用关闭的执行顺序：
1. Spring 开始关闭流程
2. gRPC 服务器收到关闭信号并关闭
3. SSE 连接因为网络断开而触发 `doOnComplete`
4. SSE 客户端开始重连尝试
5. 应用尝试完全关闭，但重连线程仍在运行

## 解决方案

### 1. 添加应用级别的关闭处理

在 **MCPServerRegistryImpl** 中添加 `@PreDestroy` 方法：

```java
/**
 * 应用关闭时清理所有 MCP 客户端连接
 */
@PreDestroy
public void cleanup() {
    log.info("应用关闭，开始清理所有 MCP 客户端连接");
    
    try {
        // 关闭所有客户端连接
        for (Map.Entry<String, MCPClient> entry : clients.entrySet()) {
            try {
                log.debug("关闭 MCP 客户端连接: {}", entry.getKey());
                entry.getValue().close();
                log.debug("已关闭 MCP 客户端连接: {}", entry.getKey());
            } catch (Exception e) {
                log.warn("关闭 MCP 客户端连接时发生错误: {}", entry.getKey(), e);
            }
        }
        
        // 清空内存
        clients.clear();
        log.info("所有 MCP 客户端连接已关闭");
        
    } catch (Exception e) {
        log.error("清理 MCP 客户端连接时发生错误", e);
    }
}
```

### 2. 修复 SSE 连接关闭时的重连逻辑

修改 **MCPSseClient** 中的 `doOnComplete` 处理：

```java
.doOnComplete(() -> {
    log.info("SSE 连接已关闭: {}", spec.getId());
    // 只有在应该重连的情况下才触发重连，避免应用关闭时的无效重连
    transitionToDisconnected(null, shouldReconnect, false);
});
```

**关键改进**：
- 原来：`transitionToDisconnected(null, true, false)` - 总是触发重连
- 现在：`transitionToDisconnected(null, shouldReconnect, false)` - 根据 `shouldReconnect` 标志决定是否重连

### 3. 确保关闭顺序正确

通过 `@PreDestroy` 注解，Spring 会在应用关闭时自动调用 `cleanup()` 方法：

1. Spring 开始关闭流程
2. **MCPServerRegistryImpl.cleanup()** 被调用
3. 所有 MCP 客户端的 `close()` 方法被调用
4. **MCPSseClient.close()** 设置 `shouldReconnect = false`
5. SSE 连接关闭时不再触发重连
6. 应用优雅关闭

## 修改的文件

### 1. MCPServerRegistryImpl.java

- **添加导入**：`import jakarta.annotation.PreDestroy;`
- **添加方法**：`@PreDestroy public void cleanup()`

### 2. MCPSseClient.java

- **修改**：`doOnComplete` 中的重连逻辑
- **从**：`transitionToDisconnected(null, true, false)`
- **到**：`transitionToDisconnected(null, shouldReconnect, false)`

## 测试验证

创建了 **MCPServerRegistryShutdownTest** 来验证关闭逻辑：

- 测试所有客户端都被正确关闭
- 测试异常情况下的处理
- 测试客户端映射被正确清空

## 预期效果

修复后，应用关闭时的日志应该是：

```
2025-09-26T15:58:27.168+08:00  INFO 243100 --- [mcp-client] [       Thread-1] com.mcp.client.config.GrpcServerConfig   : 收到关闭信号，正在关闭 gRPC 服务器...
2025-09-26T15:58:27.169+08:00  INFO 243100 --- [mcp-client] [       Thread-1] com.mcp.client.config.GrpcServerConfig   : 正在关闭 gRPC 服务器...
2025-09-26T15:58:27.174+08:00  INFO 243100 --- [mcp-client] [       Thread-1] com.mcp.client.config.GrpcServerConfig   : gRPC 服务器已关闭
2025-09-26T15:58:27.180+08:00  INFO 243100 --- [mcp-client] [ionShutdownHook] c.m.c.s.impl.MCPServerRegistryImpl       : 应用关闭，开始清理所有 MCP 客户端连接
2025-09-26T15:58:27.181+08:00  INFO 243100 --- [mcp-client] [ionShutdownHook] c.mcp.client.service.impl.MCPSseClient   : 关闭 MCP SSE 客户端: zhipu-web-search-sse
2025-09-26T15:58:27.182+08:00  INFO 243100 --- [mcp-client] [ionShutdownHook] c.mcp.client.service.impl.MCPSseClient   : 断开 MCP SSE 客户端连接: zhipu-web-search-sse
2025-09-26T15:58:27.183+08:00  INFO 243100 --- [mcp-client] [ionShutdownHook] c.m.c.s.impl.MCPServerRegistryImpl       : 所有 MCP 客户端连接已关闭
```

**不再出现**：
- 重连尝试日志
- 连接错误异常
- 应用挂起问题

## 总结

这个修复解决了应用关闭时 SSE 客户端仍尝试重连的问题，确保了应用能够优雅关闭，避免了不必要的错误日志和资源浪费。修改遵循了 Spring 的生命周期管理最佳实践，使用 `@PreDestroy` 注解来确保资源的正确清理。
