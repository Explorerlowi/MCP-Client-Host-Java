package com.mcp.host.service;

import lombok.Data;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 流式缓存服务接口
 *
 * @author cs
 */
public interface IStreamCacheService {

    /**
     * 创建新的流式缓存
     *
     * @param sessionId 会话ID
     * @return 消息ID
     */
    String createStreamCache(String sessionId);

    /**
     * 添加流式内容块到缓存
     *
     * @param sessionId 会话ID
     * @param messageId 消息ID
     * @param type 事件类型
     * @param data 数据
     */
    void addStreamChunk(String sessionId, String messageId, String type, Object data);

    /**
     * 标记流式响应完成
     *
     * @param sessionId 会话ID
     * @param messageId 消息ID
     * @param fullContent 完整内容
     * @param reasoningContent 思维链内容
     */
    void markStreamCompleted(String sessionId, String messageId, String fullContent, String reasoningContent);

    /**
     * 存储本次请求的临时回复
     *
     * @param sessionId 会话ID
     * @param messageId 消息ID
     * @param tempContent 本次请求回复的完整内容
     */
    void markToolCall(String sessionId, String messageId, String tempContent);

    /**
     * 标记流式响应出错
     *
     * @param sessionId 会话ID
     * @param messageId 消息ID
     * @param errorMessage 错误信息
     */
    void markStreamError(String sessionId, String messageId, String errorMessage);

    /**
     * 获取缓存数据
     *
     * @param sessionId 会话ID
     * @param messageId 消息ID
     * @return 缓存数据
     */
    StreamCacheData getStreamCache(String sessionId, String messageId);

    /**
     * 检查流式响应是否完成
     *
     * @param sessionId 会话ID
     * @param messageId 消息ID
     * @return 是否完成
     */
    boolean isStreamCompleted(String sessionId, String messageId);

    /**
     * 获取指定会话的最新缓存
     *
     * @param sessionId 会话ID
     * @return 缓存数据
     */
    StreamCacheData getLatestStreamCache(String sessionId);

    /**
     * 删除缓存
     *
     * @param sessionId 会话ID
     * @param messageId 消息ID
     */
    void removeStreamCache(String sessionId, String messageId);

    /**
     * 清理指定会话的所有缓存
     *
     * @param sessionId 会话ID
     */
    void clearSessionCache(String sessionId);

    /**
     * 标记缓存已重放到指定位置
     *
     * @param sessionId 会话ID
     * @param messageId 消息ID
     * @param replayedCount 已重放数量
     */
    void markCacheReplayed(String sessionId, String messageId, int replayedCount);

    /**
     * 标记开始重放缓存
     *
     * @param sessionId 会话ID
     * @param messageId 消息ID
     */
    void markReplayStarted(String sessionId, String messageId);

    /**
     * 标记重放缓存完成
     *
     * @param sessionId 会话ID
     * @param messageId 消息ID
     */
    void markReplayCompleted(String sessionId, String messageId);

    /**
     * 检查缓存是否正在重放
     *
     * @param sessionId 会话ID
     * @param messageId 消息ID
     * @return 是否正在重放
     */
    boolean isReplaying(String sessionId, String messageId);

    /**
     * 获取客户端未接收的缓存块
     *
     * @param sessionId 会话ID
     * @param messageId 消息ID
     * @return 未接收的块列表
     */
    List<StreamChunk> getUnreceivedChunks(String sessionId, String messageId);

    /**
     * 获取未重放的缓存块
     *
     * @param sessionId 会话ID
     * @param messageId 消息ID
     * @return 未重放的块列表
     */
    List<StreamChunk> getUnreplayedChunks(String sessionId, String messageId);

    /**
     * 获取缓存状态统计
     *
     * @return 统计信息
     */
    Map<String, Object> getCacheStats();

    /**
     * 标记客户端成功接收到指定数量的块
     *
     * @param sessionId 会话ID
     * @param messageId 消息ID
     * @param receivedCount 接收数量
     */
    void markClientReceived(String sessionId, String messageId, int receivedCount);

    /**
     * Info信息块
     */
    @Data
    class InfoChunk {
        private String content;
        private Integer index;
        private Integer reasoningPosition;
        private Integer fullContentPosition;
        private LocalDateTime timestamp = LocalDateTime.now();

        public InfoChunk(String content, Integer index, Integer reasoningPosition, Integer fullContentPosition) {
            this.content = content;
            this.index = index;
            this.reasoningPosition = reasoningPosition;
            this.fullContentPosition = fullContentPosition;
        }

        /**
         * 转换为JSON对象
         */
        public JsonObject toJsonObject() {
            JsonObject jsonObject = new JsonObject();
            jsonObject.addProperty("content", content);
            jsonObject.addProperty("index", index);
            jsonObject.addProperty("reasoningPosition", reasoningPosition);
            jsonObject.addProperty("fullContentPosition", fullContentPosition);
            jsonObject.addProperty("timestamp", timestamp.toString());
            return jsonObject;
        }
    }

    /**
     * 流式缓存数据
     */
    @Data
    class StreamCacheData {
        private String sessionId;
        private String messageId;
        private List<StreamChunk> chunks = new ArrayList<>();
        private String tempContent = "";
        private String fullContent = "";
        private String reasoningContent = "";
        private JsonArray extraContent = new JsonArray(); // 存储info信息的JsonArray
        private List<InfoChunk> infoChunks = new ArrayList<>(); // 存储info信息的详细列表
        private boolean isCompleted = false;
        private boolean hasError = false;
        private String errorMessage = "";
        private int replayedChunkCount = 0;
        private int clientReceivedChunkCount = 0;
        private boolean isReplaying = false;
        private LocalDateTime createTime = LocalDateTime.now();
        private LocalDateTime updateTime = LocalDateTime.now();

        public synchronized void addChunk(StreamChunk chunk) {
            this.chunks.add(chunk);
            this.updateTime = LocalDateTime.now();
        }

        public void appendContent(String content) {
            this.fullContent += content;
            this.updateTime = LocalDateTime.now();
        }

        public void appendReasoningContent(String reasoning) {
            this.reasoningContent += reasoning;
            this.updateTime = LocalDateTime.now();
        }

        /**
         * 添加info信息块
         *
         * @param content info内容
         * @param index 顺序索引
         */
        public synchronized void addInfoChunk(String content, Integer index) {
            // 计算当前位置
            Integer reasoningPosition = reasoningContent != null ? reasoningContent.length() : 0;
            Integer fullContentPosition = fullContent != null ? fullContent.length() : 0;

            // 创建InfoChunk对象
            InfoChunk infoChunk = new InfoChunk(content, index, reasoningPosition, fullContentPosition);
            this.infoChunks.add(infoChunk);

            // 添加到JsonArray
            this.extraContent.add(infoChunk.toJsonObject());

            this.updateTime = LocalDateTime.now();
        }

        /**
         * 获取extraContent的字符串表示（用于数据库存储）
         */
        public String getExtraContentString() {
            return extraContent.toString();
        }
    }

    /**
     * 流式块
     */
    @Data
    class StreamChunk {
        private String type;
        private String content;
        private Map<String, Object> data;
        private LocalDateTime timestamp = LocalDateTime.now();

        public StreamChunk(String type, String content) {
            this.type = type;
            this.content = content;
        }

        public StreamChunk(String type, Map<String, Object> data) {
            this.type = type;
            this.data = data;
            if (data != null && data.containsKey("content")) {
                this.content = data.get("content").toString();
            }
        }
    }
} 