package com.mcp.host.domain.dto;

import lombok.Data;

/**
 * 聊天历史请求DTO
 * 
 * @author cs
 */
@Data
public class ChatHistoryRequestDto {
    
    /** 会话ID */
    private Long chatId;
    
    /** 用户ID */
    private Long userId;
    
    /** 页码，从1开始 */
    private Integer page = 1;
    
    /** 每页大小，最大100 */
    private Integer size = 20;
    
    /** 最后一条消息的sortId（用于游标分页） */
    private Long lastSortId;
    
    /** 是否倒序排列 */
    private Boolean descOrder = false;
    
    /** 消息类型过滤（1-文本，2-图片，3-文件） */
    private Integer messageType;
    
    /** 发送者角色过滤（1-用户，2-AI助手，3-系统） */
    private Integer senderRole;
} 