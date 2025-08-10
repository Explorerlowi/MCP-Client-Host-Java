package com.mcp.host.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Map;

/**
 * LLM 请求模型
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LLMRequest {
    
    @JsonProperty("system_prompt")
    private String systemPrompt;
    
    @NotBlank(message = "用户消息不能为空")
    @Size(max = 10000, message = "用户消息长度不能超过10000字符")
    @JsonProperty("user_message")
    private String userMessage;
    
    @JsonProperty("conversation_id")
    private String conversationId;
    
    @JsonProperty("parameters")
    private Map<String, Object> parameters;
    
    @JsonProperty("model")
    private String model;
    
    @JsonProperty("temperature")
    private Double temperature;
    
    @JsonProperty("max_tokens")
    private Integer maxTokens;
    
    @JsonProperty("stream")
    @Builder.Default
    private boolean stream = false;
}