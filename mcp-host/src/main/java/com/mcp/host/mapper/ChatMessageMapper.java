package com.mcp.host.mapper;

import com.mcp.host.domain.ChatMessage;
import com.mcp.host.domain.dto.ChatHistoryRequestDto;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 聊天消息Mapper接口
 * 
 * @author cs
 */
@Mapper
public interface ChatMessageMapper {
    
    /**
     * 插入聊天消息
     * 
     * @param chatMessage 聊天消息
     * @return 结果
     */
    int insertChatMessage(ChatMessage chatMessage);
    
    /**
     * 根据用户ID查询消息列表
     * 
     * @param userId 用户ID
     * @return 消息列表
     */
    List<ChatMessage> selectMessagesByUserId(Long userId);
    
    /**
     * 获取用户最近的消息
     * 
     * @param chatId 会话ID
     * @param userId 用户ID
     * @param limit 限制数量
     * @return 消息列表
     */
    List<ChatMessage> selectRecentMessagesByUserId(@Param("chatId") Long chatId,
                                                   @Param("userId") Long userId,
                                                   @Param("limit") Integer limit);
    
    /**
     * 获取会话的上下文消息
     * 
     * @param chatId 会话ID
     * @param userId 用户ID
     * @param limit 限制数量
     * @return 消息列表
     */
    List<ChatMessage> selectContextMessagesForChat(@Param("chatId") Long chatId,
                                                   @Param("userId") Long userId,
                                                   @Param("limit") Integer limit);
    
    /**
     * 获取聊天历史
     * 
     * @param request 历史请求参数
     * @return 消息列表
     */
    List<ChatMessage> selectChatHistory(ChatHistoryRequestDto request);
    
    /**
     * 根据主键查询聊天消息
     * 
     * @param id 主键
     * @return 聊天消息
     */
    ChatMessage selectChatMessageById(Long id);
    
    /**
     * 更新聊天消息
     * 
     * @param chatMessage 聊天消息
     * @return 结果
     */
    int updateChatMessage(ChatMessage chatMessage);
    
    /**
     * 删除聊天消息
     * 
     * @param id 主键
     * @return 结果
     */
    int deleteChatMessageById(Long id);
    
    /**
     * 根据会话ID逻辑删除所有消息
     * 
     * @param chatId 会话ID
     * @return 结果
     */
    int deleteChatMessagesByChatId(Long chatId);

    /**
     * 批量删除消息（从指定消息开始删除）
     * 
     * @param chatId 会话ID
     * @param fromMessageId 从该消息ID开始删除
     * @return 删除的消息数量
     */
    int deleteMessagesFromIndex(@Param("chatId") Long chatId,
                                @Param("fromMessageId") Long fromMessageId);

    /**
     * 根据消息ID和用户ID删除消息（权限验证）
     * 
     * @param messageId 消息ID
     * @param userId 用户ID
     * @return 删除结果
     */
    int deleteMessageByIdAndUserId(@Param("messageId") Long messageId,
                                   @Param("userId") Long userId);

    /**
     * 清空会话所有消息（带权限验证）
     * 
     * @param chatId 会话ID
     * @param userId 用户ID
     * @return 删除的消息数量
     */
    int clearChatMessagesByUserId(@Param("chatId") Long chatId,
                                  @Param("userId") Long userId);
} 