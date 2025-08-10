-- ----------------------------
-- 21、聊天消息表
-- ----------------------------
DROP TABLE IF EXISTS chat_message;
CREATE TABLE `chat_message` (
    `chatMessage_id`     BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    `chat_id`           BIGINT UNSIGNED NOT NULL COMMENT '会话 ID',
    `user_id`           BIGINT UNSIGNED NOT NULL COMMENT '用户 ID',
    `sort_id`           BIGINT UNSIGNED NOT NULL COMMENT '同一会话内的消息序号',
    `sender_role`       TINYINT UNSIGNED NOT NULL COMMENT '1=用户 2=Agent 3=系统',
    `reasoning_content` TEXT             NULL COMMENT '思维链内容（纯文本存储）',
    `message_content`   TEXT             NOT NULL COMMENT '消息/回复正文',
    `extra_content`     TEXT             NULL COMMENT '额外引用信息（纯文本存储）',
    `message_type`      TINYINT UNSIGNED NOT NULL COMMENT '1=文本 2=图片 3=语音',
    `del_flag`          TINYINT(1) UNSIGNED NOT NULL DEFAULT 0 COMMENT '逻辑删除标记',
    `create_time`       DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`       DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
                                   ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`chatMessage_id`),
    UNIQUE KEY `uk_chat_sort` (`chat_id`, `sort_id`),   -- 保证一个会话里序号唯一
    KEY `idx_chat` (`chat_id`, `sort_id`),              -- 拉取历史消息常用
    KEY `idx_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='聊天消息表';

-- ----------------------------
-- 22、Agent智能体表
-- ----------------------------
DROP TABLE IF EXISTS agent;
CREATE TABLE `agent` (
    `agent_id`          BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `agent_name`        VARCHAR(100) DEFAULT NULL COMMENT 'Agent名称',
    `agent_description` VARCHAR(255) DEFAULT NULL COMMENT 'Agent描述',
    `chat_enabled`      TINYINT(1) DEFAULT 1 COMMENT '是否开启聊天功能',
    `system_prompt`     TEXT COMMENT '默认系统提示词',
    `llm_supplier`      VARCHAR(100) DEFAULT NULL COMMENT '大模型供应商',
    `model`             VARCHAR(100) DEFAULT NULL COMMENT '大模型名称',
    `stream`            TINYINT(1) DEFAULT 1 COMMENT '是否开启流式输出，1-开启，0-关闭',
    `temperature`       FLOAT DEFAULT 0.7 COMMENT '温度系数',
    `top_p`             FLOAT DEFAULT 1.0 COMMENT '核采样的概率阈值',
    `top_k`             INT DEFAULT NULL COMMENT '采样候选集',
    `presence_penalty`  FLOAT DEFAULT 0.0 COMMENT '存在性惩罚',
    `frequency_penalty` FLOAT DEFAULT 0.0 COMMENT '频率惩罚度',
    `max_tokens`        INT DEFAULT 2048 COMMENT '最大token数',
    `enable_thinking`   TINYINT(1) DEFAULT 0 COMMENT '是否开启思考模式，1-开启，0-关闭',
    `thinking_budget`   INT DEFAULT NULL COMMENT '思考过程的最大Token长度',
    `is_pinned`         TINYINT(1) DEFAULT 0 COMMENT '是否被收藏，1-收藏，0-未收藏',
    `del_flag`          INT DEFAULT 0 COMMENT '是否删除，0-未删除，1-已删除',
    `create_time`       DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`       DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`agent_id`),
    KEY `idx_llm_supplier` (`llm_supplier`),
    KEY `idx_model` (`model`),
    KEY `idx_is_pinned` (`is_pinned`),
    KEY `idx_del_flag` (`del_flag`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Agent配置表';

-- ----------------------------
-- 23、chat会话表
-- ----------------------------
DROP TABLE IF EXISTS `chat`;
CREATE TABLE `chat` (
    `chat_id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '会话ID',
    `user_id` BIGINT UNSIGNED NOT NULL COMMENT '用户ID',
    `agent_id` BIGINT UNSIGNED NULL COMMENT '该会话上次聊天选择的Agent的ID',
    `chat_title` VARCHAR(100) NULL COMMENT '会话标题',
    `last_message_id` BIGINT UNSIGNED NULL COMMENT '最后一条消息ID',
    `last_message_time` DATETIME NULL COMMENT '最后消息时间',
    `unread_count` INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '未读消息数',
    `is_pinned` TINYINT(1) UNSIGNED NOT NULL DEFAULT 0 COMMENT '是否置顶 0=否 1=是',
    `is_starred` TINYINT(1) UNSIGNED NOT NULL DEFAULT 0 COMMENT '是否星标 0=否 1=是',
    `del_flag` TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '是否删除，0-未删除，1-已删除',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`chat_id`),
    KEY `idx_user` (`user_id`, `del_flag`) COMMENT '用户会话列表查询'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='聊天会话表';


