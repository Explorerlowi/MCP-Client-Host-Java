package com.mcp.client.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

/**
 * MCP 服务器配置
 * 支持从 application.yml 或 JSON 文件加载配置
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "mcp")
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
        private String type = "stdio"; // 传输类型
        private boolean disabled = false;
        private String description;
        private Long timeout; // 请求超时时间（秒）
    }
}