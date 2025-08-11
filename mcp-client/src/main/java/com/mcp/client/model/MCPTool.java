package com.mcp.client.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * MCP 工具模型
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MCPTool {
    
    /**
     * 工具名称
     */
    private String name;
    
    /**
     * 工具描述
     */
    private String description;
    
    /**
     * 所属服务器名称
     */
    private String serverName;
    
    /**
     * 输入参数模式（JSON Schema）
     */
    private String inputSchema;
    
    /**
     * 输出结果模式（JSON Schema）
     */
    private String outputSchema;
    
    /**
     * 工具是否禁用
     */
    @Builder.Default
    private boolean disabled = false;
    
    /**
     * 工具类别
     */
    private String category;
    
    /**
     * 使用次数统计
     */
    @Builder.Default
    private Long usageCount = 0L;
}