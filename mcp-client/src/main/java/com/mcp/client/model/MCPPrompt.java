package com.mcp.client.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * MCP 提示模板模型
 * 表示服务器提供的结构化消息和指令模板
 * 符合 MCP 协议规范 2025-06-18
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MCPPrompt {
    
    /**
     * 提示模板名称 - 提示的唯一标识符
     */
    @JsonProperty("name")
    private String name;
    
    /**
     * 提示模板标题 - 可选的人类可读的提示显示名称
     */
    @JsonProperty("title")
    private String title;
    
    /**
     * 提示模板描述 - 可选的人类可读的描述
     */
    @JsonProperty("description")
    private String description;
    
    /**
     * 参数列表 - 用于自定义的可选参数列表
     */
    @JsonProperty("arguments")
    private List<PromptArgument> arguments;
    
    /**
     * 消息列表 - 提示的实际内容（仅在获取提示时返回）
     */
    @JsonProperty("messages")
    private List<PromptMessage> messages;
    
    // 以下为客户端扩展字段，不参与JSON序列化
    
    /**
     * 所属服务器名称
     */
    private String serverName;
    
    /**
     * 提示模板是否启用
     */
    @Builder.Default
    private boolean enabled = true;
    
    /**
     * 使用次数统计
     */
    @Builder.Default
    private Long usageCount = 0L;
    
    /**
     * 提示模板类别
     */
    private String category;
    
    /**
     * 提示参数类
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PromptArgument {
        
        /**
         * 参数名称
         */
        @JsonProperty("name")
        private String name;
        
        /**
         * 参数描述
         */
        @JsonProperty("description")
        private String description;
        
        /**
         * 是否必需参数
         */
        @JsonProperty("required")
        private Boolean required;
    }
    
    /**
     * 提示消息类
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PromptMessage {
        
        /**
         * 消息角色 - "user" 或 "assistant"
         */
        @JsonProperty("role")
        private String role;
        
        /**
         * 消息内容
         */
        @JsonProperty("content")
        private PromptContent content;
    }
    
    /**
     * 提示内容基类
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PromptContent {
        
        /**
         * 内容类型 - "text", "image", "audio", "resource"
         */
        @JsonProperty("type")
        private String type;
        
        /**
         * 文本内容 - 当type为"text"时使用
         */
        @JsonProperty("text")
        private String text;
        
        /**
         * 数据内容 - 当type为"image"或"audio"时使用（base64编码）
         */
        @JsonProperty("data")
        private String data;
        
        /**
         * MIME类型 - 当type为"image"或"audio"时使用
         */
        @JsonProperty("mimeType")
        private String mimeType;
        
        /**
         * 嵌入资源 - 当type为"resource"时使用
         */
        @JsonProperty("resource")
        private MCPResource resource;
        
        /**
         * 内容注解 - 可选的元数据注解
         */
        @JsonProperty("annotations")
        private MCPResource.ResourceAnnotations annotations;
    }
}