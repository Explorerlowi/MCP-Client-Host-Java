package com.mcp.client.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * MCP 服务器能力模型
 * 表示服务器在初始化时声明的各种能力
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MCPServerCapabilities {
    
    /**
     * 工具能力
     */
    @JsonProperty("tools")
    private ToolsCapability tools;
    
    /**
     * 资源能力
     */
    @JsonProperty("resources")
    private ResourcesCapability resources;
    
    /**
     * 提示模板能力
     */
    @JsonProperty("prompts")
    private PromptsCapability prompts;
    
    /**
     * 日志能力
     */
    @JsonProperty("logging")
    private LoggingCapability logging;
    
    /**
     * 实验性能力
     */
    @JsonProperty("experimental")
    private Object experimental;
    
    /**
     * 补全能力（旧版实验特性）
     */
    @JsonProperty("completions")
    private Object completions;
    
    /**
     * 工具能力详细配置
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ToolsCapability {
        /**
         * 工具列表变化时是否推送通知
         */
        @JsonProperty("listChanged")
        @Builder.Default
        private Boolean listChanged = false;
    }
    
    /**
     * 资源能力详细配置
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ResourcesCapability {
        /**
         * 是否支持客户端订阅资源更新
         */
        @JsonProperty("subscribe")
        @Builder.Default
        private Boolean subscribe = false;
        
        /**
         * 资源列表变化时是否推送通知
         */
        @JsonProperty("listChanged")
        @Builder.Default
        private Boolean listChanged = false;
    }
    
    /**
     * 提示模板能力详细配置
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PromptsCapability {
        /**
         * 提示模板列表变化时是否推送通知
         */
        @JsonProperty("listChanged")
        @Builder.Default
        private Boolean listChanged = false;
    }
    
    /**
     * 日志能力详细配置
     */
    @Data
    @Builder
    @NoArgsConstructor
    public static class LoggingCapability {
        // 日志能力通常为空对象，表示支持日志功能
    }
    
    /**
     * 检查是否支持工具功能
     */
    public boolean supportsTools() {
        return tools != null;
    }
    
    /**
     * 检查是否支持资源功能
     */
    public boolean supportsResources() {
        return resources != null;
    }
    
    /**
     * 检查是否支持提示模板功能
     */
    public boolean supportsPrompts() {
        return prompts != null;
    }
    
    /**
     * 检查是否支持日志功能
     */
    public boolean supportsLogging() {
        return logging != null;
    }
    
    /**
     * 检查工具列表是否支持变化通知
     */
    public boolean supportsToolsListChanged() {
        return tools != null && Boolean.TRUE.equals(tools.getListChanged());
    }
    
    /**
     * 检查资源列表是否支持变化通知
     */
    public boolean supportsResourcesListChanged() {
        return resources != null && Boolean.TRUE.equals(resources.getListChanged());
    }
    
    /**
     * 检查是否支持资源订阅
     */
    public boolean supportsResourcesSubscribe() {
        return resources != null && Boolean.TRUE.equals(resources.getSubscribe());
    }
    
    /**
     * 检查提示模板列表是否支持变化通知
     */
    public boolean supportsPromptsListChanged() {
        return prompts != null && Boolean.TRUE.equals(prompts.getListChanged());
    }
}