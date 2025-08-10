package com.mcp.host.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import jakarta.validation.constraints.NotBlank;
import java.time.Instant;

/**
 * MCP 工具执行结果模型
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MCPToolResult {
    
    @JsonProperty("success")
    private boolean success;
    
    @JsonProperty("result")
    private String result;
    
    @JsonProperty("error")
    private String error;
    
    @NotBlank(message = "工具名称不能为空")
    @JsonProperty("tool_name")
    private String toolName;
    
    @NotBlank(message = "服务器名称不能为空")
    @JsonProperty("server_name")
    private String serverName;
    
    @JsonProperty("timestamp")
    @Builder.Default
    private Instant timestamp = Instant.now();
    
    @JsonProperty("execution_time_ms")
    private Long executionTimeMs;
}