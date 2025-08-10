package com.mcp.client.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import jakarta.validation.constraints.NotBlank;
import java.time.Instant;

/**
 * MCP 服务器健康状态模型
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MCPServerHealth {
    
    @NotBlank(message = "服务器ID不能为空")
    @JsonProperty("server_id")
    private String serverId;
    
    @JsonProperty("connected")
    private boolean connected;
    
    @JsonProperty("last_check")
    @Builder.Default
    private Instant lastCheck = Instant.now();
    
    @JsonProperty("response_time_ms")
    private Long responseTimeMs;
    
    @JsonProperty("error_message")
    private String errorMessage;
    
    @JsonProperty("uptime_seconds")
    private Long uptimeSeconds;
    
    @JsonProperty("tool_count")
    private Integer toolCount;
    
    @JsonProperty("status")
    @Builder.Default
    private String status = "UNKNOWN";
    
    public static MCPServerHealth healthy(String serverId) {
        return MCPServerHealth.builder()
                .serverId(serverId)
                .connected(true)
                .status("HEALTHY")
                .lastCheck(Instant.now())
                .build();
    }
    
    public static MCPServerHealth unhealthy(String serverId, String errorMessage) {
        return MCPServerHealth.builder()
                .serverId(serverId)
                .connected(false)
                .status("UNHEALTHY")
                .errorMessage(errorMessage)
                .lastCheck(Instant.now())
                .build();
    }
}