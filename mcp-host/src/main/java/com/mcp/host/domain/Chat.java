package com.mcp.host.domain;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Date;

/**
 * 聊天会话实体类
 *
 * @author cs
 */
@Data
@EqualsAndHashCode(callSuper = false)
public class Chat {
    private static final long serialVersionUID = 1L;

    /** 会话ID */
    private Long chatId;

    /** 用户ID */
    private Long userId;

    /** 该会话上次聊天选择的Agent的ID */
    private Long agentId;

    /** 会话标题 */
    private String chatTitle;

    /** 最后一条消息ID */
    private Long lastMessageId;

    /** 最后消息时间 */
    private Date lastMessageTime;

    /** 未读消息数 */
    private Integer unreadCount = 0;

    /** 是否置顶 0=否 1=是 */
    private Boolean isPinned = false;

    /** 是否星标 0=否 1=是 */
    private Boolean isStarred = false;

    /** 删除标志 0=未删除 1=已删除 */
    private Integer delFlag = 0;

    /** 创建时间 */
    private Date createTime;

    /** 更新时间 */
    private Date updateTime;
}