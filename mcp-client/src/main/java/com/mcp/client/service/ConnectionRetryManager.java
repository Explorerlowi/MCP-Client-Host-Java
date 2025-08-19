package com.mcp.client.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 连接重试管理器
 * 实现指数退避策略，避免无限重试导致的日志雪崩
 */
@Slf4j
@Component
public class ConnectionRetryManager {

    /**
     * 最大失败次数，超过此次数将被标记为应该放弃
     */
    private static final int MAX_FAILURE_COUNT = 5;
    
    /**
     * 连接失败信息
     */
    public static class FailureInfo {
        private int failureCount = 0;
        private Instant lastFailureTime = Instant.now();
        private Instant nextRetryTime = Instant.now();
        
        public int getFailureCount() { return failureCount; }
        public Instant getLastFailureTime() { return lastFailureTime; }
        public Instant getNextRetryTime() { return nextRetryTime; }
        
        public void recordFailure() {
            failureCount++;
            lastFailureTime = Instant.now();
            
            // 指数退避：1s, 2s, 4s, 8s, 16s, 32s, 60s (最大)
            long backoffSeconds = Math.min(60, (long) Math.pow(2, Math.min(failureCount - 1, 5)));
            nextRetryTime = lastFailureTime.plus(backoffSeconds, ChronoUnit.SECONDS);
            
            log.debug("记录连接失败，失败次数: {}, 下次重试时间: {} ({}秒后)", 
                    failureCount, nextRetryTime, backoffSeconds);
        }
        
        public void reset() {
            failureCount = 0;
            lastFailureTime = null;
            nextRetryTime = Instant.now();
            log.debug("重置连接失败计数");
        }
        
        public boolean canRetry() {
            return Instant.now().isAfter(nextRetryTime);
        }
        
        public boolean shouldGiveUp() {
            // 连续失败达到最大次数后暂时放弃
            return failureCount >= MAX_FAILURE_COUNT;
        }
    }
    
    private final ConcurrentMap<String, FailureInfo> failureInfoMap = new ConcurrentHashMap<>();
    
    /**
     * 检查是否可以重试连接
     */
    public boolean canRetry(String serverId) {
        FailureInfo info = failureInfoMap.get(serverId);
        if (info == null) {
            return true; // 首次连接
        }
        
        if (info.shouldGiveUp()) {
            log.warn("服务器 {} 连续失败 {} 次（达到最大失败次数 {}），暂时放弃重试",
                    serverId, info.getFailureCount(), MAX_FAILURE_COUNT);
            return false;
        }
        
        boolean canRetry = info.canRetry();
        if (!canRetry) {
            log.debug("服务器 {} 仍在退避期内，下次重试时间: {}", serverId, info.getNextRetryTime());
        }
        
        return canRetry;
    }
    
    /**
     * 记录连接失败
     */
    public void recordFailure(String serverId) {
        FailureInfo info = failureInfoMap.computeIfAbsent(serverId, k -> new FailureInfo());
        info.recordFailure();
        
        log.warn("服务器 {} 连接失败，失败次数: {}, 下次重试: {}", 
                serverId, info.getFailureCount(), info.getNextRetryTime());
    }
    
    /**
     * 记录连接成功，重置失败计数
     */
    public void recordSuccess(String serverId) {
        FailureInfo info = failureInfoMap.get(serverId);
        if (info != null) {
            log.info("服务器 {} 连接成功，重置失败计数 (之前失败 {} 次)", serverId, info.getFailureCount());
            info.reset();
        }
    }
    
    /**
     * 获取失败信息
     */
    public FailureInfo getFailureInfo(String serverId) {
        return failureInfoMap.get(serverId);
    }
    
    /**
     * 清除失败信息
     */
    public void clearFailureInfo(String serverId) {
        failureInfoMap.remove(serverId);
        log.debug("清除服务器 {} 的失败信息", serverId);
    }
    
    /**
     * 清除所有失败信息
     */
    public void clearAllFailureInfo() {
        int count = failureInfoMap.size();
        failureInfoMap.clear();
        log.info("清除所有服务器失败信息，共 {} 个", count);
    }
    
    /**
     * 获取统计信息
     */
    public String getStatistics() {
        if (failureInfoMap.isEmpty()) {
            return "无连接失败记录";
        }
        
        StringBuilder sb = new StringBuilder();
        sb.append("连接失败统计:\n");
        
        failureInfoMap.forEach((serverId, info) -> {
            sb.append(String.format("  %s: 失败%d次, 最后失败: %s, 下次重试: %s\n",
                    serverId, info.getFailureCount(), info.getLastFailureTime(), info.getNextRetryTime()));
        });
        
        return sb.toString();
    }
}
