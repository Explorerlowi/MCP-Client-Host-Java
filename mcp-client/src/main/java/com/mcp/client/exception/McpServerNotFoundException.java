package com.mcp.client.exception;

/**
 * MCP 服务器未找到异常
 */
public class McpServerNotFoundException extends McpException {
    
    public McpServerNotFoundException(String serverId) {
        super("MCP server not found: " + serverId);
    }
}