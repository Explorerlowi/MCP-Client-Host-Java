package com.mcp.host.service.chat;

import com.mcp.host.service.IStreamCacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicLong;

/**
 * SSE连接管理服务
 * 
 * 职责：
 * 1. 管理SSE连接的创建和销毁
 * 2. 处理连接重连和缓存重放
 * 3. 维护连接状态
 * 4. 处理连接异常
 *
 * @author cs
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SseConnectionService {

    private final IStreamCacheService streamCacheService;

    @Qualifier("sseCacheExecutor")
    private final Executor sseCacheExecutor;

    // 存储活跃的SSE连接
    private final ConcurrentHashMap<String, SseEmitter> sseEmitters = new ConcurrentHashMap<>();
    private final AtomicLong emitterIdGenerator = new AtomicLong(0);

    // SSE连接超时时间（5分钟）
    private static final long SSE_TIMEOUT = 300_000L;

    /**
     * 创建SSE连接
     */
    public SseEmitter createConnection(String sessionId, HttpServletResponse response) {
        log.info("创建SSE连接: sessionId={}", sessionId);

        try {
            // 设置响应头
            configureResponseHeaders(response);

            // 创建SSE发射器
            SseEmitter emitter = new SseEmitter(SSE_TIMEOUT);
            
            // 生成唯一的emitter ID
            String emitterId = generateEmitterId(sessionId);
            sseEmitters.put(emitterId, emitter);

            log.info("SSE连接已建立: sessionId={}, emitterId={}", sessionId, emitterId);

            // 发送连接成功消息
            sendConnectionSuccessMessage(emitter, emitterId);

            // 设置连接事件处理器
            setupConnectionHandlers(emitter, emitterId, sessionId);

            // 异步检查并重放缓存
            checkAndReplayCacheAsync(sessionId, emitter);

            return emitter;

        } catch (Exception e) {
            log.error("创建SSE连接失败: sessionId={}", sessionId, e);
            throw new RuntimeException("创建SSE连接失败: " + e.getMessage(), e);
        }
    }

    /**
     * 发送消息到指定会话的所有连接
     */
    public void sendToSession(String sessionId, String eventName, Object data) {
        sseEmitters.entrySet().removeIf(entry -> {
            String emitterId = entry.getKey();
            SseEmitter emitter = entry.getValue();

            if (emitterId.startsWith(sessionId + "_")) {
                try {
                    emitter.send(SseEmitter.event()
                            .name(eventName)
                            .data(data));
                    return false; // 保留连接
                } catch (Exception e) {
                    log.error("发送SSE消息失败: emitterId={}", emitterId, e);
                    return true; // 移除失效连接
                }
            }
            return false;
        });
    }

    /**
     * 获取会话的活跃连接数
     */
    public int getActiveConnectionCount(String sessionId) {
        return (int) sseEmitters.keySet().stream()
                .filter(emitterId -> emitterId.startsWith(sessionId + "_"))
                .count();
    }

    /**
     * 关闭会话的所有连接
     */
    public void closeSessionConnections(String sessionId) {
        sseEmitters.entrySet().removeIf(entry -> {
            String emitterId = entry.getKey();
            if (emitterId.startsWith(sessionId + "_")) {
                try {
                    entry.getValue().complete();
                } catch (Exception e) {
                    log.warn("关闭SSE连接异常: emitterId={}", emitterId, e);
                }
                return true;
            }
            return false;
        });
        log.info("已关闭会话的所有SSE连接: sessionId={}", sessionId);
    }

    /**
     * 配置响应头
     */
    private void configureResponseHeaders(HttpServletResponse response) {
        response.setContentType("text/event-stream");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Cache-Control", "no-cache");
        response.setHeader("Connection", "keep-alive");
        response.setHeader("Access-Control-Allow-Origin", "*");
        response.setHeader("Access-Control-Allow-Methods", "GET");
        response.setHeader("Access-Control-Allow-Headers", "Cache-Control");
    }

    /**
     * 生成emitter ID
     */
    private String generateEmitterId(String sessionId) {
        return sessionId + "_" + emitterIdGenerator.incrementAndGet();
    }

    /**
     * 发送连接成功消息
     */
    private void sendConnectionSuccessMessage(SseEmitter emitter, String emitterId) {
        try {
            emitter.send(SseEmitter.event()
                    .id(emitterId)
                    .name("connected")
                    .data("连接成功"));
        } catch (Exception e) {
            log.error("发送连接成功消息失败: emitterId={}", emitterId, e);
        }
    }

    /**
     * 设置连接事件处理器
     */
    private void setupConnectionHandlers(SseEmitter emitter, String emitterId, String sessionId) {
        // 连接完成处理
        emitter.onCompletion(() -> {
            sseEmitters.remove(emitterId);
            log.info("SSE连接已完成: emitterId={}", emitterId);
        });

        // 连接超时处理
        emitter.onTimeout(() -> {
            sseEmitters.remove(emitterId);
            log.info("SSE连接超时: emitterId={}", emitterId);
        });

        // 连接错误处理
        emitter.onError((ex) -> {
            sseEmitters.remove(emitterId);
            handleConnectionError(ex, emitterId);
        });
    }

    /**
     * 处理连接错误
     */
    private void handleConnectionError(Throwable ex, String emitterId) {
        // 客户端断开连接是正常情况，降低日志级别
        if (ex instanceof java.io.IOException || 
            (ex.getCause() != null && ex.getCause() instanceof java.io.IOException)) {
            log.debug("SSE连接已断开: emitterId={}", emitterId);
        } else {
            log.error("SSE连接发生错误: emitterId={}", emitterId, ex);
        }
    }

    /**
     * 异步检查并重放缓存
     */
    private void checkAndReplayCacheAsync(String sessionId, SseEmitter emitter) {
        CompletableFuture.runAsync(() -> {
            try {
                checkAndReplayCache(sessionId, emitter);
            } catch (Exception e) {
                log.error("异步重放缓存失败: sessionId={}", sessionId, e);
            }
        }, sseCacheExecutor);
    }

    /**
     * 检查并重放缓存内容
     */
    private void checkAndReplayCache(String sessionId, SseEmitter emitter) {
        try {
            // 获取该会话最新的缓存（无论是否完成）
            IStreamCacheService.StreamCacheData latestCache = streamCacheService.getLatestStreamCache(sessionId);

            if (latestCache != null && !latestCache.getChunks().isEmpty()) {
                log.info("发现缓存内容: sessionId={}, messageId={}, 已完成={}, 块数={}, 客户端已接收={}",
                        sessionId, latestCache.getMessageId(), latestCache.isCompleted(),
                        latestCache.getChunks().size(), latestCache.getClientReceivedChunkCount());

                // 检查客户端是否还有未接收的内容
                List<IStreamCacheService.StreamChunk> unreceivedChunks =
                        streamCacheService.getUnreceivedChunks(sessionId, latestCache.getMessageId());

                if (unreceivedChunks.isEmpty()) {
                    log.info("客户端已接收所有内容，跳过重放: sessionId={}, messageId={}",
                            sessionId, latestCache.getMessageId());

                    // 发送连接恢复提示但不重放内容
                    emitter.send(SseEmitter.event()
                            .name("reconnected")
                            .data("连接已恢复，当前消息已完成"));

                    // 如果响应已完成，可以清理缓存
                    if (latestCache.isCompleted()) {
                        CompletableFuture.runAsync(() -> {
                            try {
                                Thread.sleep(5000); // 等待5秒
                                streamCacheService.removeStreamCache(sessionId, latestCache.getMessageId());
                                log.info("已清理完成的缓存: sessionId={}, messageId={}", sessionId, latestCache.getMessageId());
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                            }
                        }, sseCacheExecutor);
                    }
                    return;
                }

                log.info("客户端有 {} 个未接收的块需要重放: sessionId={}, messageId={}",
                        unreceivedChunks.size(), sessionId, latestCache.getMessageId());

                // 发送重连恢复提示
                emitter.send(SseEmitter.event()
                        .name("reconnected")
                        .data("连接已恢复，正在重放缓存内容..."));

                // 重放缓存内容
                replayCacheContent(latestCache, emitter, unreceivedChunks);
            }
        } catch (Exception e) {
            log.error("检查并重放缓存失败: sessionId={}", sessionId, e);
        }
    }

    /**
     * 重放缓存内容
     */
    private void replayCacheContent(IStreamCacheService.StreamCacheData cacheData,
                                   SseEmitter emitter,
                                   List<IStreamCacheService.StreamChunk> unreceivedChunks) {
        try {
            int replayedCount = 0;
            log.debug("开始重放，未接收块数: {}", unreceivedChunks.size());

            for (IStreamCacheService.StreamChunk chunk : unreceivedChunks) {
                try {
                    Object data = chunk.getData() != null ? chunk.getData() : chunk.getContent();

                    // 发送数据
                    emitter.send(SseEmitter.event()
                            .name(chunk.getType())
                            .data(data));

                    replayedCount++;

                    // 流式发送的延迟，让前端有更好的体验
                    if ("thinking".equals(chunk.getType())) {
                        Thread.sleep(20); // 思考内容快速显示
                    } else if ("message".equals(chunk.getType())) {
                        Thread.sleep(80); // 消息内容慢速显示，模拟打字效果
                    } else {
                        Thread.sleep(50); // 其他类型使用中等延迟
                    }

                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    log.warn("重放缓存内容被中断");
                    break;
                } catch (Exception e) {
                    log.error("重放缓存块失败: type={}, content={}", chunk.getType(), chunk.getContent(), e);
                    break;
                }
            }

            // 更新客户端接收的块数
            if (replayedCount > 0) {
                int newClientReceivedCount = cacheData.getClientReceivedChunkCount() + replayedCount;
                streamCacheService.markClientReceived(cacheData.getSessionId(),
                        cacheData.getMessageId(), newClientReceivedCount);
            }

            log.info("重放完成，共重放 {} 个块", replayedCount);

        } catch (Exception e) {
            log.error("重放缓存内容失败", e);
            try {
                emitter.send(SseEmitter.event()
                        .name("error")
                        .data("重放缓存内容失败: " + e.getMessage()));
            } catch (Exception ex) {
                log.error("发送错误消息失败", ex);
            }
        }
    }

    /**
     * 获取SSE发射器映射（供其他服务使用）
     */
    public ConcurrentHashMap<String, SseEmitter> getSseEmitters() {
        return sseEmitters;
    }
}
