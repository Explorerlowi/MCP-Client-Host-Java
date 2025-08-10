package com.mcp.host.config;

import com.mcp.host.domain.Agent;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 固定 Agent 配置，从 application.yml 读取。
 */
@Data
@Component
@ConfigurationProperties(prefix = "agent.fixed")
public class FixedAgentProperties {

    private String agentName;
    private String agentDescription;
    private Boolean chatEnabled = true;
    private String systemPrompt;
    private String llmSupplier;
    private String model;
    private Boolean stream = true;
    private Double temperature = 0.7;
    private Double topP = 1.0;
    private Integer topK;
    private Double presencePenalty = 0.0;
    private Double frequencyPenalty = 0.0;
    private Integer maxTokens = 2048;
    private Boolean enableThinking = false;
    private Integer thinkingBudget;

    /**
     * 转换为领域对象 Agent
     */
    public Agent toAgent() {
        Agent agent = new Agent();
        agent.setAgentName(agentName);
        agent.setAgentDescription(agentDescription);
        agent.setChatEnabled(chatEnabled);
        agent.setSystemPrompt(systemPrompt);
        agent.setLlmSupplier(llmSupplier);
        agent.setModel(model);
        agent.setStream(stream);
        agent.setTemperature(temperature);
        agent.setTopP(topP);
        agent.setTopK(topK);
        agent.setPresencePenalty(presencePenalty);
        agent.setFrequencyPenalty(frequencyPenalty);
        agent.setMaxTokens(maxTokens);
        agent.setEnableThinking(enableThinking);
        agent.setThinkingBudget(thinkingBudget);
        return agent;
    }
}


