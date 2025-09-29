package com.mcp.client.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mcp.client.exception.McpConnectionException;
import com.mcp.client.model.McpServerSpec;
import com.mcp.client.model.MCPTool;
import com.mcp.client.model.MCPToolResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import org.springframework.http.MediaType;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

/**
 * SSE (Server-Sent Events) 传输协议的 MCP 客户端实现
 * 符合 MCP 协议版本 2024-11-05 规范：
 * 1. 建立 SSE 连接到 /sse 端点
 * 2. 从第一条 SSE 消息中获取 POST 消息的专用 URI
 * 3. 通过 SSE 接收服务器消息，通过 POST 发送客户端消息
 * 4. 定期进行心跳检测，确保连接有效性
 */
@Slf4j
public class MCPSseClient extends AbstractMCPClient {

    private final WebClient webClient;
    private final Map<Long, CompletableFuture<JsonNode>> pendingRequests = new ConcurrentHashMap<>();
    private Disposable sseSubscription;
    private Flux<String> eventStream;
    private volatile String messageEndpointUri; // 用于 POST 消息的专用 URI
    private volatile CompletableFuture<Void> connectionReadyFuture = new CompletableFuture<>(); // 连接就绪信号

    private volatile boolean shouldReconnect = true;
    private volatile int reconnectAttempts = 0; // 重连尝试次数
    private volatile boolean isReconnecting = false; // 防止并发重连
    private static final int MAX_RETRY_ATTEMPTS = 3;
    private static final long RETRY_DELAY_MS = 1000;
    // 连接超时时间从配置中获取，默认30秒
    
    // 心跳检测相关
    private volatile ScheduledExecutorService heartbeatExecutor; // 心跳线程池，非final，允许重新创建
    private volatile ScheduledFuture<?> heartbeatFuture; // 心跳任务句柄，用于防止重复调度
    private volatile long lastHeartbeatTime = 0; // 上次心跳时间
    private volatile boolean heartbeatEnabled = true; // 是否启用心跳检测
    private static final long HEARTBEAT_INTERVAL_MS = 60000; // 心跳间隔60秒
    private static final long HEARTBEAT_TIMEOUT_MS = 15000; // 心跳超时15秒
    
    public MCPSseClient(McpServerSpec spec) {
        super(spec);
        this.webClient = WebClient.builder()
                .baseUrl(spec.getUrl())
                .defaultHeader("Cache-Control", "no-cache")
                .build();

        // 初始化心跳时间记录
        lastHeartbeatTime = System.currentTimeMillis();

        // 初始化心跳线程池
        createHeartbeatExecutor();

        initializeConnection();
        startHeartbeat();
    }

    // ==================== 连接建立相关函数 ====================

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

            // 建立 SSE 连接
            this.eventStream = webClient.get()
                    .uri(determineSseUri())
                    .accept(MediaType.TEXT_EVENT_STREAM)
                    .retrieve()
                    .bodyToFlux(String.class)
                    .doOnNext(this::handleServerEvent)
                    .doOnError(this::handleConnectionError)
                    .doOnComplete(() -> {
                        log.info("SSE 连接已关闭: {}", spec.getId());
                        // 只有在应该重连的情况下才触发重连，避免应用关闭时的无效重连
                        transitionToDisconnected(null, shouldReconnect, false);
                    });

            // 订阅事件流
            this.sseSubscription = eventStream.subscribe();

            log.info("MCP SSE 客户端初始化成功: {}", spec.getId());

        } catch (Exception e) {
            log.error("初始化 MCP SSE 客户端失败: {}", spec.getId(), e);
            transitionToDisconnected(e, false, false); // 初始化失败不触发重连
            throw new McpConnectionException("初始化 SSE 客户端失败", e);
        }
    }

    /**
     * 确定 SSE 端点 URI
     * 如果 URL 已经包含 SSE 路径，则直接使用空路径；否则添加 /sse
     * 支持包含查询参数的URL
     */
    private String determineSseUri() {
        String url = spec.getUrl();

        // 分离URL的路径部分和查询参数部分，几种可能的mcp-server不同格式的url参数如下：
        // http://localhost:10011
        // http://localhost:10011/sse
        // https://open.bigmodel.cn/api/mcp/web_search/sse?Authorization=c2bdce85572.ydql3AhdCW
        String pathPart;
        int queryIndex = url.indexOf('?');
        if (queryIndex != -1) {
            pathPart = url.substring(0, queryIndex);
        } else {
            pathPart = url;
        }

        if (pathPart.endsWith("/sse")) {
            // URL 路径已经包含 /sse，直接使用空路径
            return "";
        } else {
            // URL 路径不包含 /sse，添加 /sse
            return "/sse";
        }
    }

    /**
     * 执行 MCP SSE 握手协议
     * 1. 检查消息端点是否已获取
     * 2. 发送 initialize 请求
     * 3. 解析服务器能力信息
     * 4. 发送 notifications/initialized 通知
     */
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
            try {
                sendNotificationInternal(initializedNotification);
                log.debug("已发送 notifications/initialized 通知 [{}]", spec.getId());
            } catch (McpConnectionException e) {
                // 对于通知，某些服务器可能不返回响应，这是正常的
                log.debug("发送 notifications/initialized 通知时收到异常，但这可能是正常的 [{}]: {}", spec.getId(), e.getMessage());
            }

            log.debug("MCP SSE 握手协议完成: {}", spec.getId());
            // 握手成功后重置心跳时间戳
            lastHeartbeatTime = System.currentTimeMillis();
            // 握手成功后，标记连接完全就绪
            if (connectionReadyFuture != null && !connectionReadyFuture.isDone()) {
                connectionReadyFuture.complete(null);
            }
            log.info("MCP SSE 连接完全就绪: {}", spec.getId());

        } catch (Exception e) {
            log.error("MCP SSE 握手协议失败: {}", spec.getId(), e);
            if (connectionReadyFuture != null && !connectionReadyFuture.isDone()) {
                connectionReadyFuture.completeExceptionally(e);
            }
            throw new McpConnectionException("SSE 握手协议失败", e);
        }
    }

    /**
     * 检查 MCP SSE 客户端是否已连接
     * 连接状态包括：
     * 1. 基本连接状态（connected 标志）
     * 2. 消息端点 URI 是否已获取
     * 3. 心跳检测（如果启用）
     */
    @Override
    public boolean isConnected() {
        // 基本连接状态检查
        if (!connected.get() || messageEndpointUri == null) {
            return false;
        }

        // 检查心跳是否正常（如果启用了心跳检测）
        if (heartbeatEnabled && lastHeartbeatTime > 0) {
            long currentTime = System.currentTimeMillis();
            long timeSinceLastHeartbeat = currentTime - lastHeartbeatTime;
            if (timeSinceLastHeartbeat > HEARTBEAT_INTERVAL_MS * 3) {
                log.warn("心跳检测异常，距离上次心跳已超过 {}ms [{}]", timeSinceLastHeartbeat, spec.getId());
                return false;
            }
        }

        return true;
    }

    // ==================== 消息传输相关函数 ====================

    /**
     * 处理服务器事件
     * 按照 MCP 协议规范处理 SSE 事件：
     * 1. endpoint 事件：包含用于 POST 消息的专用 URI
     * 2. message 事件：包含服务器响应消息
     */
    private void handleServerEvent(String event) {
        try {
            log.debug("收到 SSE 事件 [{}]: {}", spec.getId(), event);

            // 解析 SSE 事件格式，支持多行 data
            String eventType = null;
            StringBuilder dataBuilder = new StringBuilder();

            String[] lines = event.split("\n");
            for (String line : lines) {
                if (line.startsWith("event: ")) {
                    eventType = line.substring(7).trim();
                } else if (line.startsWith("data: ")) {
                    // 支持多行 data，按 SSE 规范用换行拼接
                    if (dataBuilder.length() > 0) {
                        dataBuilder.append('\n');
                    }
                    dataBuilder.append(line.substring(6));
                }
            }

            String eventData = dataBuilder.length() > 0 ? dataBuilder.toString() : null;

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
                    } catch (Exception e) {
                        log.error("握手协议失败 [{}]: {}", spec.getId(), e.getMessage());
                        transitionToDisconnected(e, true, false);
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
                } catch (Exception e) {
                    log.error("握手协议失败 [{}]: {}", spec.getId(), e.getMessage());
                    transitionToDisconnected(e, true, false);
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
     * getTools及callTool调用发送请求方法，等待连接就绪
     */
    @Override
    protected JsonNode sendRequest(JsonNode request) throws McpConnectionException {
        // 首先检查连接状态
        if (!isConnected()) {
            log.warn("连接已断开，尝试重连 [{}]", spec.getId());
            if (shouldReconnect && !isReconnecting) {
                attemptReconnection();
            }
            throw new McpConnectionException("SSE连接已断开");
        }

        // 等待连接完全就绪
        try {
            log.debug("等待 SSE 连接就绪 [{}]", spec.getId());
            long timeoutMs = spec.getTimeout() != null ? spec.getTimeout() * 1000L : 30000L;
            connectionReadyFuture.get(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            log.error("等待连接就绪超时，可能服务端会话已过期 [{}]", spec.getId());
            transitionToDisconnected(e, true, false);
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
            pendingRequests.remove(request.get("id").asLong());
            throw new McpConnectionException("请求超时", e);
        } catch (Exception e) {
            log.error("发送 MCP SSE 请求失败 [{}]", spec.getId(), e);
            pendingRequests.remove(request.get("id").asLong());
            throw new McpConnectionException("发送请求失败", e);
        }
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


    // ==================== 连接重试与恢复相关函数 ====================

    /**
     * 统一的断连和资源清理函数
     * @param cause 断连原因（可为null）
     * @param triggerReconnect 是否触发重连
     * @param stopHeartbeat 是否停止心跳检测
     */
    private void transitionToDisconnected(Throwable cause, boolean triggerReconnect, boolean stopHeartbeat) {
        log.debug("执行断连清理 [{}]: triggerReconnect={}, stopHeartbeat={}", spec.getId(), triggerReconnect, stopHeartbeat);

        // 设置连接状态为断开
        connected.set(false);

        // 安全处置SSE订阅
        safeDisposeSse();

        // 完成所有待处理的请求
        McpConnectionException disconnectException = new McpConnectionException(
            cause != null ? "连接已断开: " + cause.getMessage() : "连接已断开", cause);
        pendingRequests.values().forEach(future -> future.completeExceptionally(disconnectException));
        pendingRequests.clear();

        // 标记连接就绪失败（如果还未完成）
        if (!connectionReadyFuture.isDone()) {
            connectionReadyFuture.completeExceptionally(disconnectException);
        }

        // 停止心跳检测（如果需要）
        if (stopHeartbeat) {
            stopHeartbeat();
        }

        // 触发重连（如果需要）
        if (triggerReconnect && shouldReconnect && !isReconnecting) {
            attemptReconnection();
        }
    }

    /**
     * 处理连接错误
     */
    private void handleConnectionError(Throwable error) {
        log.error("SSE 连接错误 [{}]: {}", spec.getId(), error.getMessage());
        transitionToDisconnected(error, true, false);
    }

    /**
     * 安全地处置 SSE 订阅
     */
    private void safeDisposeSse() {
        if (sseSubscription != null && !sseSubscription.isDisposed()) {
            log.debug("处置旧的 SSE 订阅 [{}]", spec.getId());
            sseSubscription.dispose();
            sseSubscription = null;
        }
        eventStream = null;
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

        // 先处置旧的 SSE 连接，防止双连接
        safeDisposeSse();

        Thread t = new Thread(() -> {
            try {
                for (int attempt = 1; attempt <= MAX_RETRY_ATTEMPTS && shouldReconnect; attempt++) {
                    try {
                        log.info("尝试重新连接 SSE [{}] - 第 {} 次尝试", spec.getId(), attempt);
                        reconnectAttempts = attempt;

                        // 退避等待
                        try {
                            Thread.sleep(RETRY_DELAY_MS * attempt);
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                        }

                        // 重新初始化连接
                        initializeConnectionInternal();

                        if (connected.get()) {
                            // 重连成功后重置心跳时间戳
                            lastHeartbeatTime = System.currentTimeMillis();
                            log.info("SSE 重新连接成功 [{}]", spec.getId());
                            reconnectAttempts = 0; // 重置重连计数

                            // 确保心跳在线
                            if (heartbeatExecutor == null || heartbeatExecutor.isShutdown()) {
                                createHeartbeatExecutor();
                            }
                            heartbeatEnabled = true;
                            // 重新启动心跳检测
                            startHeartbeat();
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
        });
        t.setDaemon(true);
        t.start();
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

            // 建立 SSE 连接
            this.eventStream = webClient.get()
                    .uri(determineSseUri())
                    .accept(MediaType.TEXT_EVENT_STREAM)
                    .retrieve()
                    .bodyToFlux(String.class)
                    .doOnNext(this::handleServerEvent)
                    .doOnError(this::handleConnectionError)
                    .doOnComplete(() -> {
                        log.info("SSE 重连连接已关闭: {}", spec.getId());
                        // 重连场景下的连接关闭不再触发新的重连，避免无限循环
                        // 由 attemptReconnection 的重试循环来处理后续重连
                        transitionToDisconnected(null, false, false);
                    });

            // 订阅事件流
            this.sseSubscription = eventStream.subscribe();

        } catch (Exception e) {
            log.debug("SSE 连接初始化失败: {}", e.getMessage());
            transitionToDisconnected(e, false, false);
        }
    }

    // ==================== 连接关闭与清理相关函数 ====================

    @Override
    public void disconnect() {
        if (!isConnected()) {
            log.debug("MCP SSE 客户端已断开: {}", spec.getId());
            return;
        }
        log.info("断开 MCP SSE 客户端连接: {}", spec.getId());
        shouldReconnect = false;
        transitionToDisconnected(null, false, true);
    }

    @Override
    public void close() {
        log.info("关闭 MCP SSE 客户端: {}", spec.getId());
        disconnect();
        // 清空messageEndpointUri
        messageEndpointUri = null;
    }
    
    // ==================== 心跳保活相关函数 ====================

    /**
     * 创建心跳线程池
     */
    private void createHeartbeatExecutor() {
        this.heartbeatExecutor = Executors.newSingleThreadScheduledExecutor(
                r -> {
                    Thread t = new Thread(r, "SSE-Heartbeat-" + spec.getId());
                    t.setDaemon(true);
                    return t;
                });
    }
    
    /**
     * 启动心跳检测
     */
    private void startHeartbeat() {
        if (!heartbeatEnabled) return;
        if (heartbeatExecutor == null || heartbeatExecutor.isShutdown()) {
            createHeartbeatExecutor();
        }
        // 防止重复调度心跳任务
        if (heartbeatFuture != null && !heartbeatFuture.isCancelled()) {
            log.debug("心跳检测已在运行 [{}]，跳过重复启动", spec.getId());
            return;
        }

        heartbeatFuture = heartbeatExecutor.scheduleWithFixedDelay(this::performHeartbeat,
                HEARTBEAT_INTERVAL_MS, HEARTBEAT_INTERVAL_MS, TimeUnit.MILLISECONDS);
        log.debug("启动SSE心跳检测 [{}], 间隔: {}ms", spec.getId(), HEARTBEAT_INTERVAL_MS);
    }
    
    /**
     * 执行心跳检测
     */
    private void performHeartbeat() {
        if (!heartbeatEnabled || !connected.get()) {
            return;
        }

        try {
            // 更新心跳时间记录
            lastHeartbeatTime = System.currentTimeMillis();

            // 发送一个轻量级的ping请求来检测连接
            if (messageEndpointUri != null) {
                // 使用 tools/list 进行轻量心跳
                JsonNode pingRequest = buildListToolsRequest();
                sendHeartbeatRequest(pingRequest);
            }
        } catch (Exception e) {
            log.warn("心跳检测执行异常 [{}]: {}", spec.getId(), e.getMessage());
            handleHeartbeatError(e);
        }
    }
    
    /**
     * 发送心跳检测请求
     */
    private void sendHeartbeatRequest(JsonNode request) {
        try {
            // 心跳检测只是为了验证连接是否有效，不需要等待响应
            // 如果连接有问题，在POST请求阶段就会失败
            webClient.post()
                    .uri(calculateRequestUri(messageEndpointUri))
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(request.toString())
                    .retrieve()
                    .toBodilessEntity()  // 不关心响应内容，只关心请求是否成功发送
                    .timeout(Duration.ofMillis(HEARTBEAT_TIMEOUT_MS))
                    .doOnSuccess(response -> {
                        log.debug("心跳检测请求发送成功 [{}]", spec.getId());
                    })
                    .doOnError(this::handleHeartbeatError)
                    .subscribe();
                    
        } catch (Exception e) {
            log.warn("发送心跳检测请求失败 [{}]: {}", spec.getId(), e.getMessage());
            handleHeartbeatError(e);
        }
    }
    
    /**
     * 处理心跳检测错误
     */
    private void handleHeartbeatError(Throwable error) {
        log.warn("心跳检测失败 [{}]: {}, 尝试重连", spec.getId(), error.getMessage());
        transitionToDisconnected(error, true, false);
    }
    
    /**
     * 停止心跳检测
     */
    private void stopHeartbeat() {
        heartbeatEnabled = false;

        // 取消心跳任务
        if (heartbeatFuture != null && !heartbeatFuture.isCancelled()) {
            heartbeatFuture.cancel(true);
            heartbeatFuture = null;
        }

        if (heartbeatExecutor != null && !heartbeatExecutor.isShutdown()) {
            heartbeatExecutor.shutdown();
            try {
                if (!heartbeatExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                    heartbeatExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                heartbeatExecutor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        log.debug("停止SSE心跳检测 [{}]", spec.getId());
    }

    // ==================== 工具调用相关函数 ====================

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
    


    /**
     * 计算正确的请求URI
     * 根据配置的baseUrl和服务器返回的messageEndpointUri构建完整的请求URL
     *
     * MCP协议规范：
     * 1. SSE连接到 /sse 端点
     * 2. 服务器返回消息端点URI（绝对路径）
     * 3. 客户端使用 协议+域名+端口 + 消息端点URI 进行POST请求
     */
    private String calculateRequestUri(String messageEndpointUri) {
        String baseUrl = spec.getUrl();
        // 例如，当 baseUrl 为 https://open.bigmodel.cn/api/mcp/web_search/sse?Authorization=c2bdce85572.ydql3AhdCW 时

        try {
            // 解析baseUrl获取协议、域名、端口
            java.net.URL url = new java.net.URL(baseUrl);
            String protocol = url.getProtocol();
            String host = url.getHost();
            int port = url.getPort();

            // 构建服务器基础URL（协议+域名+端口）
            StringBuilder serverBaseUrl = new StringBuilder();
            serverBaseUrl.append(protocol).append("://").append(host);
            if (port != -1 && port != 80 && port != 443) {
                serverBaseUrl.append(":").append(port);
            }
            // serverBaseUrl 为 https://open.bigmodel.cn

            // 处理查询参数合并
            String baseQuery = url.getQuery();
            // baseQuery 为 Authorization=c2bdce85572.ydql3AhdCW
            if (baseQuery != null && !baseQuery.isEmpty()) {
                // 如果messageEndpointUri已经包含查询参数，需要合并
                // messageEndpointUri示例值：/messages?sessionId=f8fda987-2172-4142-ad09-d88ffb36e2bd
                if (messageEndpointUri.contains("?")) {
                    return serverBaseUrl.toString() + messageEndpointUri + "&" + baseQuery;
                } else {
                    return serverBaseUrl.toString() + messageEndpointUri + "?" + baseQuery;
                }
            } else {
                // 没有baseQuery，直接拼接
                return serverBaseUrl.toString() + messageEndpointUri;
            }

        } catch (java.net.MalformedURLException e) {
            log.warn("解析baseUrl失败 [{}]: {}, 使用fallback方法", spec.getId(), baseUrl);
            // Fallback: 简单的字符串处理
            return fallbackCalculateRequestUri(baseUrl, messageEndpointUri);
        }
    }

    /**
     * Fallback方法：当URL解析失败时使用简单的字符串处理
     */
    private String fallbackCalculateRequestUri(String baseUrl, String messageEndpointUri) {
        // 分离baseUrl的路径部分和查询参数部分
        String basePath;
        String baseQuery = "";
        int queryIndex = baseUrl.indexOf('?');
        if (queryIndex != -1) {
            basePath = baseUrl.substring(0, queryIndex);
            baseQuery = baseUrl.substring(queryIndex);
        } else {
            basePath = baseUrl;
        }

        // 提取协议+域名+端口部分（去掉路径）
        int pathStartIndex = basePath.indexOf('/', 8); // 跳过 "https://" 或 "http://"
        String serverBase;
        if (pathStartIndex != -1) {
            serverBase = basePath.substring(0, pathStartIndex);
        } else {
            serverBase = basePath;
        }

        // 合并查询参数
        if (!baseQuery.isEmpty()) {
            if (messageEndpointUri.contains("?")) {
                return serverBase + messageEndpointUri + "&" + baseQuery.substring(1);
            } else {
                return serverBase + messageEndpointUri + baseQuery;
            }
        } else {
            return serverBase + messageEndpointUri;
        }
    }
}