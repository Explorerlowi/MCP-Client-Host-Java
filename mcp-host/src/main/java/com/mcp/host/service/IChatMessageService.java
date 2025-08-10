package com.mcp.host.service;

import com.mcp.host.domain.ChatMessage;
import com.mcp.host.domain.dto.ChatHistoryRequestDto;

import java.util.List;

/**
 * 聊天消息服务接口
 *
 * @author cs
 */
public interface IChatMessageService {

    /**
     * 保存用户消息
     *
     * @param chatId 会话ID
     * @param userId 用户ID
     * @param message 消息内容
     * @param messageType 消息类型
     * @return 聊天消息
     */
    ChatMessage saveUserMessage(Long chatId, Long userId, String message, Integer messageType);

    /**
     * 保存AI消息
     *
     * @param chatId 会话ID
     * @param userId 用户ID
     * @param message 消息内容
     * @param reasoningContent 思维链内容
     * @return 聊天消息
     */
    ChatMessage saveAIMessage(Long chatId, Long userId, String message, String reasoningContent);

    /**
     * 保存AI消息（包含额外内容）
     *
     * @param chatId 会话ID
     * @param userId 用户ID
     * @param message 消息内容
     * @param reasoningContent 思维链内容
     * @param extraContent 额外内容（info信息等）
     * @return 聊天消息
     */
    ChatMessage saveAIMessage(Long chatId, Long userId, String message, String reasoningContent, String extraContent);

    /**
     * 获取会话的上下文消息
     *
     * @param chatId 会话ID
     * @param userId 用户ID
     * @param limit 限制数量
     * @return 消息列表
     */
    List<ChatMessage> getContextMessagesForChat(Long chatId, Long userId, Integer limit);

    /**
     * 获取聊天历史
     *
     * @param request 历史请求参数
     * @return 消息列表
     */
    List<ChatMessage> getChatHistory(ChatHistoryRequestDto request);

    /**
     * 删除单条消息
     *
     * @param messageId 消息ID
     * @param userId 用户ID（权限验证）
     * @return 删除结果
     */
    boolean deleteMessage(Long messageId, Long userId);

    /**
     * 批量删除消息（回溯功能）
     *
     * @param chatId 会话ID
     * @param userId 用户ID（权限验证）
     * @param fromMessageId 从该消息ID开始删除（包含该消息）
     * @return 删除结果
     */
    boolean deleteMessagesFromIndex(Long chatId, Long userId, Long fromMessageId);

    /**
     * 清空会话所有消息
     *
     * @param chatId 会话ID
     * @param userId 用户ID（权限验证）
     * @return 删除结果
     */
    boolean clearChatMessages(Long chatId, Long userId);
} 