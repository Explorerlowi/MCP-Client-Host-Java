package com.mcp.host.service.impl;

import com.mcp.host.domain.Agent;
import com.mcp.host.domain.ChatMessage;
import com.mcp.host.domain.dto.LLMResponseDto;
import com.mcp.host.mapper.AgentMapper;
import com.mcp.host.config.FixedAgentProperties;
import com.mcp.host.service.IChatMessageService;
import com.mcp.host.service.MCPSystemPromptBuilder;
import com.mcp.host.service.JSONInstructionParser;
import com.mcp.host.service.MCPHostService;
import com.mcp.host.model.MCPInstruction;
import com.mcp.host.service.IStopGenerationService;
import com.mcp.host.service.IStreamCacheService;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import jakarta.annotation.PostConstruct;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.SocketTimeoutException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * LLM大语言模型服务
 *
 * @author cs
 */
@Slf4j
@Service
public class LLMServiceImpl {

    private final OkHttpClient httpClient;
    private final Gson gson;

    @Autowired
    private AgentMapper agentMapper;

    @Autowired
    private FixedAgentProperties fixedAgentProperties;

    @Autowired
    private IStreamCacheService streamCacheService;

    // Note: Knowledge base service is not available in this project

    @Autowired
    private IStopGenerationService stopGenerationService;

    @Autowired
    private MCPSystemPromptBuilder mcpSystemPromptBuilder;

    @Autowired
    private JSONInstructionParser instructionParser;

    @Autowired
    private MCPHostService mcpHostService;

    // 全局变量
    private final Map<String, String> baseUrls = new HashMap<>();
    private final Map<String, String> apiKeys = new HashMap<>();

    private static final ThreadLocal<Agent> agentThreadLocal = ThreadLocal.withInitial(() -> null);
    private static final ThreadLocal<JsonObject> requestBodyJsonThreadLocal = ThreadLocal.withInitial(JsonObject::new);

    public LLMServiceImpl() {
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS) // 连接超时
                .readTimeout(150, TimeUnit.SECONDS)    // 读取超时，两分半
                .writeTimeout(15, TimeUnit.SECONDS)   // 写入超时
                .callTimeout(180, TimeUnit.SECONDS)    // 整个调用的总超时
                .build();
        this.gson = new Gson();
    }

    @Value("${llm.qianwen.url:https://dashscope.aliyuncs.com/compatible-mode/v1}")
    private String qianwenUrl;

    @Value("${llm.qianwen.key:your-api-key}")
    private String qianwenKey;

    @PostConstruct
    private void initializeConfig() {
        // 初始化基础URL配置
        baseUrls.put("通义千问", qianwenUrl);

        // 初始化API密钥配置
        apiKeys.put("通义千问", qianwenKey);
    }

    /**
     * 非流式响应的异步方法
     */
    @Async("asyncExecutor")
    public CompletableFuture<LLMResponseDto> sendMessageToLLMAsync(List<ChatMessage> messages, Long agentId, ChatMessage messageSend) {
        Agent agent = fixedAgentProperties.toAgent();
        agentThreadLocal.set(agent);

        try {
            return CompletableFuture.completedFuture(sendMessageToLLM(messages, messageSend, false));
        } catch (Exception e) {
            log.error("Exception occurred in sendMessageToLLMAsync: {}", e.getMessage(), e);

            LLMResponseDto errorResponse = new LLMResponseDto();
            errorResponse.setMessageContent("Error occurred: " + e.getMessage());
            return CompletableFuture.completedFuture(errorResponse);
        } finally {
            // 清理此方法设置的 ThreadLocal
            agentThreadLocal.remove();
            log.info("Cleaned up agentThreadLocal for thread: {}", Thread.currentThread().getName());
        }
    }

    /**
     * 使用自定义Agent的非流式响应异步方法
     */
    @Async("asyncExecutor")
    public CompletableFuture<LLMResponseDto> sendMessageToLLMAsyncWithCustomAgent(List<ChatMessage> messages, Agent customAgent, ChatMessage messageSend) {
        agentThreadLocal.set(customAgent);

        try {
            return CompletableFuture.completedFuture(sendMessageToLLM(messages, messageSend, false));
        } catch (Exception e) {
            log.error("Exception occurred in sendMessageToLLMAsyncWithCustomAgent: {}", e.getMessage(), e);

            LLMResponseDto errorResponse = new LLMResponseDto();
            errorResponse.setMessageContent("Error occurred: " + e.getMessage());
            return CompletableFuture.completedFuture(errorResponse);
        } finally {
            // 清理此方法设置的 ThreadLocal
            agentThreadLocal.remove();
            log.info("Cleaned up agentThreadLocal for thread: {}", Thread.currentThread().getName());
        }
    }

    /**
     * 支持SSE流式响应的异步方法
     */
    @Async("asyncExecutor")
    public void sendMessageToLLMAsyncWithSSE(
            List<ChatMessage> messages,
            Long agentId,
            ChatMessage messageSend,
            String sessionId,
            String messageId,
            ConcurrentHashMap<String, SseEmitter> sseEmitters,
            Long chatId,
            Long userId,
            IChatMessageService chatMessageService,
            String serversCsv) {

        Agent agent = fixedAgentProperties.toAgent();
        agentThreadLocal.set(agent);

        try {
            sendMessageToLLMWithSSE(messages, messageSend, true, sessionId, messageId, sseEmitters, chatId, userId, chatMessageService, serversCsv);
        } catch (Exception e) {
            log.error("Exception occurred in sendMessageToLLMAsyncWithSSE: {}", e.getMessage(), e);

            // 标记缓存出错
            streamCacheService.markStreamError(sessionId, messageId, "Error occurred: " + e.getMessage());

            // 发送错误消息到SSE
            sendToSSE(sessionId, messageId, sseEmitters, "error", "Error occurred: " + e.getMessage());
        } finally {
            // 清理此方法设置的 ThreadLocal
            agentThreadLocal.remove();
            log.info("Cleaned up agentThreadLocal for thread: {}", Thread.currentThread().getName());
        }
    }

    private JsonObject createRequestBody(List<ChatMessage> messages, ChatMessage messageSend, String serversCsv) {
        JsonObject requestBodyJson = new JsonObject();
        Agent agent = agentThreadLocal.get();

        requestBodyJson.addProperty("model", agent.getModel());
        requestBodyJson.addProperty("temperature", agent.getTemperature());
        requestBodyJson.addProperty("top_p", agent.getTopP());
        requestBodyJson.addProperty("presence_penalty", agent.getPresencePenalty());
        requestBodyJson.addProperty("frequency_penalty", agent.getFrequencyPenalty());
        requestBodyJson.addProperty("max_tokens", agent.getMaxTokens());
        requestBodyJson.addProperty("enable_thinking", agent.getEnableThinking());
        requestBodyJson.addProperty("thinking_budget", agent.getThinkingBudget());

        JsonArray messageArr = new JsonArray();

        String systemPrompt = agent.getSystemPrompt();
        // 构造 MCP 工具调用提示词并加入 systemPrompt（支持服务器过滤）
        try {
            String toolsPrompt;
            if (serversCsv != null && !serversCsv.isBlank()) {
                List<String> serverNames = Arrays.stream(serversCsv.split(","))
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .toList();
                toolsPrompt = mcpSystemPromptBuilder.buildSystemPromptForServers(serverNames);
            } else {
                toolsPrompt = mcpSystemPromptBuilder.buildSystemPromptWithMCPTools();
            }
            if (toolsPrompt != null && !toolsPrompt.isEmpty()) {
                systemPrompt = (systemPrompt == null || systemPrompt.isEmpty())
                        ? toolsPrompt
                        : (systemPrompt + "\n\n" + toolsPrompt);
            }
        } catch (Exception e) {
            log.warn("获取 MCP 工具提示失败，将仅使用原始系统提示: {}", e.getMessage());
        }

        // 添加系统消息
        JsonObject systemMessage = new JsonObject();
        systemMessage.addProperty("role", "system");
        systemMessage.addProperty("content", systemPrompt);
        messageArr.add(systemMessage);

        // 添加历史消息
        if (!messages.isEmpty()) {
            messages.stream()
                    .filter(message -> message.getMessageContent() != null && !message.getMessageContent().isEmpty())
                    .forEach(message -> messageArr.add(createMessageJson(message)));
        }

        // 添加当前用户消息
        messageArr.add(createUserMessageJson(messageSend));

        requestBodyJson.add("messages", messageArr);
        log.info("请求参数构建完成, {} - {}", agent.getLlmSupplier(), agent.getModel());
        return requestBodyJson;
    }

    // 兼容旧调用
    private JsonObject createRequestBody(List<ChatMessage> messages, ChatMessage messageSend) {
        return createRequestBody(messages, messageSend, null);
    }

    private JsonObject createMessageJson(ChatMessage message) {
        String role = mapRoleCodeToDescription(message.getSenderRole());
        String content = message.getMessageContent();
        JsonObject messageObj = new JsonObject();
        messageObj.addProperty("role", role);
        messageObj.addProperty("content", content);
        return messageObj;
    }

    private JsonObject createUserMessageJson(ChatMessage messageSend) {
        JsonObject messageObj = new JsonObject();
        messageObj.addProperty("role", mapRoleCodeToDescription(messageSend.getSenderRole()));
        messageObj.addProperty("content", messageSend.getMessageContent());
        return messageObj;
    }

    private String mapRoleCodeToDescription(Integer senderRole) {
        if (senderRole == null) {
            return "system";
        }
        return switch (senderRole) {
            case 1 -> "user";
            case 2 -> "assistant";
            default -> "system";
        };
    }

    public LLMResponseDto sendMessageToLLM(List<ChatMessage> messages, ChatMessage messageSend, Boolean stream) {
        LLMResponseDto llmResponse = new LLMResponseDto();
        requestBodyJsonThreadLocal.set(createRequestBody(messages, messageSend, null));

        try {
            requestBodyJsonThreadLocal.get().addProperty("stream", Boolean.TRUE.equals(stream));

            String requestBody = gson.toJson(requestBodyJsonThreadLocal.get());
            Agent agent = agentThreadLocal.get();

            Request request = new Request.Builder()
                    .url(baseUrls.get(agent.getLlmSupplier()) + "/chat/completions")
                    .addHeader("Authorization", "Bearer " + apiKeys.get(agent.getLlmSupplier()))
                    .addHeader("Content-Type", "application/json")
                    .post(RequestBody.Companion.create(requestBody, MediaType.parse("application/json")))
                    .build();

            log.info("LLM 请求已发送");

            // 处理非流式响应
            if (!Boolean.TRUE.equals(stream)) {
                try (Response response = httpClient.newCall(request).execute()) {
                    if (!response.isSuccessful()) {
                        llmResponse.setMessageContent(Objects.requireNonNull(response.body()).string());
                        return llmResponse;
                    }

                    handleResponse(response, llmResponse);
                    return llmResponse;
                } catch (SocketTimeoutException e) {
                    llmResponse.setMessageContent("网络连接超时，请稍后再试");
                    return llmResponse;
                } catch (IOException e) {
                    llmResponse.setMessageContent("异次元网络波动，请求未送达，请稍后再试");
                    return llmResponse;
                }
            }
        } finally {
            requestBodyJsonThreadLocal.remove();
            log.info("ThreadLocal variables requestBodyJson cleaned up for thread: {}", Thread.currentThread().getName());
        }

        return llmResponse;
    }

    /**
     * 支持SSE的流式响应方法
     */
    public void sendMessageToLLMWithSSE(
            List<ChatMessage> messages,
            ChatMessage messageSend,
            Boolean stream,
            String sessionId,
            String messageId,
            ConcurrentHashMap<String, SseEmitter> sseEmitters,
            Long chatId,
            Long userId,
            IChatMessageService chatMessageService,
            String serversCsv) {

        requestBodyJsonThreadLocal.set(createRequestBody(messages, messageSend, serversCsv));

        try {
            while (true) {
                requestBodyJsonThreadLocal.get().addProperty("stream", Boolean.TRUE.equals(stream));

                String requestBody = gson.toJson(requestBodyJsonThreadLocal.get());
                Agent agent = agentThreadLocal.get();

                Request request = new Request.Builder()
                        .url(baseUrls.get(agent.getLlmSupplier()) + "/chat/completions")
                        .addHeader("Authorization", "Bearer " + apiKeys.get(agent.getLlmSupplier()))
                        .addHeader("Content-Type", "application/json")
                        .post(RequestBody.Companion.create(requestBody, MediaType.parse("application/json")))
                        .build();

                log.info("LLM 请求已发送 (SSE模式)");

                try {
                    long startTime = System.currentTimeMillis();
                    Response response = httpClient.newCall(request).execute();
                    if (!response.isSuccessful()) {
                        sendToSSE(sessionId, messageId, sseEmitters, "error", Objects.requireNonNull(response.body()).string());
                        return;
                    }

                    StringBuilder reasoningContent = new StringBuilder();
                    StringBuilder fullMessageContent = new StringBuilder();
                    boolean flag = false;
                    boolean sql_flag = false;

                    try (BufferedReader reader = new BufferedReader(new InputStreamReader(Objects.requireNonNull(response.body()).byteStream()))) {
                        String line;
                        while ((line = reader.readLine()) != null) {
                            // 检查停止标志
                            if (shouldStopGeneration(sessionId, chatId, userId)) {
                                log.info("检测到停止标志，中断LLM流式响应: sessionId={}, chatId={}, userId={}", sessionId, chatId, userId);
                                // 保存当前已生成的内容并退出
                                savePartialContentAndStop(sessionId, messageId, sseEmitters, chatId, userId, chatMessageService);
                                return;
                            }

                            if (line.isEmpty()) {
                                continue;
                            }

                            if (!flag) {
                                log.info("首Token延时：{} ms", System.currentTimeMillis() - startTime);
                                flag = true;
                            }

                            // 移除前缀 "data: "
                            String lineStr = line.trim();
                            if (lineStr.startsWith("data: ")) {
                                lineStr = lineStr.substring(6);
                            }

                            // 检查是否是结束标记
                            if (lineStr.equals("[DONE]")) {
                                break;
                            }

                            try {
                                // JSON格式验证
                                if (lineStr.startsWith("{") && lineStr.endsWith("}")) {
                                    JsonObject responseData = gson.fromJson(lineStr, JsonObject.class);
                                    String content = "";

                                    // 处理不同API返回格式
                                    if (responseData.has("choices")) {
                                        // OpenAI兼容格式
                                        JsonObject delta = responseData.getAsJsonArray("choices").get(0).getAsJsonObject().getAsJsonObject("delta");

                                        // 兼容第三方API中转流式返回格式为非流式的问题
                                        if (delta == null) {
                                            delta = responseData.getAsJsonArray("choices").get(0).getAsJsonObject().getAsJsonObject("message");
                                        }

                                        if (delta != null) {
                                            // 处理reasoning_content
                                            if (delta.has("reasoning_content") && !delta.get("reasoning_content").isJsonNull()) {
                                                String reasoning = delta.get("reasoning_content").getAsString();
                                                reasoningContent.append(reasoning);

                                                // 实时发送思维链内容到前端
                                                if (!reasoning.isEmpty()) {
                                                    Map<String, Object> reasoningData = new HashMap<>();
                                                    reasoningData.put("content", reasoning);
                                                    reasoningData.put("type", "reasoning");
                                                    sendToSSE(sessionId, messageId, sseEmitters, "thinking", reasoningData);
                                                }
                                            }

                                            // 处理content
                                            if (delta.has("content") && !delta.get("content").isJsonNull()) {
                                                content = delta.get("content").getAsString();
                                            }
                                        }

                                        if (!content.isEmpty()) {
                                            fullMessageContent.append(content);

                                            Map<String, Object> streamData = new HashMap<>();
                                            streamData.put("content", content);
                                            streamData.put("type", "delta");
                                            sendToSSE(sessionId, messageId, sseEmitters, "message", streamData);
                                        }

                                        // 检查是否结束
                                        if (responseData.getAsJsonArray("choices").get(0).getAsJsonObject().has("finish_reason") &&
                                                !responseData.getAsJsonArray("choices").get(0).getAsJsonObject().get("finish_reason").isJsonNull() &&
                                                responseData.getAsJsonArray("choices").get(0).getAsJsonObject().get("finish_reason").getAsString().equals("stop")) {
                                            break;
                                        }
                                    } else if (responseData.has("message")) {
                                        // Ollama格式
                                        JsonObject message = responseData.getAsJsonObject("message");
                                        if (message != null) {
                                            // 处理reasoning_content
                                            if (message.has("reasoning_content") && !message.get("reasoning_content").isJsonNull()) {
                                                String reasoning = message.get("reasoning_content").getAsString();
                                                reasoningContent.append(reasoning);

                                                // 实时发送思维链内容到前端
                                                if (!reasoning.isEmpty()) {
                                                    Map<String, Object> reasoningData = new HashMap<>();
                                                    reasoningData.put("content", reasoning);
                                                    reasoningData.put("type", "reasoning");
                                                    sendToSSE(sessionId, messageId, sseEmitters, "thinking", reasoningData);
                                                }
                                            }

                                            // 处理content
                                            if (message.has("content") && !message.get("content").isJsonNull()) {
                                                content = message.get("content").getAsString();
                                            }
                                        }

                                        if (!content.isEmpty()) {
                                            fullMessageContent.append(content);

                                            Map<String, Object> streamData = new HashMap<>();
                                            streamData.put("content", content);
                                            streamData.put("type", "delta");
                                            sendToSSE(sessionId, messageId, sseEmitters, "message", streamData);
                                        }

                                        // 检查是否结束
                                        if (responseData.has("done") && responseData.get("done").getAsBoolean()) {
                                            break;
                                        }
                                    }
                                } else {
                                    log.debug("跳过非JSON格式的行: {}", lineStr);
                                }
                            } catch (Exception e) {
                                log.debug("解析流式响应行异常: {}, 行内容: {}", e.getMessage(), lineStr);
                            }
                        }
                    }

                    // 保存AI回复到数据库
                    String reasoningReply = "";
                    String aiReply = "";
                    String extraContentString = "";

                    // 获取extraContent
                    IStreamCacheService.StreamCacheData cacheData = streamCacheService.getStreamCache(sessionId, messageId);

                    if (cacheData != null) {
                        if (cacheData.getReasoningContent() != null && !cacheData.getReasoningContent().isEmpty()) {
                            reasoningReply = cacheData.getReasoningContent();
                            log.info("获取到reasoningContent，长度: {}", reasoningReply.length());
                        }

                        if (cacheData.getFullContent() != null && !cacheData.getFullContent().isEmpty()) {
                            aiReply = cacheData.getFullContent();
                            log.info("获取到fullContent，长度: {}", aiReply.length());
                        }

                        if (cacheData.getExtraContent() != null && !cacheData.getExtraContent().isEmpty()) {
                            extraContentString = cacheData.getExtraContentString();
                            log.info("获取到extraContent，长度: {}", extraContentString.length());
                        }
                    }

                    // 使用现有解析器检查 MCP 工具调用指令（仅检测本轮生成内容）
                    List<MCPInstruction> instructions = instructionParser.parseInstructions(fullMessageContent.toString());
                    boolean isNeedCallTool = !instructions.isEmpty();

                    // 发送完成消息，使用完整内容覆盖缓存
                    Map<String, Object> completeData = new HashMap<>();
                    completeData.put("messageId", messageId);
                    completeData.put("sessionId", sessionId);
                    completeData.put("fullContent", aiReply);
                    completeData.put("reasoningContent", reasoningReply);
                    completeData.put("extraContent", extraContentString);
                    completeData.put("type", isNeedCallTool ? "toolCalling" : "complete");
                    completeData.put("status", isNeedCallTool ? "toolCalling" : "completed");
                    completeData.put("timestamp", System.currentTimeMillis());
                    completeData.put("contentLength", aiReply.length());
                    completeData.put("reasoningLength", reasoningReply.length());
                    completeData.put("isStreamCompleted", !isNeedCallTool);

                    // 对于特殊Agent发送kb_generated事件，其他Agent发送complete事件
                    String eventName = isNeedCallTool ? "toolCalling" : "complete";
                    sendToSSE(sessionId, messageId, sseEmitters, eventName, completeData);

                    // 标记缓存状态：特殊的响应不标记为完全完成
                    if (isNeedCallTool) {
                        // 对于特殊，只标记内容完整，但不标记为完全完成
                        streamCacheService.markToolCall(sessionId, messageId, fullMessageContent.toString());
                        log.info("Agent第一次响应完成: sessionId={}, messageId={}, contentLength={}, reasoningLength={}",
                                sessionId, messageId, aiReply.length(), reasoningReply.length());
                    } else {
                        // 对于其他Agent，正常标记为完成
                        streamCacheService.markStreamCompleted(sessionId, messageId, aiReply, reasoningReply);
                        try {
                            ChatMessage aiMessage = chatMessageService.saveAIMessage(chatId, userId, aiReply, reasoningReply, extraContentString);
                            log.info("AI消息已保存到数据库，ID: {}", aiMessage.getId());
                        } catch (Exception e) {
                            log.error("保存AI消息到数据库失败: {}", e.getMessage(), e);
                        }
                    }

                    // 如存在指令：执行工具并把结果替换回全文，并将结果注入上下文后再次请求
                    if (isNeedCallTool) {
                        try {
                            sendToSSE(sessionId, messageId, sseEmitters, "info", "检测到 MCP 工具指令，开始执行…");
                            String processed = mcpHostService.processMCPInstructions(fullMessageContent.toString(), instructions);

                            // 1) 将本轮大模型回复作为 assistant 消息加入 ThreadLocal 中的 messages 数组
                            JsonObject assistantJson = new JsonObject();
                            assistantJson.addProperty("role", "assistant");
                            assistantJson.addProperty("content", fullMessageContent.toString());
                            requestBodyJsonThreadLocal.get().getAsJsonArray("messages").add(assistantJson);

                            // 2) 以 user 身份告知工具执行结果与后续指引
                            StringBuilder followUp = new StringBuilder();
                            followUp.append("下面是你刚才请求的工具执行结果。请严格按以下要求继续：\n\n");
                            followUp.append("- 基于结果继续完成任务，判断工具调用结果是否满足用户需求，如果满足，直接给出最终答复，如果不满足，思考工具调用指令是否存在问题，如果存在问题，思考如何修改工具调用指令并再次请求工具，如果工具调用指令没有问题，向用户解释工具调用结果不满足需求的原因，并表达歉意，不需要再次请求工具。\n\n");
                            followUp.append(processed != null ? processed : "(无)\n");

                            JsonObject userJson = new JsonObject();
                            userJson.addProperty("role", "user");
                            userJson.addProperty("content", followUp.toString());
                            requestBodyJsonThreadLocal.get().getAsJsonArray("messages").add(userJson);

                            // 继续下一轮循环
                            continue;
                        } catch (Exception toolEx) {
                            log.error("执行 MCP 工具失败: {}", toolEx.getMessage(), toolEx);
                            sendToSSE(sessionId, messageId, sseEmitters, "error", "工具执行失败: " + toolEx.getMessage());
                        }
                    }

                } catch (SocketTimeoutException e) {
                    String errorMsg = "异次元网络波动，请求未送达，请稍后再试";
                    sendToSSE(sessionId, messageId, sseEmitters, "error", errorMsg);
                    streamCacheService.markStreamError(sessionId, messageId, errorMsg);
                } catch (IOException e) {
                    String errorMsg = "异次元网络波动，请求未送达，请稍后再试";
                    sendToSSE(sessionId, messageId, sseEmitters, "error", errorMsg);
                    streamCacheService.markStreamError(sessionId, messageId, errorMsg);
                    log.error("流式请求异常", e);
                }

                break;
            }
        } finally {
            // 清理在此方法作用域内设置的 ThreadLocal
            requestBodyJsonThreadLocal.remove();
            log.info("ThreadLocal variables cleaned up for thread: {}", Thread.currentThread().getName());
        }
    }

    /**
     * 发送消息到SSE
     */
    private void sendToSSE(String sessionId, String messageId, ConcurrentHashMap<String, SseEmitter> sseEmitters, String eventName, Object data) {
        IStreamCacheService.StreamCacheData cacheData = null;

        // 先缓存数据
        if (messageId != null) {
            streamCacheService.addStreamChunk(sessionId, messageId, eventName, data);
            // 获取当前缓存数据
            cacheData = streamCacheService.getStreamCache(sessionId, messageId);
            if (cacheData != null) {
                if (cacheData.getExtraContent() != null && !cacheData.getExtraContent().isEmpty() && eventName.equals("info")) {
                    data = cacheData.getExtraContentString();
                }
            }
        }

        final Object tempData = data;

        // 检查是否正在重放缓存
        if (messageId != null && streamCacheService.isReplaying(sessionId, messageId)) {
            log.debug("正在重放缓存，暂停发送新内容到SSE: sessionId={}, messageId={}, eventName={}", sessionId, messageId, eventName);
            return;
        }

        // 检查是否有活跃的SSE连接
        boolean hasActiveConnection = sseEmitters.entrySet().stream()
                .anyMatch(entry -> entry.getKey().startsWith(sessionId + "_"));

        if (!hasActiveConnection && messageId != null) {
            // 如果没有活跃连接，只缓存数据，不发送
            log.debug("没有活跃的SSE连接，仅缓存数据: sessionId={}, messageId={}, eventName={}", sessionId, messageId, eventName);
            return;
        }

        // 发送到活跃的SSE连接
        final boolean[] sentSuccessfully = {false}; // 使用数组来解决final问题
        sseEmitters.entrySet().removeIf(entry -> {
            String emitterId = entry.getKey();
            SseEmitter emitter = entry.getValue();

            if (emitterId.startsWith(sessionId + "_")) {
                try {
                    emitter.send(SseEmitter.event()
                            .name(eventName)
                            .data(tempData));
                    sentSuccessfully[0] = true; // 标记发送成功
                    return false; // 保留连接
                } catch (Exception e) {
                    // 客户端断开连接是正常情况，降低日志级别
                    if (e.getCause() instanceof java.io.IOException) {
                        log.debug("SSE连接已断开，emitterId: {}", emitterId);
                    } else {
                        log.error("发送SSE消息失败，emitterId: {}", emitterId, e);
                    }
                    return true; // 移除失效连接
                }
            }
            return false;
        });

        // 只有在实际成功发送给客户端时，才增加客户端接收计数
        if (messageId != null && sentSuccessfully[0]) {
            if (cacheData != null) {
                // 增量更新客户端接收计数，而不是设置为总数
                int currentReceivedCount = cacheData.getClientReceivedChunkCount();
                streamCacheService.markClientReceived(sessionId, messageId, currentReceivedCount + 1);
                // log.debug("客户端接收计数更新: sessionId={}, messageId={}, 从 {} 增加到 {}",
                //         sessionId, messageId, currentReceivedCount, currentReceivedCount + 1);
            }
        }
    }

    private void handleResponse(Response response, LLMResponseDto llmResponse) throws IOException {
        String responseBody = Objects.requireNonNull(response.body()).string();
        JsonObject responseJson = gson.fromJson(responseBody, JsonObject.class);

        if (responseJson.has("choices")) {
            JsonArray choicesArray = responseJson.getAsJsonArray("choices");
            if (choicesArray != null && !choicesArray.isEmpty()) {
                JsonObject messageObject = choicesArray.get(0).getAsJsonObject().getAsJsonObject("message");

                if (messageObject.has("reasoning_content") && !messageObject.get("reasoning_content").isJsonNull()) {
                    llmResponse.setReasoningContent(messageObject.get("reasoning_content").getAsString());
                }

                if (messageObject.has("content") && !messageObject.get("content").isJsonNull()) {
                    String content = messageObject.get("content").getAsString();
                    llmResponse.setMessageContent(content);
                }
            }
        }
    }

    /**
     * 检查是否应该停止生成
     */
    private boolean shouldStopGeneration(String sessionId, Long chatId, Long userId) {
        return stopGenerationService.shouldStopGeneration(sessionId, chatId, userId);
    }

    /**
     * 保存部分内容并停止
     */
    private void savePartialContentAndStop(String sessionId, String messageId,
                                           ConcurrentHashMap<String, SseEmitter> sseEmitters, Long chatId, Long userId, IChatMessageService chatMessageService) {
        try {
            log.info("保存停止时的部分内容");

            // 保存AI回复到数据库
            String reasoningReply = "";
            String aiReply = "";
            String extraContentString = "";

            // 获取extraContent
            IStreamCacheService.StreamCacheData cacheData = streamCacheService.getStreamCache(sessionId, messageId);

            if (cacheData != null) {
                if (cacheData.getReasoningContent() != null && !cacheData.getReasoningContent().isEmpty()) {
                    reasoningReply = cacheData.getReasoningContent();
                    log.info("停止时获取到reasoningContent，长度: {}", reasoningReply.length());
                }

                if (cacheData.getFullContent() != null && !cacheData.getFullContent().isEmpty()) {
                    aiReply = cacheData.getFullContent();
                    log.info("停止时获取到fullContent，长度: {}", aiReply.length());
                }

                if (cacheData.getExtraContent() != null && !cacheData.getExtraContent().isEmpty()) {
                    extraContentString = cacheData.getExtraContentString();
                    log.info("停止时获取到extraContent，长度: {}", extraContentString.length());
                }
            }

            // 发送停止完成事件到前端
            Map<String, Object> stoppedData = new HashMap<>();
            stoppedData.put("messageId", messageId);
            stoppedData.put("sessionId", sessionId);
            stoppedData.put("fullContent", aiReply);
            stoppedData.put("reasoningContent", reasoningReply);
            stoppedData.put("extraContent", extraContentString);
            stoppedData.put("type", "stopped");
            stoppedData.put("status", "stopped");
            stoppedData.put("timestamp", System.currentTimeMillis());
            stoppedData.put("contentLength", aiReply.length());
            stoppedData.put("reasoningLength", reasoningReply.length());
            stoppedData.put("isStreamCompleted", true);
            stoppedData.put("wasStopped", true);

            sendToSSE(sessionId, messageId, sseEmitters, "stopped", stoppedData);

            // 标记缓存为已完成（停止状态）
            streamCacheService.markStreamCompleted(sessionId, messageId, aiReply, reasoningReply);

            // 保存部分生成的内容到数据库
            try {
                ChatMessage aiMessage = chatMessageService.saveAIMessage(chatId, userId, aiReply, reasoningReply, extraContentString);
                log.info("停止时的AI消息已保存到数据库，ID: {}", aiMessage.getId());
            } catch (Exception e) {
                log.error("保存停止时的AI消息到数据库失败: {}", e.getMessage(), e);
            }

            // 清除停止标志（停止处理完成）
            stopGenerationService.clearStopFlag(sessionId, chatId, userId);
            log.info("停止处理完成，已清除停止标志: sessionId={}, chatId={}, userId={}", sessionId, chatId, userId);

        } catch (Exception e) {
            log.error("保存部分内容并停止失败: {}", e.getMessage(), e);
            // 发送错误事件
            sendToSSE(sessionId, messageId, sseEmitters, "error", "停止处理失败: " + e.getMessage());
        }
    }
} 