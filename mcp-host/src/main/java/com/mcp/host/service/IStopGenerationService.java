package com.mcp.host.service;

/**
 * 停止生成服务接口
 *
 * @author cs
 */
public interface IStopGenerationService {

    /**
     * 设置停止标志
     *
     * @param sessionId 会话ID
     * @param chatId 聊天ID
     * @param userId 用户ID
     */
    void setStopFlag(String sessionId, Long chatId, Long userId);

    /**
     * 检查是否需要停止生成
     *
     * @param sessionId 会话ID
     * @param chatId 聊天ID
     * @param userId 用户ID
     * @return 是否需要停止
     */
    boolean shouldStopGeneration(String sessionId, Long chatId, Long userId);

    /**
     * 清除停止标志
     *
     * @param sessionId 会话ID
     * @param chatId 聊天ID
     * @param userId 用户ID
     */
    void clearStopFlag(String sessionId, Long chatId, Long userId);

    /**
     * 清理所有停止标志
     */
    void clearAllStopFlags();
}