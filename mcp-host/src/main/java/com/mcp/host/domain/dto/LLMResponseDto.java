package com.mcp.host.domain.dto;

import com.google.gson.JsonArray;
import lombok.Data;

/**
 * LLM响应结果DTO
 * 
 * @author cs
 */
@Data
public class LLMResponseDto {
    private String reasoningContent;
    private String messageContent;
    private int messageType;    // 默认为1: text类型
    private JsonArray extraContent;  // functionCall信息

    public LLMResponseDto() {
        this.reasoningContent = "";
        this.messageContent = "";
        this.messageType = 1;
        this.extraContent = new JsonArray();
    }
} 