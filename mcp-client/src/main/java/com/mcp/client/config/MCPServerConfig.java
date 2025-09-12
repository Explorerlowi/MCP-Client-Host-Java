package com.mcp.client.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.Map;

/**
 * MCP 服务器配置
 * 支持从 JSON 格式加载配置
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class MCPServerConfig {
    
    /**
     * MCP 服务器配置映射
     * 格式: {"server-name": {"command": "...", "args": [...], "env": {...}}}
     */
    private Map<String, ServerConfig> mcpServers;
    
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ServerConfig {
        private String command;
        private String[] args;
        private Map<String, String> env;
        private String url;
        private String type = "stdio"; // 传输类型，默认为 stdio
        private boolean disabled = false;
        private String description;
        private Long timeout; // 请求超时时间（秒）
    }
}
