package com.mcp.client.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.mcp.client.exception.McpConnectionException;
import com.mcp.client.model.McpServerSpec;
import com.mcp.client.model.MCPTool;
import com.mcp.client.model.MCPToolResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * SSE (Server-Sent Events) 传输协议的 MCP 客户端实现
 * 符合 MCP 协议版本 2024-11-05 规范：
 * 1. 建立 SSE 连接到 /sse 端点
 * 2. 从第一条 SSE 消息中获取 POST 消息的专用 URI
 * 3. 通过 SSE 接收服务器消息，通过 POST 发送客户端消息
 */
@Slf4j
public class MCPSseClient extends AbstractMCPClient {

    private final WebClient webClient;
    private final Map<Long, CompletableFuture<JsonNode>> pendingRequests = new ConcurrentHashMap<>();
    private Flux<String> eventStream;
    private volatile boolean shouldReconnect = true;
    private volatile String messageEndpointUri; // 用于 POST 消息的专用 URI
    private volatile CompletableFuture<Void> connectionReadyFuture = new CompletableFuture<>(); // 连接就绪信号
    private volatile int reconnectAttempts = 0; // 重连尝试次数
    private volatile boolean isReconnecting = false; // 防止并发重连
    private static final int MAX_RETRY_ATTEMPTS = 3;
    private static final long RETRY_DELAY_MS = 1000;
    // 连接超时时间从配置中获取，默认30秒
    
    public MCPSseClient(McpServerSpec spec) {
        super(spec);
        this.webClient = WebClient.builder()
                .baseUrl(spec.getUrl())
                .defaultHeader("Accept", "text/event-stream")
                .defaultHeader("Cache-Control", "no-cache")
                .build();

        initializeConnection();
    }
    
    /**
     * 初始化 SSE 连接
     * 按照 MCP 协议规范建立连接：
     * 1. 连接到 /sse 端点建立 SSE 长连接
     * 2. 等待服务器发送 endpoint 事件，获取 POST 消息的专用 URI
     * 3. 执行初始化握手协议
     */
    private void initializeConnection() {
        try {
            log.info("建立 MCP SSE 连接: {}", spec.getUrl());

            // 确定 SSE 端点 URI
            String sseUri = determineSseUri();
            log.debug("使用 SSE 端点: {}", sseUri);

            // 建立 SSE 连接
            this.eventStream = webClient.get()
                    .uri(sseUri)
                    .retrieve()
                    .bodyToFlux(String.class)
                    .doOnNext(this::handleServerEvent)
                    .doOnError(this::handleConnectionError)
                    .doOnComplete(() -> {
                        log.info("SSE 连接已关闭: {}", spec.getId());
                        connected.set(false);
                    });

            // 订阅事件流
            eventStream.subscribe();

            log.info("MCP SSE 客户端初始化成功: {}", spec.getId());

        } catch (Exception e) {
            log.error("初始化 MCP SSE 客户端失败: {}", spec.getId(), e);
            connected.set(false); // 确保连接状态正确
            throw new McpConnectionException("初始化 SSE 客户端失败", e);
        }
    }

    /**
     * 确定 SSE 端点 URI
     * 如果 URL 已经包含 SSE 路径，则直接使用空路径；否则添加 /sse
     */
    private String determineSseUri() {
        String url = spec.getUrl();
        if (url.endsWith("/sse")) {
            // URL 已经包含 /sse 路径，直接使用空路径
            return "";
        } else {
            // URL 不包含 /sse 路径，添加 /sse
            return "/sse";
        }
    }

    /**
     * 处理服务器事件
     * 按照 MCP 协议规范处理 SSE 事件：
     * 1. endpoint 事件：包含用于 POST 消息的专用 URI
     * 2. message 事件：包含服务器响应消息
     */
    private void handleServerEvent(String event) {
        try {
            log.debug("收到 SSE 事件 [{}]: {}", spec.getId(), event);

            // 解析 SSE 事件格式
            String eventType = null;
            String eventData = null;

            String[] lines = event.split("\n");
            for (String line : lines) {
                if (line.startsWith("event: ")) {
                    eventType = line.substring(7).trim();
                } else if (line.startsWith("data: ")) {
                    eventData = line.substring(6).trim();
                }
            }

            if (eventType != null && eventData != null) {
                handleTypedEvent(eventType, eventData);
            } else if (event.startsWith("data: ")) {
                // 兼容没有明确事件类型的情况
                String jsonData = event.substring(6);
                handleMessageEvent(jsonData);
            } else if (event.trim().startsWith("/")) {
                // 兼容直接发送 URI 的情况（某些 MCP 服务器实现）
                log.debug("检测到直接 URI 格式的 endpoint 事件 [{}]: {}", spec.getId(), event.trim());
                handleDirectEndpointEvent(event.trim());
            } else if (event.trim().startsWith("{")) {
                // 兼容直接发送 JSON 的情况（某些 MCP 服务器实现）
                log.debug("检测到直接 JSON 格式的 message 事件 [{}]: {}", spec.getId(), event.trim());
                handleMessageEvent(event.trim());
            }
        } catch (Exception e) {
            log.error("处理 SSE 事件失败 [{}]: {}", spec.getId(), event, e);
        }
    }

    /**
     * 处理特定类型的 SSE 事件
     */
    private void handleTypedEvent(String eventType, String eventData) {
        switch (eventType) {
            case "endpoint":
                handleEndpointEvent(eventData);
                break;
            case "message":
                handleMessageEvent(eventData);
                break;
            default:
                log.warn("未知的 SSE 事件类型 [{}]: {}", eventType, eventData);
        }
    }

    /**
     * 处理 endpoint 事件，获取 POST 消息的专用 URI
     */
    private void handleEndpointEvent(String eventData) {
        try {
            JsonNode endpointInfo = objectMapper.readTree(eventData);
            if (endpointInfo.has("uri")) {
                this.messageEndpointUri = endpointInfo.get("uri").asText();
                log.info("获取到消息端点 URI [{}]: {}", spec.getId(), messageEndpointUri);

                // 设置连接状态为已连接，并执行握手协议
                connected.set(true);

                // 异步执行握手协议，避免阻塞事件处理
                CompletableFuture.runAsync(() -> {
                    try {
                        performHandshake();
                        // 握手成功后，标记连接完全就绪
                        connectionReadyFuture.complete(null);
                        log.info("MCP SSE 连接完全就绪: {}", spec.getId());
                    } catch (Exception e) {
                        log.error("握手协议失败 [{}]: {}", spec.getId(), e.getMessage());
                        connectionReadyFuture.completeExceptionally(e);
                        connected.set(false);
                    }
                });
            }
        } catch (Exception e) {
            log.error("处理 endpoint 事件失败 [{}]: {}", spec.getId(), eventData, e);
            connectionReadyFuture.completeExceptionally(e);
        }
    }

    /**
     * 处理直接 URI 格式的 endpoint 事件
     * 某些 MCP 服务器实现直接发送 URI 字符串而不是 JSON 格式
     */
    private void handleDirectEndpointEvent(String uri) {
        try {
            this.messageEndpointUri = uri;
            log.info("获取到消息端点 URI [{}]: {}", spec.getId(), messageEndpointUri);

            // 设置连接状态为已连接，并执行握手协议
            connected.set(true);

            // 异步执行握手协议，避免阻塞事件处理
            CompletableFuture.runAsync(() -> {
                try {
                    performHandshake();
                    // 握手成功后，标记连接完全就绪
                    connectionReadyFuture.complete(null);
                    log.info("MCP SSE 连接完全就绪: {}", spec.getId());
                } catch (Exception e) {
                    log.error("握手协议失败 [{}]: {}", spec.getId(), e.getMessage());
                    connectionReadyFuture.completeExceptionally(e);
                    connected.set(false);
                }
            });
        } catch (Exception e) {
            log.error("处理直接 endpoint 事件失败 [{}]: {}", spec.getId(), uri, e);
            connectionReadyFuture.completeExceptionally(e);
        }
    }

    /**
     * 处理 message 事件，分发响应到对应的请求
     */
    private void handleMessageEvent(String eventData) {
        try {
            JsonNode response = objectMapper.readTree(eventData);

            // 根据请求ID分发响应
            if (response.has("id")) {
                long requestId = response.get("id").asLong();
                CompletableFuture<JsonNode> future = pendingRequests.remove(requestId);
                if (future != null) {
                    future.complete(response);
                }
            }
        } catch (Exception e) {
            log.error("处理 message 事件失败 [{}]: {}", spec.getId(), eventData, e);
        }
    }
    
    /**
     * 处理连接错误
     */
    private void handleConnectionError(Throwable error) {
        log.error("SSE 连接错误 [{}]: {}", spec.getId(), error.getMessage());
        connected.set(false);

        // 标记连接就绪失败
        if (!connectionReadyFuture.isDone()) {
            connectionReadyFuture.completeExceptionally(new McpConnectionException("连接建立失败", error));
        }

        // 完成所有待处理的请求
        pendingRequests.values().forEach(future ->
            future.completeExceptionally(new McpConnectionException("连接已断开", error)));
        pendingRequests.clear();

        // 尝试重新连接
        if (shouldReconnect) {
            attemptReconnection();
        }
    }
    
    /**
     * 尝试重新连接
     */
    private void attemptReconnection() {
        // 防止并发重连
        if (isReconnecting) {
            log.debug("重连已在进行中，跳过此次重连请求 [{}]", spec.getId());
            return;
        }

        synchronized (this) {
            if (isReconnecting) {
                return;
            }
            isReconnecting = true;
        }

        new Thread(() -> {
            try {
                for (int attempt = 1; attempt <= MAX_RETRY_ATTEMPTS && shouldReconnect; attempt++) {
                    try {
                        log.info("尝试重新连接 SSE [{}] - 第 {} 次尝试", spec.getId(), attempt);
                        reconnectAttempts = attempt;

                        // 等待一段时间后重试
                        Thread.sleep(RETRY_DELAY_MS * attempt);

                        // 重新初始化连接
                        initializeConnectionInternal();

                        if (connected.get()) {
                            log.info("SSE 重新连接成功 [{}]", spec.getId());
                            reconnectAttempts = 0; // 重置重连计数
                            return;
                        }
                    } catch (Exception e) {
                        log.warn("SSE 重新连接失败 [{}] - 第 {} 次尝试: {}", spec.getId(), attempt, e.getMessage());
                    }
                }

                log.error("SSE 重新连接失败，已达到最大重试次数 [{}]", spec.getId());
                shouldReconnect = false; // 停止重连
            } finally {
                isReconnecting = false;
            }
        }).start();
    }
    
    /**
     * 内部连接初始化方法（不抛出异常）
     */
    private void initializeConnectionInternal() {
        try {
            // 重置连接状态
            this.messageEndpointUri = null;
            // 创建新的连接就绪 Future（如果之前的已完成）
            if (connectionReadyFuture.isDone()) {
                connectionReadyFuture = new CompletableFuture<>();
            }

            // 确定 SSE 端点 URI
            String sseUri = determineSseUri();
            log.debug("重连使用 SSE 端点: {}", sseUri);

            // 建立 SSE 连接
            this.eventStream = webClient.get()
                    .uri(sseUri)
                    .retrieve()
                    .bodyToFlux(String.class)
                    .doOnNext(this::handleServerEvent)
                    .doOnError(this::handleConnectionError)
                    .doOnComplete(() -> {
                        log.info("SSE 连接已关闭: {}", spec.getId());
                        connected.set(false);
                    });

            // 订阅事件流
            eventStream.subscribe();

        } catch (Exception e) {
            log.debug("SSE 连接初始化失败: {}", e.getMessage());
            connected.set(false);
        }
    }
    
    @Override
    protected void performHandshake() throws McpConnectionException {
        try {
            log.debug("开始 MCP SSE 握手协议: {}", spec.getId());

            // 检查消息端点是否已获取
            if (messageEndpointUri == null) {
                throw new McpConnectionException("未获取到消息端点 URI");
            }

            // 发送 initialize 请求（使用内部方法，不等待连接就绪）
            JsonNode initRequest = buildInitializeRequest();
            JsonNode initResponse = sendRequestInternal(initRequest);

            validateResponse(initResponse);
            
            // 解析服务器能力信息
            parseInitializeResponse(initResponse);

            // 发送 notifications/initialized 通知（使用内部方法）
            JsonNode initializedNotification = buildInitializedNotification();
            sendNotificationInternal(initializedNotification);

            log.debug("MCP SSE 握手协议完成: {}", spec.getId());

        } catch (Exception e) {
            log.error("MCP SSE 握手协议失败: {}", spec.getId(), e);
            throw new McpConnectionException("SSE 握手协议失败", e);
        }
    }
    
    @Override
    protected JsonNode sendRequest(JsonNode request) throws McpConnectionException {
        // 等待连接完全就绪
        try {
            log.debug("等待 SSE 连接就绪 [{}]", spec.getId());
            long timeoutMs = spec.getTimeout() != null ? spec.getTimeout() * 1000L : 30000L;
            connectionReadyFuture.get(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            throw new McpConnectionException("等待连接就绪超时");
        } catch (Exception e) {
            throw new McpConnectionException("连接建立失败", e);
        }

        return sendRequestInternal(request);
    }

    /**
     * 内部发送请求方法，不等待连接就绪（用于握手协议）
     */
    private JsonNode sendRequestInternal(JsonNode request) throws McpConnectionException {
        if (!connected.get()) {
            throw new McpConnectionException("SSE 连接未建立");
        }

        if (messageEndpointUri == null) {
            throw new McpConnectionException("消息端点 URI 未获取");
        }

        try {
            long requestId = request.get("id").asLong();
            CompletableFuture<JsonNode> responseFuture = new CompletableFuture<>();
            pendingRequests.put(requestId, responseFuture);

            log.debug("发送 MCP SSE 请求 [{}]: {}", spec.getId(), request.toString());

            // 计算正确的请求URI
            String requestUri = calculateRequestUri(messageEndpointUri);
            log.debug("使用请求URI [{}]: {}", spec.getId(), requestUri);

            // 通过 POST 请求发送数据到专用消息端点
            webClient.post()
                    .uri(requestUri)
                    .header("Content-Type", "application/json")
                    .bodyValue(request.toString())
                    .retrieve()
                    .bodyToMono(String.class)
                    .subscribe(
                        response -> log.debug("请求已发送 [{}]", spec.getId()),
                        error -> {
                            log.error("发送请求失败 [{}]: {}", spec.getId(), error.getMessage());
                            CompletableFuture<JsonNode> future = pendingRequests.remove(requestId);
                            if (future != null) {
                                future.completeExceptionally(new McpConnectionException("发送请求失败", error));
                            }
                        }
                    );

            // 等待响应
            long timeoutMs = spec.getTimeout() != null ? spec.getTimeout() * 1000L : 30000L;
            JsonNode response = responseFuture.get(timeoutMs, TimeUnit.MILLISECONDS);
            log.debug("收到 MCP SSE 响应 [{}]: {}", spec.getId(), response.toString());

            return response;

        } catch (TimeoutException e) {
            log.error("MCP SSE 请求超时 [{}]", spec.getId());
            throw new McpConnectionException("请求超时", e);
        } catch (Exception e) {
            log.error("发送 MCP SSE 请求失败 [{}]", spec.getId(), e);
            throw new McpConnectionException("发送请求失败", e);
        }
    }

    /**
     * 计算正确的请求URI，当配置包含/sse时使用绝对路径
     */
    private String calculateRequestUri(String messageEndpointUri) {
        String baseUrl = spec.getUrl();
        
        // 如果baseUrl以/sse结尾，需要使用绝对路径，忽略baseUrl中的/sse部分
        if (baseUrl.endsWith("/sse")) {
            // 构建完整的绝对URL，去掉/sse部分
            String serverBaseUrl = baseUrl.substring(0, baseUrl.length() - 4); // 去掉"/sse"
            return serverBaseUrl + messageEndpointUri;
        }
        
        return messageEndpointUri;
    }

    /**
     * 发送通知消息（不需要响应）
     */
    private void sendNotification(JsonNode notification) throws McpConnectionException {
        // 等待连接完全就绪
        try {
            log.debug("等待 SSE 连接就绪 [{}]", spec.getId());
            long timeoutMs = spec.getTimeout() != null ? spec.getTimeout() * 1000L : 30000L;
            connectionReadyFuture.get(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            throw new McpConnectionException("等待连接就绪超时");
        } catch (Exception e) {
            throw new McpConnectionException("连接建立失败", e);
        }

        sendNotificationInternal(notification);
    }

    /**
     * 内部发送通知方法，不等待连接就绪（用于握手协议）
     */
    private void sendNotificationInternal(JsonNode notification) throws McpConnectionException {
        if (!connected.get()) {
            throw new McpConnectionException("SSE 连接未建立");
        }

        if (messageEndpointUri == null) {
            throw new McpConnectionException("消息端点 URI 未获取");
        }

        try {
            log.debug("发送 MCP SSE 通知 [{}]: {}", spec.getId(), notification.toString());

            // 计算正确的请求URI
            String requestUri = calculateRequestUri(messageEndpointUri);
            log.debug("使用通知URI [{}]: {}", spec.getId(), requestUri);

            // 通过 POST 请求发送通知到专用消息端点
            webClient.post()
                    .uri(requestUri)
                    .header("Content-Type", "application/json")
                    .bodyValue(notification.toString())
                    .retrieve()
                    .bodyToMono(String.class)
                    .subscribe(
                        response -> log.debug("通知已发送 [{}]", spec.getId()),
                        error -> log.error("发送通知失败 [{}]: {}", spec.getId(), error.getMessage())
                    );

        } catch (Exception e) {
            log.error("发送 MCP SSE 通知失败 [{}]", spec.getId(), e);
            throw new McpConnectionException("发送通知失败", e);
        }
    }
    
    @Override
    public MCPToolResult callTool(String toolName, Map<String, String> arguments) {
        try {
            log.debug("调用 MCP SSE 工具 [{}]: {} with args: {}", spec.getId(), toolName, arguments);
            
            JsonNode request = buildToolCallRequest(toolName, arguments);
            JsonNode response = sendRequest(request);
            
            MCPToolResult result = parseToolResult(response, toolName);
            log.debug("MCP SSE 工具调用结果 [{}]: success={}", spec.getId(), result.isSuccess());
            
            return result;
            
        } catch (Exception e) {
            log.error("调用 MCP SSE 工具失败 [{}]: {}", spec.getId(), toolName, e);
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
            log.debug("获取 MCP SSE 工具列表: {}", spec.getId());
            
            JsonNode request = buildListToolsRequest();
            JsonNode response = sendRequest(request);
            
            List<MCPTool> tools = parseToolsList(response);
            log.debug("获取到 {} 个 MCP SSE 工具: {}", tools.size(), spec.getId());
            
            return tools;
            
        } catch (Exception e) {
            log.error("获取 MCP SSE 工具列表失败: {}", spec.getId(), e);
            return List.of();
        }
    }

    @Override
    public void connect() {
        if (isConnected()) {
            log.debug("MCP SSE 客户端已连接: {}", spec.getId());
            return;
        }

        log.info("连接 MCP SSE 客户端: {}", spec.getId());
        shouldReconnect = true;
        messageEndpointUri = null; // 重置消息端点
        reconnectAttempts = 0; // 重置重连计数
        isReconnecting = false; // 重置重连状态

        // 重置连接就绪状态
        if (connectionReadyFuture.isDone()) {
            connectionReadyFuture = new CompletableFuture<>();
        }

        initializeConnection();
    }

    @Override
    public void disconnect() {
        if (!isConnected()) {
            log.debug("MCP SSE 客户端已断开: {}", spec.getId());
            return;
        }

        log.info("断开 MCP SSE 客户端连接: {}", spec.getId());
        shouldReconnect = false;
        connected.set(false);

        // 标记连接就绪失败（如果还未完成）
        if (!connectionReadyFuture.isDone()) {
            connectionReadyFuture.completeExceptionally(new McpConnectionException("连接已断开"));
        }

        // 完成所有待处理的请求
        pendingRequests.values().forEach(future ->
            future.completeExceptionally(new McpConnectionException("连接已断开")));
        pendingRequests.clear();

        // 关闭事件流
        if (eventStream != null) {
            // WebClient 会自动清理资源
        }
    }

    @Override
    public void close() {
        log.info("关闭 MCP SSE 客户端: {}", spec.getId());
        disconnect();
    }
}