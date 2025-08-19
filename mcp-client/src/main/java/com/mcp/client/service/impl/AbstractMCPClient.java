package com.mcp.client.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mcp.client.exception.McpConnectionException;
import com.mcp.client.model.McpServerSpec;
import com.mcp.client.model.MCPServerInfo;
import com.mcp.client.model.MCPServerCapabilities;
import com.mcp.client.model.MCPTool;
import com.mcp.client.model.MCPToolResult;
import com.mcp.client.model.MCPResource;
import com.mcp.client.model.MCPPrompt;
import com.mcp.client.service.MCPClient;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * MCP 客户端抽象基类
 * 提供通用的功能实现
 */
@Slf4j
public abstract class AbstractMCPClient implements MCPClient {
    
    protected final McpServerSpec spec;
    protected final ObjectMapper objectMapper;
    protected final AtomicBoolean connected = new AtomicBoolean(false);
    protected final AtomicLong requestIdCounter = new AtomicLong(1);
    
    // 服务器能力信息
    protected MCPServerCapabilities serverCapabilities;
    protected String protocolVersion;
    protected MCPServerInfo.ServerImplementationInfo serverImplementationInfo;
    
    protected AbstractMCPClient(McpServerSpec spec) {
        this.spec = spec;
        this.objectMapper = new ObjectMapper();
    }
    
    @Override
    public MCPServerInfo getServerInfo() {
        return MCPServerInfo.builder()
                .name(spec.getName() != null ? spec.getName() : spec.getId())
                .transport(spec.getTransport())
                .connected(isConnected())
                .description(spec.getDescription() != null ? spec.getDescription() : "MCP Server: " + spec.getId())
                .version(serverImplementationInfo != null ? serverImplementationInfo.getVersion() : "1.0.0")
                .capabilities(serverCapabilities)
                .protocolVersion(protocolVersion)
                .serverInfo(serverImplementationInfo)
                .build();
    }
    
    @Override
    public boolean isConnected() {
        return connected.get();
    }
    
    /**
     * 构建工具调用的 JSON-RPC 请求
     */
    protected JsonNode buildToolCallRequest(String toolName, Map<String, String> arguments) {
        ObjectNode request = objectMapper.createObjectNode();
        request.put("jsonrpc", "2.0");
        request.put("id", requestIdCounter.getAndIncrement());
        request.put("method", "tools/call");

        ObjectNode params = objectMapper.createObjectNode();
        params.put("name", toolName);

        // 始终包含 arguments 字段，即使没有参数也要包含空对象
        ObjectNode argsNode = objectMapper.createObjectNode();
        if (arguments != null && !arguments.isEmpty()) {
            arguments.forEach((key, value) -> {
                // 尝试解析参数值的实际类型
                if (value != null) {
                    // 首先检查是否为JSON数组或对象字符串
                    if ((value.startsWith("[") && value.endsWith("]")) || 
                        (value.startsWith("{") && value.endsWith("}"))) {
                        try {
                            // 尝试解析为JSON节点
                            JsonNode jsonNode = objectMapper.readTree(value);
                            argsNode.set(key, jsonNode);
                        } catch (Exception e) {
                            // 如果解析失败，作为字符串处理
                            argsNode.put(key, value);
                        }
                    } else {
                        // 尝试解析为数字
                        try {
                            if (value.contains(".")) {
                                // 浮点数
                                double doubleValue = Double.parseDouble(value);
                                argsNode.put(key, doubleValue);
                            } else {
                                // 整数
                                long longValue = Long.parseLong(value);
                                argsNode.put(key, longValue);
                            }
                        } catch (NumberFormatException e) {
                            // 尝试解析为布尔值
                            if ("true".equalsIgnoreCase(value) || "false".equalsIgnoreCase(value)) {
                                argsNode.put(key, Boolean.parseBoolean(value));
                            } else {
                                // 默认作为字符串
                                argsNode.put(key, value);
                            }
                        }
                    }
                } else {
                    argsNode.putNull(key);
                }
            });
        }
        // 无论是否有参数，都设置 arguments 字段
        params.set("arguments", argsNode);

        request.set("params", params);
        return request;
    }
    
    /**
     * 构建获取工具列表的 JSON-RPC 请求
     */
    protected JsonNode buildListToolsRequest() {
        ObjectNode request = objectMapper.createObjectNode();
        request.put("jsonrpc", "2.0");
        request.put("id", requestIdCounter.getAndIncrement());
        request.put("method", "tools/list");
        request.set("params", objectMapper.createObjectNode());
        return request;
    }
    
    /**
     * 构建初始化握手请求
     */
    protected JsonNode buildInitializeRequest() {
        ObjectNode request = objectMapper.createObjectNode();
        request.put("jsonrpc", "2.0");
        request.put("id", requestIdCounter.getAndIncrement());
        request.put("method", "initialize");
        
        ObjectNode params = objectMapper.createObjectNode();
        params.put("protocolVersion", "2024-11-05");
        
        ObjectNode clientInfo = objectMapper.createObjectNode();
        clientInfo.put("name", "mcp-java-client");
        clientInfo.put("version", "1.0.0");
        params.set("clientInfo", clientInfo);
        
        ObjectNode capabilities = objectMapper.createObjectNode();
        // capabilities.put("tools", true);
        params.set("capabilities", capabilities);
        
        request.set("params", params);
        return request;
    }

    /**
     * 构建初始化完成通知
     */
    protected JsonNode buildInitializedNotification() {
        ObjectNode notification = objectMapper.createObjectNode();
        notification.put("jsonrpc", "2.0");
        notification.put("method", "notifications/initialized");
        notification.set("params", objectMapper.createObjectNode());
        return notification;
    }
    
    /**
     * 构建获取资源列表的 JSON-RPC 请求
     */
    protected JsonNode buildListResourcesRequest() {
        ObjectNode request = objectMapper.createObjectNode();
        request.put("jsonrpc", "2.0");
        request.put("id", requestIdCounter.getAndIncrement());
        request.put("method", "resources/list");
        request.set("params", objectMapper.createObjectNode());
        return request;
    }
    
    /**
     * 构建读取资源的 JSON-RPC 请求
     */
    protected JsonNode buildReadResourceRequest(String uri) {
        ObjectNode request = objectMapper.createObjectNode();
        request.put("jsonrpc", "2.0");
        request.put("id", requestIdCounter.getAndIncrement());
        request.put("method", "resources/read");
        
        ObjectNode params = objectMapper.createObjectNode();
        params.put("uri", uri);
        request.set("params", params);
        
        return request;
    }
    
    /**
     * 构建获取提示模板列表的 JSON-RPC 请求
     */
    protected JsonNode buildListPromptsRequest() {
        ObjectNode request = objectMapper.createObjectNode();
        request.put("jsonrpc", "2.0");
        request.put("id", requestIdCounter.getAndIncrement());
        request.put("method", "prompts/list");
        request.set("params", objectMapper.createObjectNode());
        return request;
    }
    
    /**
     * 构建生成提示的 JSON-RPC 请求
     */
    protected JsonNode buildGeneratePromptRequest(String promptId, Map<String, String> arguments) {
        ObjectNode request = objectMapper.createObjectNode();
        request.put("jsonrpc", "2.0");
        request.put("id", requestIdCounter.getAndIncrement());
        request.put("method", "prompts/get");
        
        ObjectNode params = objectMapper.createObjectNode();
        params.put("name", promptId);
        
        // 添加参数
        if (arguments != null && !arguments.isEmpty()) {
            ObjectNode argsNode = objectMapper.createObjectNode();
            arguments.forEach(argsNode::put);
            params.set("arguments", argsNode);
        }
        
        request.set("params", params);
        return request;
    }

    /**
     * 解析工具调用结果
     */
    protected MCPToolResult parseToolResult(JsonNode response, String toolName) {
        try {
            if (response.has("error")) {
                JsonNode error = response.get("error");
                return MCPToolResult.builder()
                        .success(false)
                        .error(error.get("message").asText())
                        .toolName(toolName)
                        .serverName(spec.getId())
                        .build();
            }
            
            if (response.has("result")) {
                JsonNode result = response.get("result");
                return MCPToolResult.builder()
                        .success(true)
                        .result(result.toString())
                        .toolName(toolName)
                        .serverName(spec.getId())
                        .build();
            }
            
            return MCPToolResult.builder()
                    .success(false)
                    .error("无效的响应格式")
                    .toolName(toolName)
                    .serverName(spec.getId())
                    .build();
        } catch (Exception e) {
            log.error("解析工具调用结果失败", e);
            return MCPToolResult.builder()
                    .success(false)
                    .error("解析响应失败: " + e.getMessage())
                    .toolName(toolName)
                    .serverName(spec.getId())
                    .build();
        }
    }
    
    /**
     * 解析工具列表
     */
    protected List<MCPTool> parseToolsList(JsonNode response) {
        List<MCPTool> tools = new ArrayList<>();
        
        try {
            if (response.has("error")) {
                log.error("获取工具列表失败: {}", response.get("error").get("message").asText());
                return tools;
            }
            
            if (response.has("result") && response.get("result").has("tools")) {
                JsonNode toolsArray = response.get("result").get("tools");
                
                if (toolsArray.isArray()) {
                    for (JsonNode toolNode : toolsArray) {
                        try {
                            MCPTool tool = MCPTool.builder()
                                    .name(toolNode.get("name").asText())
                                    .description(toolNode.has("description") ? 
                                            toolNode.get("description").asText() : "")
                                    .inputSchema(toolNode.has("inputSchema") ? 
                                            toolNode.get("inputSchema").toString() : "{}")
                                    .serverName(spec.getId())
                                    .disabled(false)
                                    .build();
                            tools.add(tool);
                        } catch (Exception e) {
                            log.warn("解析工具信息失败: {}", toolNode, e);
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.error("解析工具列表失败", e);
        }
        
        return tools;
    }
    
    /**
     * 解析初始化响应，提取服务器能力信息
     */
    protected void parseInitializeResponse(JsonNode response) {
        try {
            if (response.has("result")) {
                JsonNode result = response.get("result");
                
                // 解析协议版本
                if (result.has("protocolVersion")) {
                    this.protocolVersion = result.get("protocolVersion").asText();
                }
                
                // 解析服务器能力
                if (result.has("capabilities")) {
                    JsonNode capabilitiesNode = result.get("capabilities");
                    this.serverCapabilities = parseServerCapabilities(capabilitiesNode);
                }
                
                // 解析服务器实现信息
                if (result.has("serverInfo")) {
                    JsonNode serverInfoNode = result.get("serverInfo");
                    this.serverImplementationInfo = MCPServerInfo.ServerImplementationInfo.builder()
                            .name(serverInfoNode.has("name") ? serverInfoNode.get("name").asText() : "Unknown")
                            .version(serverInfoNode.has("version") ? serverInfoNode.get("version").asText() : "1.0.0")
                            .build();
                }
                
                log.info("服务器 {} 初始化完成，协议版本: {}, 支持的能力: {}", 
                        spec.getId(), protocolVersion, getCapabilitiesSummary());
            }
        } catch (Exception e) {
            log.warn("解析初始化响应失败: {}", e.getMessage());
        }
    }
    
    /**
     * 解析服务器能力
     */
    private MCPServerCapabilities parseServerCapabilities(JsonNode capabilitiesNode) {
        MCPServerCapabilities.MCPServerCapabilitiesBuilder builder = MCPServerCapabilities.builder();
        
        // 解析工具能力
        if (capabilitiesNode.has("tools")) {
            JsonNode toolsNode = capabilitiesNode.get("tools");
            MCPServerCapabilities.ToolsCapability.ToolsCapabilityBuilder toolsBuilder = 
                    MCPServerCapabilities.ToolsCapability.builder();
            
            if (toolsNode.has("listChanged")) {
                toolsBuilder.listChanged(toolsNode.get("listChanged").asBoolean());
            }
            
            builder.tools(toolsBuilder.build());
        }
        
        // 解析资源能力
        if (capabilitiesNode.has("resources")) {
            JsonNode resourcesNode = capabilitiesNode.get("resources");
            MCPServerCapabilities.ResourcesCapability.ResourcesCapabilityBuilder resourcesBuilder = 
                    MCPServerCapabilities.ResourcesCapability.builder();
            
            if (resourcesNode.has("subscribe")) {
                resourcesBuilder.subscribe(resourcesNode.get("subscribe").asBoolean());
            }
            if (resourcesNode.has("listChanged")) {
                resourcesBuilder.listChanged(resourcesNode.get("listChanged").asBoolean());
            }
            
            builder.resources(resourcesBuilder.build());
        }
        
        // 解析提示模板能力
        if (capabilitiesNode.has("prompts")) {
            JsonNode promptsNode = capabilitiesNode.get("prompts");
            MCPServerCapabilities.PromptsCapability.PromptsCapabilityBuilder promptsBuilder = 
                    MCPServerCapabilities.PromptsCapability.builder();
            
            if (promptsNode.has("listChanged")) {
                promptsBuilder.listChanged(promptsNode.get("listChanged").asBoolean());
            }
            
            builder.prompts(promptsBuilder.build());
        }
        
        // 解析日志能力
        if (capabilitiesNode.has("logging")) {
            builder.logging(MCPServerCapabilities.LoggingCapability.builder().build());
        }
        
        // 解析实验性能力
        if (capabilitiesNode.has("experimental")) {
            builder.experimental(capabilitiesNode.get("experimental"));
        }
        
        // 解析补全能力（旧版）
        if (capabilitiesNode.has("completions")) {
            builder.completions(capabilitiesNode.get("completions"));
        }
        
        return builder.build();
    }
    
    /**
     * 获取能力摘要信息
     */
    private String getCapabilitiesSummary() {
        if (serverCapabilities == null) {
            return "未知";
        }
        
        List<String> capabilities = new ArrayList<>();
        if (serverCapabilities.supportsTools()) {
            capabilities.add("tools");
        }
        if (serverCapabilities.supportsResources()) {
            capabilities.add("resources");
        }
        if (serverCapabilities.supportsPrompts()) {
            capabilities.add("prompts");
        }
        if (serverCapabilities.supportsLogging()) {
            capabilities.add("logging");
        }
        
        return capabilities.isEmpty() ? "无" : String.join(", ", capabilities);
    }
    
    /**
     * 解析资源列表
     */
    protected List<MCPResource> parseResourcesList(JsonNode response) {
        List<MCPResource> resources = new ArrayList<>();
        
        try {
            if (response.has("error")) {
                log.error("获取资源列表失败: {}", response.get("error").get("message").asText());
                return resources;
            }
            
            if (response.has("result") && response.get("result").has("resources")) {
                JsonNode resourcesArray = response.get("result").get("resources");
                
                if (resourcesArray.isArray()) {
                    for (JsonNode resourceNode : resourcesArray) {
                        try {
                            MCPResource.MCPResourceBuilder builder = MCPResource.builder()
                                    .uri(resourceNode.has("uri") ? resourceNode.get("uri").asText() : "")
                                    .name(resourceNode.has("name") ? resourceNode.get("name").asText() : "")
                                    .title(resourceNode.has("title") ? resourceNode.get("title").asText() : null)
                                    .description(resourceNode.has("description") ? 
                                            resourceNode.get("description").asText() : null)
                                    .mimeType(resourceNode.has("mimeType") ? 
                                            resourceNode.get("mimeType").asText() : null)
                                    .size(resourceNode.has("size") ? resourceNode.get("size").asLong() : null)
                                    .text(resourceNode.has("text") ? resourceNode.get("text").asText() : null)
                                    .blob(resourceNode.has("blob") ? resourceNode.get("blob").asText() : null)
                                    .serverName(spec.getId());
                            
                            // 解析注解
                            if (resourceNode.has("annotations")) {
                                JsonNode annotationsNode = resourceNode.get("annotations");
                                MCPResource.ResourceAnnotations.ResourceAnnotationsBuilder annotationsBuilder = 
                                        MCPResource.ResourceAnnotations.builder();
                                
                                if (annotationsNode.has("audience") && annotationsNode.get("audience").isArray()) {
                                    JsonNode audienceArray = annotationsNode.get("audience");
                                    String[] audience = new String[audienceArray.size()];
                                    for (int i = 0; i < audienceArray.size(); i++) {
                                        audience[i] = audienceArray.get(i).asText();
                                    }
                                    annotationsBuilder.audience(audience);
                                }
                                
                                if (annotationsNode.has("priority")) {
                                    annotationsBuilder.priority(annotationsNode.get("priority").asDouble());
                                }
                                
                                if (annotationsNode.has("lastModified")) {
                                    annotationsBuilder.lastModified(annotationsNode.get("lastModified").asText());
                                }
                                
                                builder.annotations(annotationsBuilder.build());
                            }
                            
                            resources.add(builder.build());
                        } catch (Exception e) {
                            log.warn("解析资源信息失败: {}", resourceNode, e);
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.error("解析资源列表失败", e);
        }
        
        return resources;
    }
    
    /**
     * 解析提示模板列表
     */
    protected List<MCPPrompt> parsePromptsList(JsonNode response) {
        List<MCPPrompt> prompts = new ArrayList<>();
        
        try {
            if (response.has("error")) {
                log.error("获取提示模板列表失败: {}", response.get("error").get("message").asText());
                return prompts;
            }
            
            if (response.has("result") && response.get("result").has("prompts")) {
                JsonNode promptsArray = response.get("result").get("prompts");
                
                if (promptsArray.isArray()) {
                    for (JsonNode promptNode : promptsArray) {
                        try {
                            MCPPrompt.MCPPromptBuilder builder = MCPPrompt.builder()
                                    .name(promptNode.has("name") ? promptNode.get("name").asText() : "")
                                    .title(promptNode.has("title") ? promptNode.get("title").asText() : null)
                                    .description(promptNode.has("description") ? 
                                            promptNode.get("description").asText() : null)
                                    .serverName(spec.getId());
                            
                            // 解析参数列表
                            if (promptNode.has("arguments") && promptNode.get("arguments").isArray()) {
                                JsonNode argumentsArray = promptNode.get("arguments");
                                List<MCPPrompt.PromptArgument> arguments = new ArrayList<>();
                                
                                for (JsonNode argNode : argumentsArray) {
                                    MCPPrompt.PromptArgument argument = MCPPrompt.PromptArgument.builder()
                                            .name(argNode.has("name") ? argNode.get("name").asText() : null)
                                            .description(argNode.has("description") ? argNode.get("description").asText() : null)
                                            .required(argNode.has("required") ? argNode.get("required").asBoolean() : null)
                                            .build();
                                    arguments.add(argument);
                                }
                                
                                builder.arguments(arguments);
                            }
                            
                            prompts.add(builder.build());
                        } catch (Exception e) {
                            log.warn("解析提示模板信息失败: {}", promptNode, e);
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.error("解析提示模板列表失败", e);
        }
        
        return prompts;
    }
    
    /**
     * 解析资源内容
     */
    protected String parseResourceContent(JsonNode response) {
        try {
            if (response.has("error")) {
                log.error("读取资源失败: {}", response.get("error").get("message").asText());
                return "";
            }
            
            if (response.has("result") && response.get("result").has("contents")) {
                JsonNode contentsArray = response.get("result").get("contents");
                if (contentsArray.isArray() && contentsArray.size() > 0) {
                    JsonNode firstContent = contentsArray.get(0);
                    if (firstContent.has("text")) {
                        return firstContent.get("text").asText();
                    }
                }
            }
        } catch (Exception e) {
            log.error("解析资源内容失败", e);
        }
        
        return "";
    }
    
    /**
     * 解析生成的提示内容
     */
    /**
     * 解析完整的提示响应
     */
    protected MCPPrompt parsePromptResponse(JsonNode response) {
        try {
            if (response.has("error")) {
                log.error("获取提示失败: {}", response.get("error").get("message").asText());
                return null;
            }
            
            if (response.has("result")) {
                JsonNode result = response.get("result");
                MCPPrompt.MCPPromptBuilder builder = MCPPrompt.builder()
                        .description(result.has("description") ? result.get("description").asText() : null);
                
                // 解析消息列表
                if (result.has("messages") && result.get("messages").isArray()) {
                    JsonNode messagesArray = result.get("messages");
                    List<MCPPrompt.PromptMessage> messages = new ArrayList<>();
                    
                    for (JsonNode messageNode : messagesArray) {
                        MCPPrompt.PromptMessage message = parsePromptMessage(messageNode);
                        if (message != null) {
                            messages.add(message);
                        }
                    }
                    
                    builder.messages(messages);
                }
                
                return builder.build();
            }
        } catch (Exception e) {
            log.error("解析提示响应失败", e);
        }
        
        return null;
    }
    
    /**
     * 解析单个提示消息
     */
    protected MCPPrompt.PromptMessage parsePromptMessage(JsonNode messageNode) {
        try {
            MCPPrompt.PromptMessage.PromptMessageBuilder builder = MCPPrompt.PromptMessage.builder()
                    .role(messageNode.has("role") ? messageNode.get("role").asText() : null);
            
            if (messageNode.has("content")) {
                JsonNode contentNode = messageNode.get("content");
                MCPPrompt.PromptContent content = parsePromptContent(contentNode);
                builder.content(content);
            }
            
            return builder.build();
        } catch (Exception e) {
            log.warn("解析提示消息失败: {}", messageNode, e);
            return null;
        }
    }
    
    /**
     * 解析提示内容
     */
    protected MCPPrompt.PromptContent parsePromptContent(JsonNode contentNode) {
        try {
            MCPPrompt.PromptContent.PromptContentBuilder builder = MCPPrompt.PromptContent.builder()
                    .type(contentNode.has("type") ? contentNode.get("type").asText() : "text");
            
            String type = contentNode.has("type") ? contentNode.get("type").asText() : "text";
            
            switch (type) {
                case "text":
                    builder.text(contentNode.has("text") ? contentNode.get("text").asText() : null);
                    break;
                case "image":
                case "audio":
                    builder.data(contentNode.has("data") ? contentNode.get("data").asText() : null)
                           .mimeType(contentNode.has("mimeType") ? contentNode.get("mimeType").asText() : null);
                    break;
                case "resource":
                    if (contentNode.has("resource")) {
                        // 这里可以解析嵌入的资源，暂时简化处理
                        JsonNode resourceNode = contentNode.get("resource");
                        MCPResource resource = MCPResource.builder()
                                .uri(resourceNode.has("uri") ? resourceNode.get("uri").asText() : null)
                                .name(resourceNode.has("name") ? resourceNode.get("name").asText() : null)
                                .title(resourceNode.has("title") ? resourceNode.get("title").asText() : null)
                                .mimeType(resourceNode.has("mimeType") ? resourceNode.get("mimeType").asText() : null)
                                .text(resourceNode.has("text") ? resourceNode.get("text").asText() : null)
                                .blob(resourceNode.has("blob") ? resourceNode.get("blob").asText() : null)
                                .build();
                        builder.resource(resource);
                    }
                    break;
            }
            
            // 解析注解
            if (contentNode.has("annotations")) {
                JsonNode annotationsNode = contentNode.get("annotations");
                MCPResource.ResourceAnnotations.ResourceAnnotationsBuilder annotationsBuilder = 
                        MCPResource.ResourceAnnotations.builder();
                
                if (annotationsNode.has("audience") && annotationsNode.get("audience").isArray()) {
                    JsonNode audienceArray = annotationsNode.get("audience");
                    String[] audience = new String[audienceArray.size()];
                    for (int i = 0; i < audienceArray.size(); i++) {
                        audience[i] = audienceArray.get(i).asText();
                    }
                    annotationsBuilder.audience(audience);
                }
                
                if (annotationsNode.has("priority")) {
                    annotationsBuilder.priority(annotationsNode.get("priority").asDouble());
                }
                
                if (annotationsNode.has("lastModified")) {
                    annotationsBuilder.lastModified(annotationsNode.get("lastModified").asText());
                }
                
                builder.annotations(annotationsBuilder.build());
            }
            
            return builder.build();
        } catch (Exception e) {
            log.warn("解析提示内容失败: {}", contentNode, e);
            return null;
        }
    }
    
    /**
     * 解析生成的提示内容（向后兼容）
     */
    protected String parseGeneratedPrompt(JsonNode response) {
        MCPPrompt prompt = parsePromptResponse(response);
        if (prompt != null && prompt.getMessages() != null && !prompt.getMessages().isEmpty()) {
            MCPPrompt.PromptMessage firstMessage = prompt.getMessages().get(0);
            if (firstMessage.getContent() != null && firstMessage.getContent().getText() != null) {
                return firstMessage.getContent().getText();
            }
        }
        return "";
    }
    
    /**
     * 实现资源列表获取（子类可重写）
     */
    @Override
    public List<MCPResource> getResources() {
        if (serverCapabilities == null || !serverCapabilities.supportsResources()) {
            return List.of();
        }
        
        try {
            JsonNode request = buildListResourcesRequest();
            JsonNode response = sendRequest(request);
            return parseResourcesList(response);
        } catch (Exception e) {
            log.error("获取资源列表失败", e);
            return List.of();
        }
    }
    
    /**
     * 实现资源读取（子类可重写）
     */
    @Override
    public String readResource(String uri) {
        if (serverCapabilities == null || !serverCapabilities.supportsResources()) {
            throw new UnsupportedOperationException("服务器不支持资源功能");
        }
        
        try {
            JsonNode request = buildReadResourceRequest(uri);
            JsonNode response = sendRequest(request);
            return parseResourceContent(response);
        } catch (Exception e) {
            log.error("读取资源失败: {}", uri, e);
            return "";
        }
    }
    
    /**
     * 实现提示模板列表获取（子类可重写）
     */
    @Override
    public List<MCPPrompt> getPrompts() {
        if (serverCapabilities == null || !serverCapabilities.supportsPrompts()) {
            return List.of();
        }
        
        try {
            JsonNode request = buildListPromptsRequest();
            JsonNode response = sendRequest(request);
            return parsePromptsList(response);
        } catch (Exception e) {
            log.error("获取提示模板列表失败", e);
            return List.of();
        }
    }
    
    /**
     * 实现提示生成（子类可重写）
     */
    @Override
    public String generatePrompt(String promptId, Map<String, String> arguments) {
        if (serverCapabilities == null || !serverCapabilities.supportsPrompts()) {
            throw new UnsupportedOperationException("服务器不支持提示模板功能");
        }
        
        try {
            JsonNode request = buildGeneratePromptRequest(promptId, arguments);
            JsonNode response = sendRequest(request);
            return parseGeneratedPrompt(response);
        } catch (Exception e) {
            log.error("生成提示失败: {}", promptId, e);
            return "";
        }
    }
    
    @Override
    public MCPPrompt getPrompt(String promptName, Map<String, String> arguments) {
        if (serverCapabilities == null || !serverCapabilities.supportsPrompts()) {
            throw new UnsupportedOperationException("服务器不支持提示模板功能");
        }
        
        try {
            JsonNode request = buildGeneratePromptRequest(promptName, arguments);
            JsonNode response = sendRequest(request);
            return parsePromptResponse(response);
        } catch (Exception e) {
            log.error("获取提示失败: {}", promptName, e);
            return null;
        }
    }
    
    /**
     * 验证 JSON-RPC 响应
     */
    protected void validateResponse(JsonNode response) throws McpConnectionException {
        if (response == null) {
            throw new McpConnectionException("收到空响应");
        }
        
        if (!response.has("jsonrpc") || !"2.0".equals(response.get("jsonrpc").asText())) {
            throw new McpConnectionException("无效的 JSON-RPC 响应");
        }
        
        if (response.has("error")) {
            JsonNode error = response.get("error");
            String message = error.has("message") ? error.get("message").asText() : "未知错误";
            throw new McpConnectionException("服务器返回错误: " + message);
        }
    }
    
    /**
     * 执行握手协议
     */
    protected abstract void performHandshake() throws McpConnectionException;
    
    /**
     * 发送 JSON-RPC 请求并接收响应
     */
    protected abstract JsonNode sendRequest(JsonNode request) throws McpConnectionException;
}