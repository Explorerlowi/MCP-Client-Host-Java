package com.mcp.host.service.chat;

import com.mcp.host.domain.ChatMessage;
import com.mcp.host.domain.dto.LLMResponseDto;
import com.mcp.host.service.IChatMessageService;
import com.mcp.host.service.IStreamCacheService;
import com.mcp.host.service.IStopGenerationService;

import com.mcp.host.service.impl.LLMServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * 聊天编排服务
 * 
 * 职责：
 * 1. 协调整个聊天流程
 * 2. 管理异步任务
 * 3. 处理意图识别和Agent分发
 * 4. 管理生成任务的生命周期
 *
 * @author cs
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatOrchestrationService {

    private final LLMServiceImpl llmService;
    private final SseConnectionService sseConnectionService;
    private final IChatMessageService chatMessageService;
    private final IStreamCacheService streamCacheService;
    private final IStopGenerationService stopGenerationService;
    private final TaskManagementService taskManagementService;

    @Qualifier("llmTaskExecutor")
    private final Executor llmTaskExecutor;

    /**
     * 处理用户消息
     */
    public Long processMessage(String sessionId, String message, Long chatId, Long userId, Long agentId) {
        log.info("开始处理消息: sessionId={}, chatId={}, userId={}, agentId={}", 
                sessionId, chatId, userId, agentId);

        try {
            // 1. 获取历史消息上下文
            List<ChatMessage> contextMessages = getContextMessages(chatId, userId);
            
            // 2. 保存用户消息
            ChatMessage userMessage = chatMessageService.saveUserMessage(chatId, userId, message, 1);
            
            // 3. 创建流式缓存
            String messageId = streamCacheService.createStreamCache(sessionId);
            
            // 4. 清除停止标志
            stopGenerationService.clearStopFlag(sessionId, chatId, userId);
            
            // 5. 创建并启动生成任务
            CompletableFuture<Void> generationTask = createGenerationTask(
                    contextMessages, userMessage, sessionId, messageId, chatId, userId, agentId);
            
            // 6. 注册任务管理
            taskManagementService.registerTask(sessionId, chatId, userId, generationTask);
            
            return userMessage.getId();
            
        } catch (Exception e) {
            log.error("处理消息失败: sessionId={}, chatId={}, userId={}", sessionId, chatId, userId, e);
            throw new RuntimeException("处理消息失败: " + e.getMessage(), e);
        }
    }

    /**
     * 停止生成
     */
    public void stopGeneration(String sessionId, Long chatId, Long userId) {
        log.info("停止生成: sessionId={}, chatId={}, userId={}", sessionId, chatId, userId);
        
        try {
            // 设置停止标志
            stopGenerationService.setStopFlag(sessionId, chatId, userId);
            
            // 取消正在进行的任务
            taskManagementService.cancelTask(sessionId, chatId, userId);
            
            log.info("停止生成完成: sessionId={}, chatId={}, userId={}", sessionId, chatId, userId);
            
        } catch (Exception e) {
            log.error("停止生成失败: sessionId={}, chatId={}, userId={}", sessionId, chatId, userId, e);
            throw new RuntimeException("停止生成失败: " + e.getMessage(), e);
        }
    }

    /**
     * 获取上下文消息
     */
    private List<ChatMessage> getContextMessages(Long chatId, Long userId) {
        List<ChatMessage> messages = chatMessageService.getContextMessagesForChat(chatId, userId, 10);
        Collections.reverse(messages);
        return messages;
    }

    /**
     * 创建生成任务
     */
    private CompletableFuture<Void> createGenerationTask(List<ChatMessage> contextMessages, 
                                                        ChatMessage userMessage,
                                                        String sessionId, 
                                                        String messageId, 
                                                        Long chatId, 
                                                        Long userId, 
                                                        Long agentId) {
        
        return CompletableFuture.runAsync(() -> {
            try {
                // 直接调用LLM服务
                llmService.sendMessageToLLMAsyncWithSSE(
                        contextMessages, agentId, userMessage, sessionId, messageId,
                        sseConnectionService.getSseEmitters(), chatId, userId, chatMessageService);
            } catch (Exception e) {
                log.error("处理失败: agentId={}, sessionId={}, chatId={}, userId={}", 
                        agentId, sessionId, chatId, userId, e);
                streamCacheService.addStreamChunk(sessionId, messageId, "error", e.getMessage());
                sseConnectionService.sendToSession(sessionId, "error", e.getMessage());
            }
        }, llmTaskExecutor);
    }
}
