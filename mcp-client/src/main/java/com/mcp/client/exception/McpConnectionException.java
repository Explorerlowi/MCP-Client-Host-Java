package com.mcp.client.exception;

/**
 * MCP 连接异常类
 * 当 MCP 客户端连接失败或通信错误时抛出
 */
public class McpConnectionException extends McpException {
    
    public McpConnectionException(String message) {
        super(message);
    }
    
    public McpConnectionException(String message, Throwable cause) {
        super(message, cause);
    }
}