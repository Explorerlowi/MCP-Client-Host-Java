package com.mcp.host.exception;

/**
 * 指令解析异常
 */
public class InstructionParseException extends McpException {
    
    public InstructionParseException(String message) {
        super(message);
    }
    
    public InstructionParseException(String message, Throwable cause) {
        super(message, cause);
    }
}