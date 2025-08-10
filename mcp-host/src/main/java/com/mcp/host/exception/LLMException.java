package com.mcp.host.exception;

/**
 * LLM 调用异常
 */
public class LLMException extends McpException {
    
    public LLMException(String message) {
        super(message);
    }
    
    public LLMException(String message, Throwable cause) {
        super(message, cause);
    }
}