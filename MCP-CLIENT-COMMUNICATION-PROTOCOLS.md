# MCP 客户端与服务器通信协议详解

欢迎来到 MCP (Model Context Protocol) 的世界！本文档将以最通俗易懂的方式，带你了解三种主要的 MCP 客户端 (`Stdio`, `SSE`, `StreamableHttp`) 是如何与 MCP 服务器进行通信的。我们将一起探索从建立连接、获取工具列表到最终调用工具的全过程。

## 核心概念：JSON-RPC 2.0

在深入了解具体协议之前，你需要知道，所有 MCP 通信都基于一个简单而强大的规范：[JSON-RPC 2.0](https://www.jsonrpc.org/specification)。这意味着无论使用哪种传输方式，客户端和服务器之间传递的消息都遵循以下格式：

**请求 (Request) 格式:**
```json
{
  "jsonrpc": "2.0",
  "method": "some_method_name",
  "params": { /* 参数 */ },
  "id": 1
}
```
- `method`: 要调用的方法名，例如 `initialize`, `tools/list`, `tools/call`。
- `params`: 方法所需的参数，是一个 JSON 对象。
- `id`: 请求的唯一标识，用于匹配响应。如果是通知 (Notification)，则没有 `id`。

**响应 (Response) 格式:**
```json
{
  "jsonrpc": "2.0",
  "result": { /* 成功时的返回结果 */ },
  "error": { /* 失败时的错误信息 */ },
  "id": 1
}
```
- `result`: 成功时，包含服务器返回的数据。
- `error`: 失败时，包含错误代码和描述信息。
- `id`: 对应请求的 `id`。

所有通信细节都封装在 `AbstractMCPClient.java` 中，它提供了构建标准 JSON-RPC 请求的通用方法。

## 通信全流程概览

无论使用哪种协议，通信流程都遵循以下三个主要阶段：

1.  **连接与握手 (Connection & Handshake)**: 客户端与服务器建立物理连接，并进行初始化协商。
2.  **获取工具列表 (Get Tools List)**: 客户端向服务器请求可用的工具列表。
3.  **调用工具 (Call Tool)**: 客户端请求服务器执行某个特定的工具。

接下来，我们将详细拆解每种协议在这三个阶段的具体实现。

---

## 1. STDIO (标准输入/输出) 协议

`STDIO` 模式是最基础的通信方式，通常用于本地进程间通信。客户端通过启动一个子进程（MCP 服务器），并利用其标准输入 (`stdin`) 和标准输出 (`stdout`) 来交换数据。

**实现文件**: `mcp-client/src/main/java/com/mcp/client/service/impl/MCPStdioClient.java`

### 阶段一：连接与握手

1.  **启动进程**: `MCPStdioClient` 通过 `ProcessBuilder` 启动服务器的可执行文件。
2.  **获取 IO 流**: 客户端获取子进程的 `stdin` (用于发送数据) 和 `stdout` (用于接收数据)。
3.  **发送 `initialize` 请求**: 客户端构建一个 `initialize` 请求，并通过 `stdin` 发送给服务器。

    **请求 (`-> stdin`):**
    ```json
    {
      "jsonrpc": "2.0",
      "method": "initialize",
      "params": {
        "protocolVersion": "2024-11-05",
        "clientInfo": { "name": "mcp-java-client", "version": "1.0.0" },
        "capabilities": { "tools": true }
      },
      "id": 1
    }
    ```

4.  **接收 `initialize` 响应**: 客户端从 `stdout` 读取服务器的响应。

    **响应 (`<- stdout`):**
    ```json
    {
      "jsonrpc": "2.0",
      "result": {
        "protocolVersion": "2024-11-05",
        "serverInfo": { /* 服务器信息 */ },
        "capabilities": { /* 服务器能力 */ }
      },
      "id": 1
    }
    ```
    例如：
    ```json
    {
      "jsonrpc": "2.0",
      "result": {
        "protocolVersion": "2024-11-05",
        "capabilities": {
          "experimental": {},
          "logging": {},
          "prompts": {
            "listChanged": false
          },
          "resources": {
            "subscribe": false,
            "listChanged": false
          },
          "tools": {
            "listChanged": false
          },
          "completions": {}
        },
        "serverInfo": {
          "name": "Calculator MCP Server",
          "version": "1.0.0"
        }
      },
      "id": 1
    }
    ```

5.  **发送 `initialized` 通知**: 握手成功后，客户端发送一个 `initialized` 通知，告知服务器客户端已准备就绪。

    **通知 (`-> stdin`):**
    ```json
    {
      "jsonrpc": "2.0",
      "method": "notifications/initialized",
      "params": {}
    }
    ```

### 阶段二：获取工具列表

1.  **发送 `tools/list` 请求**: 客户端构建并发送 `tools/list` 请求。

    **请求 (`-> stdin`):**
    ```json
    {
      "jsonrpc": "2.0",
      "method": "tools/list",
      "params": {},
      "id": 2
    }
    ```

2.  **接收工具列表响应**: 服务器返回一个包含所有可用工具的列表。

    **响应 (`<- stdout`):**
    ```json
    {
      "jsonrpc": "2.0",
      "result": {
        "tools": [
          { "name": "tool_a", "description": "...", "inputSchema": { /* ... */ } },
          { "name": "tool_b", "description": "...", "inputSchema": { /* ... */ } }
        ]
      },
      "id": 2
    }
    ```

### 阶段三：调用工具

1.  **发送 `tools/call` 请求**: 客户端指定工具名称和参数，发送 `tools/call` 请求。

    **请求 (`-> stdin`):**
    ```json
    {
      "jsonrpc": "2.0",
      "method": "tools/call",
      "params": {
        "name": "tool_a",
        "arguments": { "param1": "value1" }
      },
      "id": 3
    }
    ```

2.  **接收工具执行结果**: 服务器执行工具后，返回执行结果。

    **响应 (`<- stdout`):**
    ```json
    {
      "jsonrpc": "2.0",
      "result": { /* 工具执行的输出 */ },
      "id": 3
    }
    ```

---

## 2. SSE (Server-Sent Events) 协议

`SSE` 是一种基于 HTTP 的协议，允许服务器向客户端单向推送事件。在 MCP 中，它被巧妙地用于实现双向通信：客户端通过标准的 HTTP POST 发送请求，服务器通过 SSE 连接推送响应和事件。

**实现文件**: `mcp-client/src/main/java/com/mcp/client/service/impl/MCPSseClient.java`

### 阶段一：连接与握手

1.  **建立 SSE 连接**: 客户端向服务器的 `/sse` 端点发起一个 HTTP GET 请求，请求头中包含 `Accept: text/event-stream`。
2.  **获取消息端点**: 服务器保持连接，并发送第一条 SSE 事件，该事件包含一个用于 POST 消息的专用 `endpoint` URI，如：/messages?sessionId=f8fda987-2172-4142-ad09-d88ffb36e2bd。
3.  **执行握手**: 客户端拿到 `endpoint` 后，向该 URI 发送 `initialize` POST 请求。后续的响应和服务器事件都将通过 SSE 连接推送回来。

    *请求和响应的数据格式与 STDIO 模式完全相同，只是传输方式变为了 HTTP POST 和 SSE 事件流。*

### 阶段二 & 三：获取工具与调用工具

-   **请求**: 客户端向从服务器获取的 `endpoint` URI 发送 `tools/list` 或 `tools/call` 的 HTTP POST 请求。
-   **响应**: 服务器通过 SSE 连接将结果作为 `message` 事件推送给客户端。每个 SSE 事件的 `data` 字段就是一个完整的 JSON-RPC 响应字符串。

**SSE 事件示例 (`<- /sse`):**
```
id: 123
event: message
data: {"jsonrpc":"2.0","result":{...},"id":2}

```

这种设计的优势在于，服务器可以主动向客户端推送通知，例如工具状态的变更。

---

## 3. StreamableHTTP 协议

`StreamableHTTP` 是一种更现代的、统一的通信协议。它试图将 SSE 的实时推送能力和传统 HTTP 的请求/响应模式结合在一个端点上，同时支持会话管理和断线续传。

**实现文件**: `mcp-client/src/main/java/com/mcp/client/service/impl/MCPStreamableHttpClient.java`

### 阶段一：连接与握手

1.  **建立连接**: 客户端首先尝试向服务器的 `/mcp` 端点发起一个 SSE 连接请求。如果服务器支持，则建立一个 SSE 事件流用于接收服务器推送。
2.  **会话管理**: 客户端在第一次发送 POST 请求时，服务器会在响应头中返回一个 `Mcp-Session-Id`。客户端需要保存这个 `sessionId`，并在后续的所有请求中通过请求头回传，以维持会话。
3.  **执行握手**: 客户端向服务器的 `/mcp` 端点发送 `initialize` POST 请求。数据格式与前两种模式一致。

### 阶段二 & 三：获取工具与调用工具

-   **请求**: 客户端向服务器的 `/mcp` 端点发送 `tools/list` 或 `tools/call` 的 HTTP POST 请求，并在请求头中带上 `Mcp-Session-Id`。
-   **响应**: 服务器通过 HTTP 响应体直接返回 JSON-RPC 结果。如果建立了 SSE 连接，服务器也可以通过 SSE 推送异步事件。

这种模式的灵活性最高，它既可以像传统的 HTTP API 一样工作（一问一答），也可以利用 SSE 实现实时通信。

## 总结

| 协议 | 连接方式 | 优点 | 缺点 |
| :--- | :--- | :--- | :--- |
| **STDIO** | 子进程标准输入/输出 | 简单、高效、无需网络 | 仅限本地，不易调试 |
| **SSE** | HTTP GET (SSE) + POST | 实时性好，服务器可主动推送 | 需要两个端点，实现稍复杂 |
| **StreamableHTTP** | 单一 HTTP 端点 (POST + 可选 SSE) | 统一、灵活、支持会话 | 协议较新，需要服务器完全支持 |