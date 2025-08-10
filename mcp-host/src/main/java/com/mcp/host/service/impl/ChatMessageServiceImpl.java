package com.mcp.host.service.impl;

import com.mcp.host.domain.ChatMessage;
import com.mcp.host.domain.dto.ChatHistoryRequestDto;
import com.mcp.host.mapper.ChatMessageMapper;
//import com.cs.system.mapper.ChatMapper;
import com.mcp.host.service.IChatMessageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;

/**
 * 聊天消息服务实现类
 *
 * @author cs
 */
@Slf4j
@Service
public class ChatMessageServiceImpl implements IChatMessageService {

    @Autowired
    private ChatMessageMapper chatMessageMapper;

//    @Autowired
//    private ChatMapper chatMapper;

    /**
     * 保存用户消息
     */
    @Override
    @Transactional
    public ChatMessage saveUserMessage(Long chatId, Long userId, String messageContent, Integer messageType) {
        ChatMessage chatMessage = new ChatMessage();
        chatMessage.setChatId(chatId);
        chatMessage.setUserId(userId);
        chatMessage.setMessageContent(messageContent);
        chatMessage.setMessageType(messageType);
        chatMessage.setSenderRole(1); // 用户
        chatMessage.setDelFlag(0);

        // 生成sortId：使用当前时间戳 + 随机数确保唯一性
        long sortId = System.currentTimeMillis() * 1000 + (long)(Math.random() * 1000);
        chatMessage.setSortId(sortId);

        // 保存消息
        chatMessageMapper.insertChatMessage(chatMessage);
        log.info("保存用户消息成功，ID: {}, sortId: {}", chatMessage.getId(), chatMessage.getSortId());

        // 同步更新chat表
        updateChatLastMessage(chatId, chatMessage.getId(), new Date());

        return chatMessage;
    }

    /**
     * 保存AI消息
     */
    @Override
    @Transactional
    public ChatMessage saveAIMessage(Long chatId, Long userId, String messageContent, String reasoningContent) {
        return saveAIMessage(chatId, userId, messageContent, reasoningContent, null);
    }

    /**
     * 保存AI消息（包含额外内容）
     */
    @Override
    @Transactional
    public ChatMessage saveAIMessage(Long chatId, Long userId, String messageContent, String reasoningContent, String extraContent) {
        ChatMessage chatMessage = new ChatMessage();
        chatMessage.setChatId(chatId);
        chatMessage.setUserId(userId);
        chatMessage.setMessageContent(messageContent);
        chatMessage.setReasoningContent(reasoningContent);
        chatMessage.setExtraContent(extraContent);
        chatMessage.setMessageType(1); // 文本
        chatMessage.setSenderRole(2); // AI助手
        chatMessage.setDelFlag(0);

        // 生成sortId：使用当前时间戳 + 随机数确保唯一性
        long sortId = System.currentTimeMillis() * 1000 + (long)(Math.random() * 1000);
        chatMessage.setSortId(sortId);

        // 保存消息
        chatMessageMapper.insertChatMessage(chatMessage);

        // 记录日志
        if (extraContent != null && !extraContent.trim().isEmpty()) {
            log.info("保存AI消息成功，ID: {}, sortId: {}, 包含extraContent长度: {}",
                    chatMessage.getId(), chatMessage.getSortId(), extraContent.length());
        } else {
            log.info("保存AI消息成功，ID: {}, sortId: {}", chatMessage.getId(), chatMessage.getSortId());
        }

        // 同步更新chat表
        updateChatLastMessage(chatId, chatMessage.getId(), new Date());

        return chatMessage;
    }

    /**
     * 获取会话的上下文消息
     */
    @Override
    public List<ChatMessage> getContextMessagesForChat(Long chatId, Long userId, Integer limit) {
        return chatMessageMapper.selectContextMessagesForChat(chatId, userId, limit);
    }

    /**
     * 获取聊天历史
     */
    @Override
    public List<ChatMessage> getChatHistory(ChatHistoryRequestDto request) {
        return chatMessageMapper.selectChatHistory(request);
    }

    /**
     * 删除单条消息
     */
    @Override
    @Transactional
    public boolean deleteMessage(Long messageId, Long userId) {
        try {
            int result = chatMessageMapper.deleteMessageByIdAndUserId(messageId, userId);
            log.info("删除单条消息，消息ID: {}, 用户ID: {}, 删除结果: {}", messageId, userId, result > 0 ? "成功" : "失败");
            return result > 0;
        } catch (Exception e) {
            log.error("删除消息失败，消息ID: {}, 用户ID: {}", messageId, userId, e);
            return false;
        }
    }

    /**
     * 批量删除消息（回溯功能）
     */
    @Override
    @Transactional
    public boolean deleteMessagesFromIndex(Long chatId, Long userId, Long fromMessageId) {
        try {
            // 先验证该消息是否属于该用户
            ChatMessage message = chatMessageMapper.selectChatMessageById(fromMessageId);
            if (message == null || !userId.equals(message.getUserId()) || !chatId.equals(message.getChatId())) {
                log.warn("无权限删除消息，消息ID: {}, 用户ID: {}, 会话ID: {}", fromMessageId, userId, chatId);
                return false;
            }

            int result = chatMessageMapper.deleteMessagesFromIndex(chatId, fromMessageId);
            log.info("批量删除消息，会话ID: {}, 从消息ID: {}, 删除数量: {}", chatId, fromMessageId, result);

            // ChatMessage previousMessage = chatMessageMapper.selectChatMessageById(fromMessageId);
            // 更新chat表的最后消息时间
            updateChatLastMessage(chatId, null, new Date());

            return result > 0;
        } catch (Exception e) {
            log.error("批量删除消息失败，会话ID: {}, 从消息ID: {}", chatId, fromMessageId, e);
            return false;
        }
    }

    /**
     * 清空会话所有消息
     */
    @Override
    @Transactional
    public boolean clearChatMessages(Long chatId, Long userId) {
        try {
            int result = chatMessageMapper.clearChatMessagesByUserId(chatId, userId);
            log.info("清空会话消息，会话ID: {}, 用户ID: {}, 删除数量: {}", chatId, userId, result);

            // 更新chat表的最后消息时间
            updateChatLastMessage(chatId, null, new Date());

            return result > 0;
        } catch (Exception e) {
            log.error("清空会话消息失败，会话ID: {}, 用户ID: {}", chatId, userId, e);
            return false;
        }
    }

    /**
     * 更新chat表的最后消息信息
     *
     * @param chatId 会话ID
     * @param lastMessageId 最后消息ID
     * @param lastMessageTime 最后消息时间
     */
    private void updateChatLastMessage(Long chatId, Long lastMessageId, Date lastMessageTime) {
        try {
//            chatMapper.updateChatLastMessage(chatId, lastMessageId, lastMessageTime);
            log.debug("同步更新chat表成功，chatId: {}, lastMessageId: {}", chatId, lastMessageId);
        } catch (Exception e) {
            log.error("同步更新chat表失败，chatId: {}, lastMessageId: {}", chatId, lastMessageId, e);
            // 这里不抛出异常，避免影响消息保存的主要流程
        }
    }
} 