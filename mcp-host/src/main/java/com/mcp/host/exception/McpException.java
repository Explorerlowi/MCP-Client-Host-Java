package com.mcp.host.exception;

/**
 * MCP 基础异常类
 */
public class McpException extends RuntimeException {
    
    public McpException(String message) {
        super(message);
    }
    
    public McpException(String message, Throwable cause) {
        super(message, cause);
    }
}