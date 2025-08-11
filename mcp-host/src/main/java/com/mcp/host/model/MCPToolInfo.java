package com.mcp.host.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import jakarta.validation.constraints.NotBlank;

/**
 * MCP 工具信息模型（用于前端显示）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MCPToolInfo {
    
    @NotBlank(message = "工具名称不能为空")
    @JsonProperty("name")
    private String name;
    
    @JsonProperty("description")
    private String description;
    
    @NotBlank(message = "服务器名称不能为空")
    @JsonProperty("server_name")
    private String serverName;
    
    @JsonProperty("input_schema")
    private String inputSchema;
    
    @JsonProperty("output_schema")
    private String outputSchema;
    
    @JsonProperty("disabled")
    @Builder.Default
    private boolean disabled = false;
    
    @JsonProperty("category")
    private String category;
    
    @JsonProperty("usage_count")
    @Builder.Default
    private Long usageCount = 0L;
}