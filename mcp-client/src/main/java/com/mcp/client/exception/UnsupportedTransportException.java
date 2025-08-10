package com.mcp.client.exception;

import com.mcp.client.model.TransportType;

/**
 * 不支持的传输协议异常类
 * 当尝试使用不支持的传输协议时抛出
 */
public class UnsupportedTransportException extends McpException {
    
    public UnsupportedTransportException(TransportType transport) {
        super("不支持的传输协议类型: " + transport);
    }
    
    public UnsupportedTransportException(String message) {
        super(message);
    }
    
    public UnsupportedTransportException(String message, Throwable cause) {
        super(message, cause);
    }
}