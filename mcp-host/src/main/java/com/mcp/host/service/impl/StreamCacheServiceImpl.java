package com.mcp.host.service.impl;

import com.mcp.host.service.IStreamCacheService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 流式缓存服务实现类
 *
 * @author cs
 */
@Slf4j
@Service
public class StreamCacheServiceImpl implements IStreamCacheService {

    // 缓存key格式: sessionId_messageId
    private final ConcurrentHashMap<String, StreamCacheData> streamCache = new ConcurrentHashMap<>();
    // Info索引计数器，用于记录info消息的顺序索引
    private final ConcurrentHashMap<String, AtomicInteger> infoIndexCounters = new ConcurrentHashMap<>();

    /**
     * 创建新会话的流式缓存
     */
    @Override
    public String createStreamCache(String sessionId) {
        String messageId = UUID.randomUUID().toString();
        String cacheKey = sessionId + "_" + messageId;

        StreamCacheData cacheData = new StreamCacheData();
        cacheData.setSessionId(sessionId);
        cacheData.setMessageId(messageId);

        streamCache.put(cacheKey, cacheData);

        // 初始化info索引计数器
        infoIndexCounters.put(cacheKey, new AtomicInteger(0));

        log.info("创建流式缓存, sessionId: {}, messageId: {}", sessionId, messageId);

        return messageId;
    }

    /**
     * 添加流式内容块到缓存
     */
    @Override
    public void addStreamChunk(String sessionId, String messageId, String type, Object data) {
        String cacheKey = sessionId + "_" + messageId;
        StreamCacheData cacheData = streamCache.get(cacheKey);

        if (cacheData == null) {
            log.warn("缓存不存在: {}", cacheKey);
            return;
        }

        // 处理info类型的事件，记录到extraContent中
        if ("info".equals(type)) {
            handleInfoEvent(cacheKey, cacheData, data);
        }

        if (data instanceof String) {
            StreamChunk chunk = new StreamChunk(type, (String) data);
            cacheData.addChunk(chunk);

            if ("message".equals(type)) {
                cacheData.appendContent((String) data);
            } else if ("thinking".equals(type)) {
                cacheData.appendReasoningContent((String) data);
            }
        } else if (data instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> dataMap = (Map<String, Object>) data;
            StreamChunk chunk = new StreamChunk(type, dataMap);
            cacheData.addChunk(chunk);

            if ("message".equals(type) && dataMap.containsKey("content")) {
                cacheData.appendContent(dataMap.get("content").toString());
            } else if (("reasoning".equals(type) || "thinking".equals(type)) && dataMap.containsKey("content")) {
                cacheData.appendReasoningContent(dataMap.get("content").toString());
            } else if ("complete".equals(type)) {
                cacheData.setCompleted(true);
                if (dataMap.containsKey("fullContent")) {
                    cacheData.setFullContent(dataMap.get("fullContent").toString());
                }
                if (dataMap.containsKey("reasoningContent")) {
                    cacheData.setReasoningContent(dataMap.get("reasoningContent").toString());
                }
            }
        }
    }

    /**
     * 处理info事件，记录到extraContent中
     */
    private void handleInfoEvent(String cacheKey, StreamCacheData cacheData, Object data) {
        try {
            String infoContent;

            // 提取info内容
            if (data instanceof String) {
                infoContent = (String) data;
            } else if (data instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> dataMap = (Map<String, Object>) data;
                infoContent = dataMap.containsKey("content") ? dataMap.get("content").toString() : data.toString();
            } else {
                infoContent = data.toString();
            }

            // 获取当前info索引
            AtomicInteger indexCounter = infoIndexCounters.get(cacheKey);
            if (indexCounter == null) {
                indexCounter = new AtomicInteger(0);
                infoIndexCounters.put(cacheKey, indexCounter);
            }

            int currentIndex = indexCounter.getAndIncrement();

            // 添加info块到extraContent
            cacheData.addInfoChunk(infoContent, currentIndex);

            log.debug("记录info信息到extraContent: cacheKey={}, index={}, content={}",
                    cacheKey, currentIndex, infoContent);

        } catch (Exception e) {
            log.error("处理info事件失败: cacheKey={}, data={}", cacheKey, data, e);
        }
    }

    /**
     * 标记流式响应完成
     */
    @Override
    public void markStreamCompleted(String sessionId, String messageId, String fullContent, String reasoningContent) {
        String cacheKey = sessionId + "_" + messageId;
        StreamCacheData cacheData = streamCache.get(cacheKey);

        if (cacheData != null) {
            synchronized (cacheData) {
                cacheData.setCompleted(true);
                cacheData.setFullContent(fullContent);
                cacheData.setReasoningContent(reasoningContent);
                cacheData.setUpdateTime(LocalDateTime.now());

                log.info("标记流式响应完成: {}, 总块数: {}, 客户端已接收: {}, 已重放: {}, info块数: {}",
                        cacheKey, cacheData.getChunks().size(), cacheData.getClientReceivedChunkCount(),
                        cacheData.getReplayedChunkCount(), cacheData.getInfoChunks().size());
            }
        }
    }

    /**
     * 存储本次请求的临时回复
     */
    @Override
    public void markToolCall(String sessionId, String messageId, String tempContent) {
        String cacheKey = sessionId + "_" + messageId;
        StreamCacheData cacheData = streamCache.get(cacheKey);

        if (cacheData != null) {
            synchronized (cacheData) {
                cacheData.setTempContent(tempContent);
                cacheData.setUpdateTime(LocalDateTime.now());

            }
        }
    }

    /**
     * 标记流式响应出错
     */
    @Override
    public void markStreamError(String sessionId, String messageId, String errorMessage) {
        String cacheKey = sessionId + "_" + messageId;
        StreamCacheData cacheData = streamCache.get(cacheKey);

        if (cacheData != null) {
            cacheData.setHasError(true);
            cacheData.setErrorMessage(errorMessage);
            cacheData.setCompleted(true); // 出错也算完成
            cacheData.setUpdateTime(LocalDateTime.now());

            log.info("标记流式响应出错: {}, 错误: {}", cacheKey, errorMessage);
        }
    }

    /**
     * 获取缓存数据
     */
    @Override
    public StreamCacheData getStreamCache(String sessionId, String messageId) {
        String cacheKey = sessionId + "_" + messageId;
        return streamCache.get(cacheKey);
    }

    /**
     * 检查流式响应是否完成
     */
    @Override
    public boolean isStreamCompleted(String sessionId, String messageId) {
        StreamCacheData cacheData = getStreamCache(sessionId, messageId);
        return cacheData != null && cacheData.isCompleted();
    }

    /**
     * 获取指定会话的最新缓存
     */
    @Override
    public StreamCacheData getLatestStreamCache(String sessionId) {
        return streamCache.values().stream()
                .filter(data -> sessionId.equals(data.getSessionId()))
                .max(Comparator.comparing(StreamCacheData::getCreateTime))
                .orElse(null);
    }

    /**
     * 获取指定会话的所有未完成缓存
     */
    public List<StreamCacheData> getIncompleteStreams(String sessionId) {
        return streamCache.values().stream()
                .filter(data -> sessionId.equals(data.getSessionId()) && !data.isCompleted())
                .sorted(Comparator.comparing(StreamCacheData::getCreateTime))
                .toList();
    }

    /**
     * 删除缓存
     */
    @Override
    public void removeStreamCache(String sessionId, String messageId) {
        String cacheKey = sessionId + "_" + messageId;
        streamCache.remove(cacheKey);
        // 同时清理info索引计数器
        infoIndexCounters.remove(cacheKey);
        log.info("删除流式缓存: {}", cacheKey);
    }

    /**
     * 清理指定会话的所有缓存
     */
    @Override
    public void clearSessionCache(String sessionId) {
        List<String> keysToRemove = streamCache.keySet().stream()
                .filter(key -> key.startsWith(sessionId + "_"))
                .toList();

        keysToRemove.forEach(key -> {
            streamCache.remove(key);
            infoIndexCounters.remove(key);
        });
        log.info("清理会话缓存: {}, 删除 {} 个缓存", sessionId, keysToRemove.size());
    }

    /**
     * 定时清理过期缓存 (超过1小时的缓存)
     */
    @Scheduled(fixedRate = 300000) // 每5分钟执行一次
    public void cleanExpiredCache() {
        LocalDateTime expireTime = LocalDateTime.now().minusHours(1);

        List<String> expiredKeys = streamCache.entrySet().stream()
                .filter(entry -> entry.getValue().getUpdateTime().isBefore(expireTime))
                .map(Map.Entry::getKey)
                .toList();

        expiredKeys.forEach(key -> {
            streamCache.remove(key);
            infoIndexCounters.remove(key);
        });

        if (!expiredKeys.isEmpty()) {
            log.info("清理过期缓存: {} 个", expiredKeys.size());
        }
    }

    /**
     * 标记缓存已重放到指定位置
     */
    @Override
    public void markCacheReplayed(String sessionId, String messageId, int replayedCount) {
        String cacheKey = sessionId + "_" + messageId;
        StreamCacheData cacheData = streamCache.get(cacheKey);

        if (cacheData != null) {
            cacheData.setReplayedChunkCount(replayedCount);
            cacheData.setUpdateTime(LocalDateTime.now());
            log.debug("标记缓存重放位置: {}, 已重放: {}", cacheKey, replayedCount);
        }
    }

    /**
     * 标记开始重放缓存
     */
    @Override
    public void markReplayStarted(String sessionId, String messageId) {
        String cacheKey = sessionId + "_" + messageId;
        StreamCacheData cacheData = streamCache.get(cacheKey);

        if (cacheData != null) {
            cacheData.setReplaying(true);
            cacheData.setUpdateTime(LocalDateTime.now());
            log.info("开始重放缓存: {}", cacheKey);
        }
    }

    /**
     * 标记重放缓存完成
     */
    @Override
    public void markReplayCompleted(String sessionId, String messageId) {
        String cacheKey = sessionId + "_" + messageId;
        StreamCacheData cacheData = streamCache.get(cacheKey);

        if (cacheData != null) {
            cacheData.setReplaying(false);
            cacheData.setUpdateTime(LocalDateTime.now());
            log.info("重放缓存完成: {}", cacheKey);
        }
    }

    /**
     * 检查缓存是否正在重放
     */
    @Override
    public boolean isReplaying(String sessionId, String messageId) {
        StreamCacheData cacheData = getStreamCache(sessionId, messageId);
        return cacheData != null && cacheData.isReplaying();
    }

    /**
     * 获取客户端未接收的缓存块（用于重连时重放）
     */
    @Override
    public List<StreamChunk> getUnreceivedChunks(String sessionId, String messageId) {
        StreamCacheData cacheData = getStreamCache(sessionId, messageId);
        if (cacheData == null) {
            return new ArrayList<>();
        }

        // 使用同步块确保线程安全
        synchronized (cacheData) {
            List<StreamChunk> allChunks = cacheData.getChunks();
            int receivedCount = cacheData.getClientReceivedChunkCount();

            if (receivedCount >= allChunks.size()) {
                return new ArrayList<>();
            }

            // 创建新的ArrayList而不是返回subList视图，避免ConcurrentModificationException
            return new ArrayList<>(allChunks.subList(receivedCount, allChunks.size()));
        }
    }

    /**
     * 获取未重放的缓存块
     */
    @Override
    public List<StreamChunk> getUnreplayedChunks(String sessionId, String messageId) {
        StreamCacheData cacheData = getStreamCache(sessionId, messageId);
        if (cacheData == null) {
            return new ArrayList<>();
        }

        // 使用同步块确保线程安全
        synchronized (cacheData) {
            List<StreamChunk> allChunks = cacheData.getChunks();
            int replayedCount = cacheData.getReplayedChunkCount();

            if (replayedCount >= allChunks.size()) {
                return new ArrayList<>();
            }

            // 创建新的ArrayList而不是返回subList视图，避免ConcurrentModificationException
            return new ArrayList<>(allChunks.subList(replayedCount, allChunks.size()));
        }
    }

    /**
     * 获取从指定位置开始的新内容块（用于重放完成后发送）
     */
    public List<StreamChunk> getNewChunksFromPosition(String sessionId, String messageId, int fromPosition) {
        StreamCacheData cacheData = getStreamCache(sessionId, messageId);
        if (cacheData == null) {
            return new ArrayList<>();
        }

        // 使用同步块确保线程安全
        synchronized (cacheData) {
            List<StreamChunk> allChunks = cacheData.getChunks();

            if (fromPosition >= allChunks.size()) {
                return new ArrayList<>();
            }

            // 创建新的ArrayList而不是返回subList视图，避免ConcurrentModificationException
            return new ArrayList<>(allChunks.subList(fromPosition, allChunks.size()));
        }
    }

    /**
     * 获取缓存状态统计
     */
    @Override
    public Map<String, Object> getCacheStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalCacheCount", streamCache.size());
        stats.put("completedCount", streamCache.values().stream().mapToLong(data -> data.isCompleted() ? 1 : 0).sum());
        stats.put("errorCount", streamCache.values().stream().mapToLong(data -> data.isHasError() ? 1 : 0).sum());
        stats.put("activeCount", streamCache.values().stream().mapToLong(data -> !data.isCompleted() ? 1 : 0).sum());
        stats.put("replayingCount", streamCache.values().stream().mapToLong(data -> data.isReplaying() ? 1 : 0).sum());
        stats.put("infoIndexCountersCount", infoIndexCounters.size());

        return stats;
    }

    /**
     * 标记客户端成功接收到指定数量的块
     */
    @Override
    public void markClientReceived(String sessionId, String messageId, int receivedCount) {
        String cacheKey = sessionId + "_" + messageId;
        StreamCacheData cacheData = streamCache.get(cacheKey);

        if (cacheData != null) {
            cacheData.setClientReceivedChunkCount(receivedCount);
            cacheData.setUpdateTime(LocalDateTime.now());
            log.debug("标记客户端接收位置: {}, 已接收: {}", cacheKey, receivedCount);
        }
    }
} 