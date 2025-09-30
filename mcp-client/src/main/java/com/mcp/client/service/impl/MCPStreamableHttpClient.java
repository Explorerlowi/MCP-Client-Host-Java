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
import reactor.core.Disposable;

import java.util.concurrent.*;

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

    private Disposable sseSubscription; // 可选的SSE订阅，一旦建立需要可控释放
    private Flux<String> eventStream;
    private String sessionId; // 改为非final，初始为null，由服务端返回
    private volatile long lastEventId = 0;
    private volatile boolean supportsResume = false;
    private volatile CompletableFuture<Void> connectionReadyFuture = new CompletableFuture<>();

    private volatile boolean shouldReconnect = true;
    private volatile int reconnectAttempts = 0; // 重连尝试次数
    private volatile boolean isReconnecting = false;
    private static final int MAX_RETRY_ATTEMPTS = 3;
    private static final long RETRY_DELAY_MS = 1000;


    // 保活检测
    private volatile ScheduledExecutorService heartbeatExecutor;
    private volatile ScheduledFuture<?> heartbeatFuture; // 心跳任务句柄，防重复调度/便于取消
    private volatile long lastHeartbeatTime = 0;
    private volatile boolean heartbeatEnabled = true;
    private static final long HEARTBEAT_INTERVAL_MS = 60000; // 60秒
    private static final long HEARTBEAT_TIMEOUT_MS = 15000; // 15秒

    // 工具信息缓存
    private volatile List<MCPTool> cachedTools = null; // 缓存的工具列表
    private final Object toolsCacheLock = new Object(); // 工具缓存锁

    public MCPStreamableHttpClient(McpServerSpec spec) {
        super(spec);
        this.sessionId = null; // 初始化为null，等待服务端返回
        this.webClient = WebClient.builder()
                .baseUrl(spec.getUrl())
                .defaultHeader("Origin", "http://localhost")
                .defaultHeader("User-Agent", "mcp-java-client/1.0.0")
                .build();

        // 初始化心跳时间记录
        lastHeartbeatTime = System.currentTimeMillis();

        // 初始化心跳线程池
        createHeartbeatExecutor();

        establishConnection();
        startHeartbeat();
    }

    // ==================== 连接建立相关函数 ====================

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
            // 建链前重置连接就绪Future（若已完成）
            if (connectionReadyFuture == null || connectionReadyFuture.isDone()) {
                connectionReadyFuture = new CompletableFuture<>();
            }

            // 构建 SSE 连接请求 - 使用计算的端点建立事件流
            WebClient.RequestHeadersSpec<?> requestSpec = webClient.get()
                    .uri(calculateRequestUri()) // 绝对URL，确保保留查询参数
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
                            log.info("服务器不支持 GET 方法，跳过 SSE 连接建立 [{}]", spec.getId());
                        })
                        .doOnComplete(() -> {
                            log.info("Streamable HTTP 连接已关闭: {}", spec.getId());
                            transitionToDisconnected(null, shouldReconnect, false);
                        });

                // 订阅事件流（如果服务器支持）
                this.sseSubscription = eventStream.subscribe(
                    event -> {}, // onNext - 已在 doOnNext 中处理
                    error -> {
                        log.info("服务器不支持 SSE 连接，将使用仅 POST 模式 [{}]: {}", spec.getId(), error.getMessage());
                        this.eventStream = null;
                        this.sseSubscription = null;
                    },
                    () -> log.debug("SSE 事件流已完成 [{}]", spec.getId()) // onComplete
                );

                log.info("MCP Streamable HTTP 客户端初始化成功: {}", spec.getId());

            } catch (Exception e) {
                log.info("无法建立 SSE 连接，将使用仅 POST 模式 [{}]: {}", spec.getId(), e.getMessage());
                this.eventStream = null;
            }

            // 设置连接状态为已连接，并执行握手协议
            connected.set(true);
            // 执行握手协议
            performHandshake();

            log.info("MCP Streamable HTTP 客户端初始化成功: {}", spec.getId());
        } catch (Exception e) {
            log.error("初始化 MCP Streamable HTTP 客户端失败: {}", spec.getId(), e);
            if (connectionReadyFuture != null && !connectionReadyFuture.isDone()) {
                connectionReadyFuture.completeExceptionally(e);
            }
            throw new McpConnectionException("初始化 Streamable HTTP 客户端失败", e);
        }
    }

    /**
     * 执行 MCP Streamable HTTP 握手协议
     */
    @Override
    protected void performHandshake() throws McpConnectionException {
        try {
            log.debug("开始 MCP Streamable HTTP 握手协议: {}", spec.getId());

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

            log.debug("MCP Streamable HTTP 握手协议完成: {}", spec.getId());
            // 握手成功后重置心跳时间戳
            lastHeartbeatTime = System.currentTimeMillis();
            // 标记连接完全就绪
            if (connectionReadyFuture != null && !connectionReadyFuture.isDone()) {
                connectionReadyFuture.complete(null);
            }
            log.info("MCP Streamable HTTP 连接完全就绪: {}", spec.getId());

        } catch (Exception e) {
            log.error("MCP Streamable HTTP 握手协议失败: {}", spec.getId(), e);
            if (connectionReadyFuture != null && !connectionReadyFuture.isDone()) {
                connectionReadyFuture.completeExceptionally(e);
            }
            throw new McpConnectionException("Streamable HTTP 握手协议失败", e);
        }
    }

    /**
     * 检查 MCP Streamable HTTP 客户端是否已连接
     */
    @Override
    public boolean isConnected() {
        if (!connected.get()) return false;

        // 检查心跳是否正常（如果启用了心跳检测）
        if (heartbeatEnabled && lastHeartbeatTime > 0) {
            long currentTime = System.currentTimeMillis();
            long since = currentTime - lastHeartbeatTime;
            if (since > HEARTBEAT_INTERVAL_MS * 3) {
                log.warn("心跳检测异常，距离上次心跳已超过 {}ms [{}]", since, spec.getId());
                return false;
            }
        }
        return true;
    }

    // ==================== 消息传输相关函数 ====================

    /**
     * 处理服务器事件
     */
    private void handleServerEvent(String event) {
        try {
            log.debug("收到 Streamable HTTP 事件 [{}]: {}", spec.getId(), event);

            // 逐行解析，兼容标准 SSE：event: / 多行 data: / id:
            String eventType = null;
            StringBuilder dataBuilder = new StringBuilder();

            String[] lines = event.split("\n");
            for (String line : lines) {
                String trimmed = line.trim();
                if (trimmed.startsWith("id: ")) {
                    String eventIdStr = trimmed.substring(4).trim();
                    try {
                        lastEventId = Long.parseLong(eventIdStr);
                    } catch (NumberFormatException e) {
                        log.debug("无法解析事件ID: {}", eventIdStr);
                    }
                } else if (trimmed.startsWith("event: ")) {
                    eventType = trimmed.substring(7).trim();
                } else if (trimmed.startsWith("data: ")) {
                    if (dataBuilder.length() > 0) {
                        dataBuilder.append('\n');
                    }
                    dataBuilder.append(trimmed.substring(6));
                }
            }

            String eventDataStr = dataBuilder.length() > 0 ? dataBuilder.toString() : null;

            if (eventDataStr != null) {
                JsonNode eventData = objectMapper.readTree(eventDataStr);

                // sessionId 从HTTP响应头获取，不从事件数据中获取

                // 检查服务器是否支持断点续传（事件内能力宣告）
                if (eventData.has("capabilities") && eventData.get("capabilities").has("resume")) {
                    supportsResume = eventData.get("capabilities").get("resume").asBoolean();
                    log.debug("服务器断点续传支持: {}", supportsResume);
                }

                // 如果提供了 eventType，则按类型分发；否则沿用之前的基于 payload 的分发
                if (eventType != null) {
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
                } else if (eventData.has("type")) {
                    String inferredType = eventData.get("type").asText();
                    switch (inferredType) {
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
                            log.debug("未知事件类型 [{}]: {}", spec.getId(), inferredType);
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
            throw new McpConnectionException("Streamable HTTP连接已断开");
        }

        // 等待连接完全就绪
        try {
            log.debug("等待 Streamable HTTP 连接就绪 [{}]", spec.getId());
            long timeoutMs = spec.getTimeout() != null ? spec.getTimeout() * 1000L : 30000L;
            if (connectionReadyFuture != null) {
                connectionReadyFuture.get(timeoutMs, TimeUnit.MILLISECONDS);
            }
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
            throw new McpConnectionException("Streamable HTTP 连接未建立");
        }

        try {
            log.debug("发送 MCP Streamable HTTP 请求 [{}]: {}", spec.getId(), request.toString());

            // 发送 POST 请求
            WebClient.RequestBodySpec requestSpec = webClient.post()
                    .uri(calculateRequestUri()) // 绝对URL，确保保留查询参数
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
            if (connectionReadyFuture != null && !connectionReadyFuture.isDone()) {
                connectionReadyFuture.completeExceptionally(e);
            }
            throw new McpConnectionException("发送请求失败", e);
        }
    }

    /**
     * 内部发送通知方法，不等待连接就绪（用于握手协议）
     * 通知请求按照 MCP 协议规范不需要等待响应
     */
    private void sendNotificationInternal(JsonNode notification) throws McpConnectionException {
        if (!connected.get()) {
            throw new McpConnectionException("Streamable HTTP 连接未建立");
        }

        try {
            log.debug("发送 MCP Streamable HTTP 通知 [{}]: {}", spec.getId(), notification.toString());

            // 发送 POST 请求
            WebClient.RequestBodySpec requestSpec = webClient.post()
                    .uri(calculateRequestUri()) // 绝对URL，确保保留查询参数
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Accept", "application/json, text/event-stream");

            // 如果已有sessionId，添加到请求头中
            if (sessionId != null) {
                requestSpec = requestSpec.header("Mcp-Session-Id", sessionId);
            }

            // 异步发送通知，不等待响应（通知按协议规范不需要响应）
            requestSpec
                    .bodyValue(notification.toString())
                    .retrieve()
                    .toBodilessEntity()
                    .timeout(Duration.ofSeconds(spec.getTimeout() != null ? spec.getTimeout() : 30L))
                    .subscribe(
                        response -> {
                            log.debug("通知已发送 [{}]", spec.getId());
                            // 从响应头中提取 sessionId（如果需要）
                            if (sessionId == null && response.getHeaders().containsKey("Mcp-Session-Id")) {
                                String headerSessionId = response.getHeaders().getFirst("Mcp-Session-Id");
                                if (headerSessionId != null) {
                                    sessionId = headerSessionId;
                                    log.info("从HTTP响应头获取到sessionId: {} [{}]", sessionId, spec.getId());
                                }
                            }
                        },
                        error -> {
                            // 对于通知，某些服务器可能返回错误状态码，但这可能是正常的
                            if (error instanceof WebClientResponseException) {
                                WebClientResponseException webError = (WebClientResponseException) error;
                                log.debug("发送通知时收到HTTP错误，但这可能是正常的 [{}]: {} - {}",
                                    spec.getId(), webError.getStatusCode(), webError.getResponseBodyAsString());
                            } else {
                                log.debug("发送通知时收到异常，但这可能是正常的 [{}]: {}", spec.getId(), error.getMessage());
                            }
                        }
                    );

        } catch (Exception e) {
            log.error("发送 MCP Streamable HTTP 通知失败 [{}]", spec.getId(), e);
            throw new McpConnectionException("发送通知失败", e);
        }
    }

    // ==================== 连接断开与重连控制 ====================

    /**
     * 统一的断连和资源清理
     * @param cause 断连原因（可为null）
     * @param triggerReconnect 是否触发重连
     * @param stopHeartbeat 是否停止心跳
     */
    private void transitionToDisconnected(Throwable cause, boolean triggerReconnect, boolean stopHeartbeat) {
        log.debug("执行断连清理 [{}]: triggerReconnect={}, stopHeartbeat={}", spec.getId(), triggerReconnect, stopHeartbeat);

        connected.set(false);

        // 安全处置SSE订阅
        safeDisposeSse();

        // 标记连接未就绪
        if (connectionReadyFuture != null && !connectionReadyFuture.isDone()) {
            connectionReadyFuture.completeExceptionally(
                    new McpConnectionException(cause != null ? cause.getMessage() : "连接已断开", cause));
        }

        // 停止心跳
        if (stopHeartbeat) {
            stopHeartbeat();
        }

        // 触发重连
        if (triggerReconnect && shouldReconnect && !isReconnecting) {
            attemptReconnection();
        }
    }

    /**
     * 处理连接错误
     */
    private void handleConnectionError(Throwable error) {
        log.error("Streamable HTTP 连接错误 [{}]: {}", spec.getId(), error.getMessage());
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
     * 尝试重连（带退避与上限）
     */
    private void attemptReconnection() {
        if (isReconnecting) {
            log.debug("重连已在进行中，跳过此次请求 [{}]", spec.getId());
            return;
        }
        synchronized (this) {
            if (isReconnecting) return;
            isReconnecting = true;
        }

        // 先处置旧的 SSE 连接，防止双连接
        safeDisposeSse();

        Thread t = new Thread(() -> {
            try {
                for (int attempt = 1; attempt <= MAX_RETRY_ATTEMPTS && shouldReconnect; attempt++) {
                    try {
                        log.info("尝试重新连接 HTTP [{}] - 第 {} 次尝试", spec.getId(), attempt);
                        reconnectAttempts = attempt;

                        // 退避等待
                        try {
                            Thread.sleep(RETRY_DELAY_MS * attempt);
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                        }

                        // 重新建立连接（尽量使用断点续传）
                        establishConnection(true);

                        if (connected.get()) {
                            lastHeartbeatTime = System.currentTimeMillis();
                            log.info("HTTP 重新连接成功 [{}]", spec.getId());
                            reconnectAttempts = 0;

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
                        log.warn("HTTP 重新连接失败 [{}] - 第 {} 次: {}", spec.getId(), attempt, e.getMessage());
                    }
                }
                log.error("HTTP 重新连接失败，达到最大重试次数 [{}]", spec.getId());
                shouldReconnect = false;
            } finally {
                isReconnecting = false;
            }
        });
        t.setDaemon(true);
        t.start();
    }

    // ==================== 连接关闭与清理相关函数 ====================

    @Override
    public void disconnect() {
        if (!isConnected()) {
            log.debug("MCP Streamable HTTP 客户端已断开: {}", spec.getId());
            return;
        }
        log.info("断开 MCP Streamable HTTP 客户端连接: {}", spec.getId());
        shouldReconnect = false;
        transitionToDisconnected(null, false, true);
    }

    @Override
    public void close() {
        log.info("关闭 MCP Streamable HTTP 客户端: {}", spec.getId());
        disconnect();
        // 清空sessionId
        sessionId = null;
    }

    // ==================== 心跳保活相关函数 ====================

    /**
     * 创建心跳线程池
     */
    private void createHeartbeatExecutor() {
        this.heartbeatExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "HTTP-Heartbeat-" + spec.getId());
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
        log.debug("启动HTTP心跳检测 [{}], 间隔: {}ms", spec.getId(), HEARTBEAT_INTERVAL_MS);
    }

    /**
     * 执行心跳检测
     */
    private void performHeartbeat() {
        if (!heartbeatEnabled || !connected.get()) return;
        try {
            // 更新心跳时间记录
            lastHeartbeatTime = System.currentTimeMillis();

            // 通过调用 getTools() 来更新工具缓存并进行心跳检测
            if (sessionId != null) {
                log.debug("执行心跳检测并更新工具缓存 [{}]", spec.getId());
                getTools();
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
            webClient.post()
                    .uri(calculateRequestUri()) // 绝对URL，确保保留查询参数
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Accept", "application/json")
                    .header("Mcp-Session-Id", sessionId)
                    .bodyValue(request.toString())
                    .retrieve()
                    .toBodilessEntity()
                    .timeout(Duration.ofMillis(HEARTBEAT_TIMEOUT_MS))
                    .doOnSuccess(resp -> log.debug("心跳检测请求发送成功 [{}]", spec.getId()))
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

        // 先取消心跳任务，避免遗留任务在重连后重复执行
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
        log.debug("停止HTTP心跳检测 [{}]", spec.getId());
    }

    // ==================== 工具调用相关函数 ====================

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
        // 首先尝试从缓存获取
        if (cachedTools != null) {
            log.debug("从缓存获取 MCP Streamable HTTP 工具列表: {} (共 {} 个工具)", spec.getId(), cachedTools.size());
            return cachedTools;
        }

        // 缓存未命中，从服务器获取
        synchronized (toolsCacheLock) {
            // 双重检查，防止并发请求
            if (cachedTools != null) {
                return cachedTools;
            }

            try {
                log.debug("从服务器获取 MCP Streamable HTTP 工具列表: {}", spec.getId());

                JsonNode request = buildListToolsRequest();
                JsonNode response = sendRequest(request);

                List<MCPTool> tools = parseToolsList(response);
                log.debug("获取到 {} 个 MCP Streamable HTTP 工具: {}", tools.size(), spec.getId());

                // 更新缓存
                cachedTools = tools;

                return tools;

            } catch (Exception e) {
                log.error("获取 MCP Streamable HTTP 工具列表失败: {}", spec.getId(), e);
                // 如果有旧缓存，返回旧缓存；否则返回空列表
                return cachedTools != null ? cachedTools : List.of();
            }
        }
    }



    /**
     * 计算请求 URI：
     * - 若 baseUrl 的路径部分已是 /mcp（即使带有查询参数），直接使用 baseUrl 本身
     * - 否则构造 服务器根 + /mcp，并合并 baseUrl 的查询参数
     * 返回绝对URL，避免 WebClient baseUrl 的相对解析丢失查询参数
     */
    private String calculateRequestUri() {
        String baseUrl = spec.getUrl();
        try {
            java.net.URL url = new java.net.URL(baseUrl);
            String protocol = url.getProtocol();
            String host = url.getHost();
            int port = url.getPort();
            String path = url.getPath(); // 不包含查询参数
            String query = url.getQuery();

            // 构造服务器基础URL
            StringBuilder serverBase = new StringBuilder();
            serverBase.append(protocol).append("://").append(host);
            if (port != -1 && port != 80 && port != 443) {
                serverBase.append(":").append(port);
            }

            // 判断是否已经是 /mcp 端点（容忍末尾斜杠）
            boolean hasMcpPath = path != null && (path.equals("/mcp") || path.endsWith("/mcp") || path.endsWith("/mcp/"));
            String requestPath = hasMcpPath ? path : "/mcp";

            StringBuilder full = new StringBuilder();
            full.append(serverBase).append(requestPath);
            if (query != null && !query.isEmpty()) {
                full.append('?').append(query);
            }

            return full.toString();
        } catch (java.net.MalformedURLException e) {
            // Fallback：字符串方式处理
            return fallbackCalculateRequestUri(baseUrl);
        }
    }

    /**
     * Fallback：当 URL 解析失败时，尽力保留查询参数并指向 /mcp
     */
    private String fallbackCalculateRequestUri(String baseUrl) {
        // 分离路径与查询
        String basePath = baseUrl;
        String baseQuery = null;
        int q = baseUrl.indexOf('?');
        if (q >= 0) {
            basePath = baseUrl.substring(0, q);
            baseQuery = baseUrl.substring(q + 1);
        }
        // 提取协议+主机+端口
        int schemeEnd = basePath.indexOf("://");
        if (schemeEnd < 0) return baseUrl; // 放弃处理
        int hostStart = schemeEnd + 3;
        int pathStart = basePath.indexOf('/', hostStart);
        String serverBase = pathStart >= 0 ? basePath.substring(0, pathStart) : basePath;
        String pathOnly = pathStart >= 0 ? basePath.substring(pathStart) : "";
        boolean hasMcpPath = "/mcp".equals(pathOnly) || pathOnly.endsWith("/mcp") || pathOnly.endsWith("/mcp/");
        String requestPath = hasMcpPath ? pathOnly : "/mcp";
        StringBuilder full = new StringBuilder();
        full.append(serverBase).append(requestPath);
        if (baseQuery != null && !baseQuery.isEmpty()) {
            full.append('?').append(baseQuery);
        }
        return full.toString();
    }
}