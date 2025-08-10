package com.mcp.client.model;

/**
 * MCP 传输协议类型枚举
 */
public enum TransportType {
    /**
     * 标准输入输出传输协议
     * 通过子进程的标准输入输出与 MCP 服务器通信
     */
    STDIO,
    
    /**
     * Server-Sent Events 传输协议
     * 通过 HTTP 长轮询与 MCP 服务器通信
     */
    SSE,
    
    /**
     * Streamable HTTP 传输协议
     * 通过统一端点支持双向通信和会话管理
     */
    STREAMABLEHTTP
}