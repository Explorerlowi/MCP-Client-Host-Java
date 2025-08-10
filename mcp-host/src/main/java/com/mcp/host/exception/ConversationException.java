package com.mcp.host.exception;

/**
 * 对话处理异常
 */
public class ConversationException extends McpException {
    
    public ConversationException(String message) {
        super(message);
    }
    
    public ConversationException(String message, Throwable cause) {
        super(message, cause);
    }
}