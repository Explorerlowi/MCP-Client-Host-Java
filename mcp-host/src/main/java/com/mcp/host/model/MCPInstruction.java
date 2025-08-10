package com.mcp.host.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.Map;

/**
 * MCP 指令模型
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MCPInstruction {
    
    @NotBlank(message = "指令类型不能为空")
    @JsonProperty("type")
    private String type;
    
    @NotBlank(message = "服务器名称不能为空")
    @JsonProperty("server_name")
    private String serverName;
    
    @NotBlank(message = "工具名称不能为空")
    @JsonProperty("tool_name")
    private String toolName;
    
    @NotNull(message = "参数不能为null")
    @JsonProperty("arguments")
    private Map<String, String> arguments;
}