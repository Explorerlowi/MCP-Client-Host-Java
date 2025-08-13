package com.mcp.host.controller;

import com.mcp.host.model.AjaxResult;
import com.mcp.host.domain.ChatMessage;
import com.mcp.host.domain.dto.ChatHistoryRequestDto;
import com.mcp.host.service.IChatMessageService;
import com.mcp.host.service.chat.ChatOrchestrationService;
import com.mcp.host.service.chat.SseConnectionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.Collections;
import java.util.List;

/**
 * 聊天控制器 - 重构版本
 * 
 * 职责：
 * 1. 处理HTTP请求和响应
 * 2. 参数验证和转换
 * 3. 调用业务服务
 * 4. 统一异常处理
 *
 * @author cs
 */
@Slf4j
@RestController
@RequestMapping("/api/chat2Agent")
@RequiredArgsConstructor
@Validated
public class LLMControllerRefactored {

    private final ChatOrchestrationService chatOrchestrationService;
    private final SseConnectionService sseConnectionService;
    private final IChatMessageService chatMessageService;

    /**
     * 建立SSE连接
     */
    @GetMapping(value = "/stream/{sessionId}")
    public SseEmitter createSseConnection(
            @PathVariable @NotBlank String sessionId,
            HttpServletResponse response) {
        
        log.info("建立SSE连接请求: sessionId={}", sessionId);
        
        try {
            return sseConnectionService.createConnection(sessionId, response);
        } catch (Exception e) {
            log.error("建立SSE连接失败: sessionId={}", sessionId, e);
            throw new RuntimeException("建立SSE连接失败: " + e.getMessage(), e);
        }
    }

    /**
     * 发送消息并接收流式响应
     */
    @PostMapping("/send")
    public AjaxResult sendMessage(
            @RequestParam @NotBlank String sessionId,
            @RequestParam @NotBlank String message,
            @RequestParam(defaultValue = "1") @NotNull @Min(1) Long chatId,
            @RequestParam(defaultValue = "1") @NotNull @Min(1) Long userId,
            @RequestParam(defaultValue = "1") @NotNull @Min(1) Long agentId,
            @RequestParam(name = "servers", required = false) String serversCsv) {
        
        log.info("发送消息请求: sessionId={}, chatId={}, userId={}, agentId={}, messageLength={}", 
                sessionId, chatId, userId, agentId, message.length());
        
        try {
            Long messageId = chatOrchestrationService.processMessage(
                    sessionId, message, chatId, userId, agentId, serversCsv);
            
            return AjaxResult.success("消息发送成功，请通过SSE接收响应", messageId);
        } catch (Exception e) {
            log.error("发送消息失败: sessionId={}, chatId={}, userId={}", sessionId, chatId, userId, e);
            return AjaxResult.error("发送消息失败: " + e.getMessage());
        }
    }

    /**
     * 停止生成消息
     */
    @PostMapping("/stop")
    public AjaxResult stopGeneration(
            @RequestParam @NotBlank String sessionId,
            @RequestParam(defaultValue = "1") @NotNull @Min(1) Long chatId,
            @RequestParam(defaultValue = "1") @NotNull @Min(1) Long userId) {
        
        log.info("停止生成请求: sessionId={}, chatId={}, userId={}", sessionId, chatId, userId);
        
        try {
            chatOrchestrationService.stopGeneration(sessionId, chatId, userId);
            return AjaxResult.success("生成已停止");
        } catch (Exception e) {
            log.error("停止生成失败: sessionId={}, chatId={}, userId={}", sessionId, chatId, userId, e);
            return AjaxResult.error("停止生成失败: " + e.getMessage());
        }
    }

    /**
     * 删除单条消息
     */
    @DeleteMapping("/message/{messageId}")
    public AjaxResult deleteMessage(
            @PathVariable @NotNull @Min(1) Long messageId,
            @RequestParam @NotNull @Min(1) Long userId) {
        
        log.info("删除消息请求: messageId={}, userId={}", messageId, userId);
        
        try {
            boolean result = chatMessageService.deleteMessage(messageId, userId);
            if (result) {
                log.info("删除消息成功: messageId={}, userId={}", messageId, userId);
                return AjaxResult.success("消息删除成功");
            } else {
                log.warn("删除消息失败或无权限: messageId={}, userId={}", messageId, userId);
                return AjaxResult.error("删除失败，消息不存在或无删除权限");
            }
        } catch (Exception e) {
            log.error("删除消息异常: messageId={}, userId={}", messageId, userId, e);
            return AjaxResult.error("删除消息失败: " + e.getMessage());
        }
    }

    /**
     * 批量删除消息（回溯功能）
     */
    @DeleteMapping("/messages/from/{messageId}")
    public AjaxResult deleteMessagesFromIndex(
            @PathVariable @NotNull @Min(1) Long messageId,
            @RequestParam @NotNull @Min(1) Long chatId,
            @RequestParam @NotNull @Min(1) Long userId) {
        
        log.info("批量删除消息请求: chatId={}, fromMessageId={}, userId={}", chatId, messageId, userId);
        
        try {
            boolean result = chatMessageService.deleteMessagesFromIndex(chatId, userId, messageId);
            if (result) {
                log.info("批量删除消息成功: chatId={}, fromMessageId={}, userId={}", chatId, messageId, userId);
                return AjaxResult.success("消息回溯成功");
            } else {
                log.warn("批量删除消息失败或无权限: chatId={}, fromMessageId={}, userId={}", chatId, messageId, userId);
                return AjaxResult.error("回溯失败，消息不存在或无操作权限");
            }
        } catch (Exception e) {
            log.error("批量删除消息异常: chatId={}, fromMessageId={}, userId={}", chatId, messageId, userId, e);
            return AjaxResult.error("回溯操作失败: " + e.getMessage());
        }
    }

    /**
     * 清空会话所有消息
     */
    @DeleteMapping("/chat/{chatId}/messages")
    public AjaxResult clearChatMessages(
            @PathVariable @NotNull @Min(1) Long chatId,
            @RequestParam @NotNull @Min(1) Long userId) {
        
        log.info("清空会话消息请求: chatId={}, userId={}", chatId, userId);
        
        try {
            boolean result = chatMessageService.clearChatMessages(chatId, userId);
            if (result) {
                log.info("清空会话消息成功: chatId={}, userId={}", chatId, userId);
                return AjaxResult.success("会话消息已清空");
            } else {
                log.warn("清空会话消息失败或无权限: chatId={}, userId={}", chatId, userId);
                return AjaxResult.error("清空失败，会话不存在或无操作权限");
            }
        } catch (Exception e) {
            log.error("清空会话消息异常: chatId={}, userId={}", chatId, userId, e);
            return AjaxResult.error("清空操作失败: " + e.getMessage());
        }
    }

    /**
     * 获取聊天历史消息（分页）
     */
    @GetMapping("/history/{chatId}")
    public AjaxResult getChatHistory(
            @PathVariable @NotNull @Min(1) Long chatId,
            @RequestParam @NotNull @Min(1) Long userId,
            @RequestParam(defaultValue = "1") @Min(1) Integer page,
            @RequestParam(defaultValue = "20") @Min(1) Integer size,
            @RequestParam(required = false) Long lastSortId,
            @RequestParam(defaultValue = "false") Boolean descOrder,
            @RequestParam(required = false) Integer messageType,
            @RequestParam(required = false) Integer senderRole) {
        
        log.info("获取聊天历史请求: chatId={}, userId={}, page={}, size={}", chatId, userId, page, size);
        
        try {
            ChatHistoryRequestDto request = new ChatHistoryRequestDto();
            request.setChatId(chatId);
            request.setUserId(userId);
            request.setPage(page);
            request.setSize(Math.min(size, 100)); // 限制最大页面大小
            request.setLastSortId(lastSortId);
            request.setDescOrder(descOrder);
            request.setMessageType(messageType);
            request.setSenderRole(senderRole);

            List<ChatMessage> result = chatMessageService.getChatHistory(request);
            Collections.reverse(result);
            
            log.info("获取聊天历史成功: chatId={}, userId={}, resultSize={}", chatId, userId, result.size());
            return AjaxResult.success("获取聊天历史成功", result);
        } catch (Exception e) {
            log.error("获取聊天历史失败: chatId={}, userId={}", chatId, userId, e);
            return AjaxResult.error("获取聊天历史失败: " + e.getMessage());
        }
    }
}
