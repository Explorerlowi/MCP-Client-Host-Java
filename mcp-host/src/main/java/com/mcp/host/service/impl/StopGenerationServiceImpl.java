package com.mcp.host.service.impl;

import com.mcp.host.service.IStopGenerationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;

/**
 * 停止生成服务实现类
 *
 * @author cs
 */
@Slf4j
@Service
public class StopGenerationServiceImpl implements IStopGenerationService {

    // 存储停止标志，key为任务键，value为停止状态
    private final ConcurrentHashMap<String, Boolean> stopFlags = new ConcurrentHashMap<>();

    @Override
    public void setStopFlag(String sessionId, Long chatId, Long userId) {
        String taskKey = generateTaskKey(sessionId, chatId, userId);
        stopFlags.put(taskKey, true);
        log.info("设置停止标志: taskKey={}", taskKey);
    }

    @Override
    public boolean shouldStopGeneration(String sessionId, Long chatId, Long userId) {
        String taskKey = generateTaskKey(sessionId, chatId, userId);
        boolean shouldStop = stopFlags.getOrDefault(taskKey, false);
        if (shouldStop) {
            log.debug("检测到停止标志: taskKey={}", taskKey);
        }
        return shouldStop;
    }

    @Override
    public void clearStopFlag(String sessionId, Long chatId, Long userId) {
        String taskKey = generateTaskKey(sessionId, chatId, userId);
        stopFlags.remove(taskKey);
        log.info("清除停止标志: taskKey={}", taskKey);
    }

    @Override
    public void clearAllStopFlags() {
        int size = stopFlags.size();
        stopFlags.clear();
        log.info("清除所有停止标志，共 {} 个", size);
    }

    /**
     * 生成任务键
     *
     * @param sessionId 会话ID
     * @param chatId 聊天ID
     * @param userId 用户ID
     * @return 任务键
     */
    private String generateTaskKey(String sessionId, Long chatId, Long userId) {
        return String.format("%s_%d_%d", sessionId, chatId, userId);
    }
}