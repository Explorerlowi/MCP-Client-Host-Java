package com.mcp.host.mapper;

import com.mcp.host.domain.Chat;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Date;
import java.util.List;

/**
 * 聊天会话Mapper接口
 *
 * @author cs
 */
@Mapper
public interface ChatMapper {

    /**
     * 根据用户ID查询会话列表（按时间倒序）
     *
     * @param userId 用户ID
     * @return 会话列表
     */
    List<Chat> selectChatsByUserId(Long userId);

    /**
     * 根据用户名查询会话列表（按时间倒序）
     *
     * @param username 用户名
     * @return 会话列表
     */
    List<Chat> selectChatsByUsername(String username);

    /**
     * 根据会话ID查询会话信息
     *
     * @param chatId 会话ID
     * @return 会话信息
     */
    Chat selectChatById(Long chatId);

    /**
     * 插入新会话
     *
     * @param chat 会话信息
     * @return 结果
     */
    int insertChat(Chat chat);

    /**
     * 更新会话信息
     *
     * @param chat 会话信息
     * @return 结果
     */
    int updateChat(Chat chat);

    /**
     * 更新会话星标状态
     *
     * @param chatId 会话ID
     * @param isStarred 星标状态
     * @return 结果
     */
    int updateChatStarredStatus(@Param("chatId") Long chatId, @Param("isStarred") Boolean isStarred);

    /**
     * 更新会话置顶状态
     *
     * @param chatId 会话ID
     * @param isPinned 置顶状态
     * @return 结果
     */
    int updateChatPinnedStatus(@Param("chatId") Long chatId, @Param("isPinned") Boolean isPinned);

    /**
     * 逻辑删除会话
     *
     * @param chatId 会话ID
     * @return 结果
     */
    int deleteChatById(Long chatId);

    /**
     * 批量逻辑删除会话
     *
     * @param chatIds 会话ID数组
     * @return 结果
     */
    int deleteChatByIds(Long[] chatIds);

    /**
     * 更新会话最后消息信息
     *
     * @param chatId 会话ID
     * @param lastMessageId 最后消息ID
     * @param lastMessageTime 最后消息时间
     * @return 结果
     */
    int updateChatLastMessage(@Param("chatId") Long chatId,
                              @Param("lastMessageId") Long lastMessageId,
                              @Param("lastMessageTime") Date lastMessageTime);

    /**
     * 更新会话未读消息数
     *
     * @param chatId 会话ID
     * @param unreadCount 未读消息数
     * @return 结果
     */
    int updateChatUnreadCount(@Param("chatId") Long chatId, @Param("unreadCount") Integer unreadCount);

    /**
     * 重置会话未读消息数为0
     *
     * @param chatId 会话ID
     * @return 结果
     */
    int resetChatUnreadCount(Long chatId);
}