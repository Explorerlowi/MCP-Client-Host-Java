package com.mcp.host.domain;

import lombok.Data;
import java.util.Date;

/**
 * Agent实体类
 * 
 * @author cs
 */
@Data
public class Agent {
    private static final long serialVersionUID = 1L;

    /** Agent ID */
    private Long id;

    /** Agent名称 */
    private String agentName;

    /** Agent描述 */
    private String agentDescription;

    /** 是否启用聊天功能 */
    private Boolean chatEnabled = true;

    /** 系统提示词 */
    private String systemPrompt;

    /** 大模型供应商 */
    private String llmSupplier;

    /** 大模型名称 */
    private String model;

    /** 是否开启流式输出 */
    private Boolean stream = true;

    /** 温度系数 */
    private Double temperature = 0.7;

    /** 核采样的概率阈值 */
    private Double topP = 1.0;

    /** 采样候选集 */
    private Integer topK;

    /** 存在性惩罚 */
    private Double presencePenalty = 0.0;

    /** 频率惩罚度 */
    private Double frequencyPenalty = 0.0;

    /** 最大token数 */
    private Integer maxTokens = 2048;

    /** 是否开启思考模式 */
    private Boolean enableThinking = false;

    /** 思考过程的最大Token长度 */
    private Integer thinkingBudget;

    /** 是否被收藏 */
    private Boolean isPinned = false;

    /** 删除标志 */
    private Integer delFlag;

    /** 创建时间 */
    private Date createTime;

    /** 更新时间 */
    private Date updateTime;
} 