package com.mcp.client.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * MCP 资源模型
 * 表示服务器提供的可读取数据资源
 * 符合 MCP 协议规范 2025-06-18
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MCPResource {
    
    /**
     * 资源URI - 资源的唯一标识符
     */
    @JsonProperty("uri")
    private String uri;
    
    /**
     * 资源名称 - 资源的名称
     */
    @JsonProperty("name")
    private String name;
    
    /**
     * 资源标题 - 可选的人类可读的资源显示名称
     */
    @JsonProperty("title")
    private String title;
    
    /**
     * 资源描述 - 可选的资源描述
     */
    @JsonProperty("description")
    private String description;
    
    /**
     * 资源MIME类型 - 可选的MIME类型
     */
    @JsonProperty("mimeType")
    private String mimeType;
    
    /**
     * 资源大小（字节）- 可选的资源大小
     */
    @JsonProperty("size")
    private Long size;
    
    /**
     * 资源注解 - 可选的元数据注解
     * 包含 audience、priority、lastModified 等信息
     */
    @JsonProperty("annotations")
    private ResourceAnnotations annotations;
    
    /**
     * 文本内容 - 当资源包含文本数据时使用
     */
    @JsonProperty("text")
    private String text;
    
    /**
     * 二进制内容 - 当资源包含二进制数据时使用（base64编码）
     */
    @JsonProperty("blob")
    private String blob;
    
    // 以下为客户端扩展字段，不参与JSON序列化
    
    /**
     * 所属服务器名称
     */
    private String serverName;
    
    /**
     * 资源是否可用
     */
    @Builder.Default
    private boolean available = true;
    
    /**
     * 资源注解类
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ResourceAnnotations {
        
        /**
         * 目标受众 - 指示资源的预期受众
         * 有效值: "user", "assistant"
         */
        @JsonProperty("audience")
        private String[] audience;
        
        /**
         * 优先级 - 0.0到1.0之间的数字，表示资源的重要性
         * 1表示最重要，0表示最不重要
         */
        @JsonProperty("priority")
        private Double priority;
        
        /**
         * 最后修改时间 - ISO 8601格式的时间戳
         */
        @JsonProperty("lastModified")
        private String lastModified;
    }
}