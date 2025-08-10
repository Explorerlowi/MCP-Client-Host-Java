package com.mcp.host.service.chat;

import com.mcp.host.service.IStopGenerationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 任务管理服务
 * 
 * 职责：
 * 1. 管理异步生成任务的生命周期
 * 2. 提供任务注册、取消、清理功能
 * 3. 维护任务状态和映射关系
 * 4. 处理任务异常和超时
 *
 * @author cs
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TaskManagementService {

    private final IStopGenerationService stopGenerationService;

    // 存储正在进行的异步任务，用于停止生成功能
    private final ConcurrentHashMap<String, CompletableFuture<Void>> activeGenerationTasks = new ConcurrentHashMap<>();

    /**
     * 注册生成任务
     */
    public void registerTask(String sessionId, Long chatId, Long userId, CompletableFuture<Void> task) {
        String taskKey = generateTaskKey(sessionId, chatId, userId);
        
        log.info("注册生成任务: taskKey={}", taskKey);
        
        // 存储任务
        activeGenerationTasks.put(taskKey, task);
        
        // 设置任务完成后的清理逻辑
        task.whenComplete((result, throwable) -> {
            cleanupTask(taskKey, sessionId, chatId, userId, throwable);
        });
    }

    /**
     * 取消任务
     */
    public boolean cancelTask(String sessionId, Long chatId, Long userId) {
        String taskKey = generateTaskKey(sessionId, chatId, userId);
        
        log.info("尝试取消任务: taskKey={}", taskKey);
        
        CompletableFuture<Void> activeTask = activeGenerationTasks.get(taskKey);
        if (activeTask != null && !activeTask.isDone()) {
            boolean cancelled = activeTask.cancel(true);
            log.info("任务取消结果: {}, taskKey={}", cancelled, taskKey);
            
            // 移除任务引用
            activeGenerationTasks.remove(taskKey);
            
            return cancelled;
        }
        
        log.info("未找到活跃任务或任务已完成: taskKey={}", taskKey);
        return false;
    }

    /**
     * 检查任务是否存在且活跃
     */
    public boolean isTaskActive(String sessionId, Long chatId, Long userId) {
        String taskKey = generateTaskKey(sessionId, chatId, userId);
        CompletableFuture<Void> task = activeGenerationTasks.get(taskKey);
        return task != null && !task.isDone();
    }

    /**
     * 获取活跃任务数量
     */
    public int getActiveTaskCount() {
        return (int) activeGenerationTasks.values().stream()
                .filter(task -> !task.isDone())
                .count();
    }

    /**
     * 获取总任务数量
     */
    public int getTotalTaskCount() {
        return activeGenerationTasks.size();
    }

    /**
     * 清理所有已完成的任务
     */
    public int cleanupCompletedTasks() {
        final int[] cleanedCount = {0}; // 使用数组来避免lambda中的变量作用域问题

        activeGenerationTasks.entrySet().removeIf(entry -> {
            if (entry.getValue().isDone()) {
                cleanedCount[0]++;
                return true;
            }
            return false;
        });

        if (cleanedCount[0] > 0) {
            log.info("清理了 {} 个已完成的任务", cleanedCount[0]);
        }

        return cleanedCount[0];
    }

    /**
     * 强制清理指定会话的所有任务
     */
    public void forceCleanupSessionTasks(String sessionId) {
        final int[] cleanedCount = {0}; // 使用数组来避免lambda中的变量作用域问题

        activeGenerationTasks.entrySet().removeIf(entry -> {
            String taskKey = entry.getKey();
            if (taskKey.startsWith(sessionId + "_")) {
                CompletableFuture<Void> task = entry.getValue();
                if (!task.isDone()) {
                    task.cancel(true);
                }
                cleanedCount[0]++;
                return true;
            }
            return false;
        });

        if (cleanedCount[0] > 0) {
            log.info("强制清理了会话 {} 的 {} 个任务", sessionId, cleanedCount[0]);
        }
    }

    /**
     * 获取任务统计信息
     */
    public TaskStats getTaskStats() {
        int totalTasks = activeGenerationTasks.size();
        int activeTasks = getActiveTaskCount();
        int completedTasks = totalTasks - activeTasks;
        
        return TaskStats.builder()
                .totalTasks(totalTasks)
                .activeTasks(activeTasks)
                .completedTasks(completedTasks)
                .build();
    }

    /**
     * 生成任务键
     */
    private String generateTaskKey(String sessionId, Long chatId, Long userId) {
        return String.format("%s_%d_%d", sessionId, chatId, userId);
    }

    /**
     * 清理任务
     */
    private void cleanupTask(String taskKey, String sessionId, Long chatId, Long userId, Throwable throwable) {
        try {
            // 移除任务
            activeGenerationTasks.remove(taskKey);

            // 只有在任务正常完成时才清除停止标志
            if (throwable == null) {
                stopGenerationService.clearStopFlag(sessionId, chatId, userId);
                log.info("任务正常完成，已清理: taskKey={}", taskKey);
            } else if (!(throwable instanceof java.util.concurrent.CancellationException)) {
                log.error("任务异常完成: taskKey={}", taskKey, throwable);
            } else {
                log.info("任务被取消: taskKey={}", taskKey);
            }

        } catch (Exception e) {
            log.error("清理任务失败: taskKey={}", taskKey, e);
        }
    }

    /**
     * 任务统计信息
     */
    public static class TaskStats {
        private final int totalTasks;
        private final int activeTasks;
        private final int completedTasks;

        private TaskStats(int totalTasks, int activeTasks, int completedTasks) {
            this.totalTasks = totalTasks;
            this.activeTasks = activeTasks;
            this.completedTasks = completedTasks;
        }

        public static Builder builder() {
            return new Builder();
        }

        public static class Builder {
            private int totalTasks;
            private int activeTasks;
            private int completedTasks;

            public Builder totalTasks(int totalTasks) {
                this.totalTasks = totalTasks;
                return this;
            }

            public Builder activeTasks(int activeTasks) {
                this.activeTasks = activeTasks;
                return this;
            }

            public Builder completedTasks(int completedTasks) {
                this.completedTasks = completedTasks;
                return this;
            }

            public TaskStats build() {
                return new TaskStats(totalTasks, activeTasks, completedTasks);
            }
        }

        // Getters
        public int getTotalTasks() { return totalTasks; }
        public int getActiveTasks() { return activeTasks; }
        public int getCompletedTasks() { return completedTasks; }
    }
}
