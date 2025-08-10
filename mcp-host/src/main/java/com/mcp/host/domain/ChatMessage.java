package com.mcp.host.domain;

import lombok.Data;
import lombok.EqualsAndHashCode;
import java.util.Date;

/**
 * 聊天消息实体类
 * 
 * @author cs
 */
@Data
@EqualsAndHashCode(callSuper = false)
public class ChatMessage {
    private static final long serialVersionUID = 1L;

    /** 消息ID */
    private Long id;

    /** 聊天ID */
    private Long chatId;

    /** 用户ID */
    private Long userId;

    /** 消息序号 */
    private Long sortId;

    /** 发送者角色（1:用户 2:Agent 3:系统） */
    private Integer senderRole;

    /** 思维链内容 */
    private String reasoningContent;

    /** 消息内容 */
    private String messageContent;

    /** 额外内容 */
    private String extraContent;

    /** 消息类型（1:文本 2:图片 3:语音） */
    private Integer messageType = 1;

    /** 删除标志 */
    private Integer delFlag;

    /** 创建时间 */
    private Date createTime;

    /** 更新时间 */
    private Date updateTime;
} 