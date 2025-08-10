package com.mcp.client.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import jakarta.validation.constraints.NotBlank;
import java.time.Instant;

/**
 * MCP 服务器信息模型
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MCPServerInfo {
    
    @NotBlank(message = "服务器名称不能为空")
    @JsonProperty("name")
    private String name;
    
    @JsonProperty("version")
    private String version;
    
    @JsonProperty("description")
    private String description;
    
    @JsonProperty("transport")
    private TransportType transport;
    
    @JsonProperty("connected")
    private boolean connected;
    
    @JsonProperty("last_ping_time")
    private Instant lastPingTime;
    
    @JsonProperty("capabilities")
    private MCPServerCapabilities capabilities;
    
    /**
     * 协议版本
     */
    @JsonProperty("protocol_version")
    private String protocolVersion;
    
    /**
     * 服务器实现信息
     */
    @JsonProperty("server_info")
    private ServerImplementationInfo serverInfo;
    
    /**
     * 服务器实现信息
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ServerImplementationInfo {
        @JsonProperty("name")
        private String name;
        
        @JsonProperty("version")
        private String version;
    }
}