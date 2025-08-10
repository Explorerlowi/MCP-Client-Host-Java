package com.mcp.client.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.mcp.client.exception.McpConnectionException;
import com.mcp.client.model.McpServerSpec;
import com.mcp.client.model.MCPTool;
import com.mcp.client.model.MCPToolResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.reactive.function.client.ClientResponse;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Streamable HTTP 传输协议的 MCP 客户端实现
 * 通过统一端点支持双向通信和会话管理
 */
@Slf4j
public class MCPStreamableHttpClient extends AbstractMCPClient {
    
    private final WebClient webClient;
    private String sessionId; // 改为非final，初始为null，由服务端返回
    private Flux<String> eventStream;
    private volatile long lastEventId = 0;
    private volatile boolean supportsResume = false;
    
    public MCPStreamableHttpClient(McpServerSpec spec) {
        super(spec);
        this.sessionId = null; // 初始化为null，等待服务端返回
        this.webClient = WebClient.builder()
                .baseUrl(spec.getUrl())
                .defaultHeader("Origin", "http://localhost")
                .defaultHeader("User-Agent", "mcp-java-client/1.0.0")
                .build();

        establishConnection();
    }
    
    /**
     * 计算请求 URI，处理 baseUrl 包含 /mcp 的情况
     */
    private String calculateRequestUri() {
        String baseUrl = spec.getUrl();
        if (baseUrl.endsWith("/mcp")) {
            // 如果 baseUrl 已包含 /mcp，则直接使用根路径
            return "";
        } else {
            // 否则使用 /mcp 端点
            return "/mcp";
        }
    }
    
    /**
     * 建立 Streamable HTTP 连接
     */
    private void establishConnection() {
        establishConnection(false);
    }
    
    /**
     * 建立 Streamable HTTP 连接
     * @param resume 是否尝试断点续传
     */
    private void establishConnection(boolean resume) {
        try {
            log.info("建立 MCP Streamable HTTP 连接: {} (session: {})", spec.getUrl(), sessionId);

            // 构建 SSE 连接请求 - 使用计算的端点建立事件流
            WebClient.RequestHeadersSpec<?> requestSpec = webClient.get()
                    .uri(calculateRequestUri())
                    .header("Accept", "text/event-stream");

            // 如果已有sessionId，添加到请求头中
            if (sessionId != null) {
                requestSpec = requestSpec.header("Mcp-Session-Id", sessionId);
            }

            // 如果支持断点续传且有上次的事件ID，添加 Last-Event-ID 头
            if (resume && supportsResume && lastEventId > 0) {
                requestSpec = requestSpec.header("Last-Event-ID", String.valueOf(lastEventId));
                log.info("尝试断点续传，从事件ID {} 开始 [{}]", lastEventId, spec.getId());
            }
            
            // 尝试建立 SSE 连接用于接收服务器推送（可选）
            try {
                this.eventStream = requestSpec
                        .retrieve()
                        .bodyToFlux(String.class)
                        .doOnNext(this::handleServerEvent)
                        .doOnError(error -> {
                            if (error instanceof org.springframework.web.reactive.function.client.WebClientResponseException.MethodNotAllowed) {
                                log.info("服务器不支持 GET 方法，跳过 SSE 连接建立 [{}]", spec.getId());
                            } else {
                                handleConnectionError(error);
                            }
                        })
                        .doOnComplete(() -> {
                            log.info("Streamable HTTP 连接已关闭: {}", spec.getId());
                        });

                // 订阅事件流（如果服务器支持）
                eventStream.subscribe();
                log.debug("SSE 事件流已建立 [{}]", spec.getId());

            } catch (Exception e) {
                log.info("无法建立 SSE 连接，将使用仅 POST 模式 [{}]: {}", spec.getId(), e.getMessage());
                this.eventStream = null;
            }
            
            // 执行握手协议
            performHandshake();
            
            connected.set(true);
            log.info("MCP Streamable HTTP 客户端初始化成功: {}", spec.getId());
            
        } catch (Exception e) {
            log.error("初始化 MCP Streamable HTTP 客户端失败: {}", spec.getId(), e);
            throw new McpConnectionException("初始化 Streamable HTTP 客户端失败", e);
        }
    }
    
    /**
     * 处理服务器事件
     */
    private void handleServerEvent(String event) {
        try {
            log.debug("收到 Streamable HTTP 事件 [{}]: {}", spec.getId(), event);
            
            // 检查事件ID以支持断点续传
            if (event.startsWith("id: ")) {
                String eventIdStr = event.substring(4).trim();
                try {
                    lastEventId = Long.parseLong(eventIdStr);
                } catch (NumberFormatException e) {
                    log.debug("无法解析事件ID: {}", eventIdStr);
                }
                return;
            }
            
            // 处理服务器推送的事件
            if (event.startsWith("data: ")) {
                String jsonData = event.substring(6);
                JsonNode eventData = objectMapper.readTree(jsonData);
                
                // sessionId 从HTTP响应头获取，不从事件数据中获取

                // 检查服务器是否支持断点续传
                if (eventData.has("capabilities") && eventData.get("capabilities").has("resume")) {
                    supportsResume = eventData.get("capabilities").get("resume").asBoolean();
                    log.debug("服务器断点续传支持: {}", supportsResume);
                }
                
                // 处理不同类型的事件
                if (eventData.has("type")) {
                    String eventType = eventData.get("type").asText();
                    switch (eventType) {
                        case "notification":
                            handleNotification(eventData);
                            break;
                        case "progress":
                            handleProgress(eventData);
                            break;
                        case "resume":
                            handleResumeEvent(eventData);
                            break;
                        default:
                            log.debug("未知事件类型 [{}]: {}", spec.getId(), eventType);
                    }
                }
            }
        } catch (Exception e) {
            log.error("处理 Streamable HTTP 事件失败 [{}]: {}", spec.getId(), event, e);
        }
    }
    
    /**
     * 处理断点续传事件
     */
    private void handleResumeEvent(JsonNode eventData) {
        if (eventData.has("last_event_id")) {
            long serverLastEventId = eventData.get("last_event_id").asLong();
            log.info("服务器确认断点续传，最后事件ID: {} [{}]", serverLastEventId, spec.getId());
            
            if (serverLastEventId != lastEventId) {
                log.warn("事件ID不匹配，服务器: {}, 客户端: {} [{}]", 
                        serverLastEventId, lastEventId, spec.getId());
            }
        }
    }
    
    /**
     * 处理通知事件
     */
    private void handleNotification(JsonNode eventData) {
        log.info("收到服务器通知 [{}]: {}", spec.getId(), eventData.get("message").asText(""));
    }
    
    /**
     * 处理进度事件
     */
    private void handleProgress(JsonNode eventData) {
        if (eventData.has("progress")) {
            int progress = eventData.get("progress").asInt();
            log.debug("任务进度 [{}]: {}%", spec.getId(), progress);
        }
    }
    
    /**
     * 处理连接错误
     */
    private void handleConnectionError(Throwable error) {
        log.error("Streamable HTTP 连接错误 [{}]: {}", spec.getId(), error.getMessage());
        connected.set(false);
    }
    
    @Override
    protected void performHandshake() throws McpConnectionException {
        try {
            log.debug("开始 MCP Streamable HTTP 握手协议: {}", spec.getId());

            JsonNode initRequest = buildInitializeRequest();
            JsonNode initResponse = sendRequest(initRequest);

            // sessionId 应该从HTTP响应头中获取，而不是响应体
            // 这里暂时保留，实际的sessionId提取在sendRequest方法中处理

            validateResponse(initResponse);
            
            // 解析服务器能力信息
            parseInitializeResponse(initResponse);

            // 发送 notifications/initialized 通知
            JsonNode initializedNotification = buildInitializedNotification();
            try {
                sendRequest(initializedNotification);
                log.debug("已发送 notifications/initialized 通知 [{}]", spec.getId());
            } catch (McpConnectionException e) {
                // 对于通知，某些服务器可能不返回响应，这是正常的
                log.debug("发送 notifications/initialized 通知时收到异常，但这可能是正常的 [{}]: {}", spec.getId(), e.getMessage());
            }

            log.debug("MCP Streamable HTTP 握手协议完成: {}", spec.getId());

        } catch (Exception e) {
            log.error("MCP Streamable HTTP 握手协议失败: {}", spec.getId(), e);
            throw new McpConnectionException("Streamable HTTP 握手协议失败", e);
        }
    }
    
    @Override
    protected JsonNode sendRequest(JsonNode request) throws McpConnectionException {
        // 对于仅支持 POST 的服务器，不需要检查 SSE 连接状态
        // 只要能发送 POST 请求就认为连接正常
        
        try {
            log.debug("发送 MCP Streamable HTTP 请求 [{}]: {}", spec.getId(), request.toString());
            
            // 发送 POST 请求
            WebClient.RequestBodySpec requestSpec = webClient.post()
                    .uri(calculateRequestUri())
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Accept", "application/json, text/event-stream");

            // 如果已有sessionId，添加到请求头中
            if (sessionId != null) {
                requestSpec = requestSpec.header("Mcp-Session-Id", sessionId);
            }

            // 使用 exchange() 方法以便访问响应头
            ClientResponse response = requestSpec
                    .bodyValue(request.toString())
                    .exchange()
                    .timeout(Duration.ofSeconds(spec.getTimeout() != null ? spec.getTimeout() : 60L))
                    .block();

            if (response == null) {
                throw new McpConnectionException("收到空响应");
            }

            // 从响应头中提取 sessionId
            if (sessionId == null) {
                String headerSessionId = response.headers().header("Mcp-Session-Id").stream().findFirst().orElse(null);
                if (headerSessionId != null) {
                    sessionId = headerSessionId;
                    log.info("从HTTP响应头获取到sessionId: {} [{}]", sessionId, spec.getId());
                }
            }

            String responseStr = response.bodyToMono(String.class).block();
            if (responseStr == null || responseStr.trim().isEmpty()) {
                // 对于通知请求，空响应体是正常的
                if (request.has("method") && request.get("method").asText().startsWith("notifications/")) {
                    log.debug("通知请求收到空响应，这是正常的 [{}]", spec.getId());
                    return objectMapper.createObjectNode(); // 返回空的 JSON 对象
                }
                throw new McpConnectionException("收到空响应体");
            }

            log.debug("收到 MCP Streamable HTTP 响应 [{}]: {}", spec.getId(), responseStr);

            return objectMapper.readTree(responseStr);
            
        } catch (WebClientResponseException e) {
            log.error("HTTP 请求失败 [{}]: {} - {}", spec.getId(), e.getStatusCode(), e.getResponseBodyAsString());
            throw new McpConnectionException("HTTP 请求失败: " + e.getStatusCode(), e);
        } catch (Exception e) {
            log.error("发送 MCP Streamable HTTP 请求失败 [{}]", spec.getId(), e);
            throw new McpConnectionException("发送请求失败", e);
        }
    }
    
    @Override
    public MCPToolResult callTool(String toolName, Map<String, String> arguments) {
        try {
            log.debug("调用 MCP Streamable HTTP 工具 [{}]: {} with args: {}", spec.getId(), toolName, arguments);
            
            JsonNode request = buildToolCallRequest(toolName, arguments);
            JsonNode response = sendRequest(request);
            
            MCPToolResult result = parseToolResult(response, toolName);
            log.debug("MCP Streamable HTTP 工具调用结果 [{}]: success={}", spec.getId(), result.isSuccess());
            
            return result;
            
        } catch (Exception e) {
            log.error("调用 MCP Streamable HTTP 工具失败 [{}]: {}", spec.getId(), toolName, e);
            return MCPToolResult.builder()
                    .success(false)
                    .error("工具调用失败: " + e.getMessage())
                    .toolName(toolName)
                    .serverName(spec.getId())
                    .build();
        }
    }
    
    @Override
    public List<MCPTool> getTools() {
        try {
            log.debug("获取 MCP Streamable HTTP 工具列表: {}", spec.getId());
            
            JsonNode request = buildListToolsRequest();
            JsonNode response = sendRequest(request);
            
            List<MCPTool> tools = parseToolsList(response);
            log.debug("获取到 {} 个 MCP Streamable HTTP 工具: {}", tools.size(), spec.getId());
            
            return tools;
            
        } catch (Exception e) {
            log.error("获取 MCP Streamable HTTP 工具列表失败: {}", spec.getId(), e);
            return List.of();
        }
    }
    
    /**
     * 获取当前会话ID
     */
    public String getSessionId() {
        return sessionId;
    }

    /**
     * 检查会话状态
     */
    public boolean isSessionValid() {
        try {
            // 如果没有sessionId，认为会话无效
            if (sessionId == null) {
                return false;
            }

            // 发送心跳请求检查会话状态 - 使用计算的端点
            webClient.get()
                    .uri(calculateRequestUri())
                    .header("Mcp-Session-Id", sessionId)
                    .header("Accept", "application/json")
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(5))
                    .block();
            return true;
        } catch (Exception e) {
            log.debug("会话状态检查失败 [{}]: {}", spec.getId(), e.getMessage());
            return false;
        }
    }
    
    /**
     * 重新建立连接
     */
    public void reconnect() {
        reconnect(false);
    }
    
    /**
     * 重新建立连接
     * @param withResume 是否尝试断点续传
     */
    public void reconnect(boolean withResume) {
        log.info("重新建立 MCP Streamable HTTP 连接: {} (断点续传: {})", spec.getId(), withResume);
        connected.set(false);
        
        try {
            // 关闭现有连接（但保留会话状态用于断点续传）
            if (!withResume) {
                close();
            } else {
                // 只关闭事件流，保留会话信息
                if (eventStream != null) {
                    // WebClient 会自动清理资源
                }
            }
            
            // 重新建立连接，可能使用断点续传
            establishConnection(withResume);
        } catch (Exception e) {
            log.error("重新连接失败 [{}]", spec.getId(), e);
            throw new McpConnectionException("重新连接失败", e);
        }
    }
    
    @Override
    public void connect() {
        if (isConnected()) {
            log.debug("MCP Streamable HTTP 客户端已连接: {}", spec.getId());
            return;
        }

        log.info("连接 MCP Streamable HTTP 客户端: {}", spec.getId());
        establishConnection();
    }

    @Override
    public void disconnect() {
        if (!isConnected()) {
            log.debug("MCP Streamable HTTP 客户端已断开: {}", spec.getId());
            return;
        }

        log.info("断开 MCP Streamable HTTP 客户端连接: {}", spec.getId());
        connected.set(false);

        try {
            // 对于简化的 Streamable HTTP 实现，可能不需要特殊的暂停端点
            // 只需要关闭事件流连接即可
            log.debug("断开事件流连接 [{}]", spec.getId());
        } catch (Exception e) {
            log.debug("断开连接时出错 [{}]: {}", spec.getId(), e.getMessage());
        }

        // 关闭事件流
        if (eventStream != null) {
            // WebClient 会自动清理资源
        }
    }

    @Override
    public boolean isConnected() {
        // 对于仅支持 POST 的服务器，只要握手成功就认为连接正常
        return connected.get();
    }

    @Override
    public void close() {
        log.info("关闭 MCP Streamable HTTP 客户端: {}", spec.getId());
        connected.set(false);

        try {
            // 对于简化的 Streamable HTTP 实现，可能不需要特殊的会话结束请求
            // 只需要关闭连接即可让服务端知道会话结束
            log.debug("关闭会话连接 [{}]", spec.getId());
        } catch (Exception e) {
            log.debug("关闭会话时出错 [{}]: {}", spec.getId(), e.getMessage());
        }

        // 关闭事件流
        if (eventStream != null) {
            // WebClient 会自动清理资源
        }

        // 清空sessionId
        sessionId = null;
    }
}