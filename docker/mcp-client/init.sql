-- MCP 数据库初始化脚本
-- 包含 MCP Host 和 MCP Client 的所有表结构
USE mcp;

-- ----------------------------
-- MCP Host 表结构
-- ----------------------------

-- ----------------------------
-- 聊天消息表
-- ----------------------------
CREATE TABLE IF NOT EXISTS `chat_message` (
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
-- Agent智能体表
-- ----------------------------
CREATE TABLE IF NOT EXISTS `agent` (
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
-- chat会话表
-- ----------------------------
CREATE TABLE IF NOT EXISTS `chat` (
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

-- ----------------------------
-- MCP Client 表结构
-- ----------------------------

-- ----------------------------
-- 1、MCP 服务器配置表
-- ----------------------------
CREATE TABLE IF NOT EXISTS `mcp_servers` (
    `id` VARCHAR(255) NOT NULL COMMENT '服务器ID',
    `name` VARCHAR(255) NULL COMMENT '服务器名称',
    `description` TEXT NULL COMMENT '服务器描述',
    `type` VARCHAR(50) NOT NULL COMMENT '传输类型：STDIO/SSE/STREAMABLEHTTP',
    `url` VARCHAR(500) NULL COMMENT 'HTTP/SSE 服务器URL',
    `command` VARCHAR(500) NULL COMMENT 'STDIO 命令',
    `timeout` BIGINT DEFAULT 60 COMMENT '超时时间（秒）',
    `disabled` BOOLEAN DEFAULT FALSE COMMENT '是否禁用',
    `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='MCP服务器配置表';

-- ----------------------------
-- 2、MCP 服务器参数表
-- ----------------------------
CREATE TABLE IF NOT EXISTS `mcp_server_args` (
    `server_id` VARCHAR(255) NOT NULL COMMENT '服务器ID（逻辑关联 mcp_servers.id）',
    `arg` VARCHAR(500) NOT NULL COMMENT '命令参数',
    INDEX `idx_server_id` (`server_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='MCP服务器参数表';

-- ----------------------------
-- 3、MCP 服务器环境变量表
-- ----------------------------
CREATE TABLE IF NOT EXISTS `mcp_server_env` (
    `server_id` VARCHAR(255) NOT NULL COMMENT '服务器ID（逻辑关联 mcp_servers.id）',
    `env_key` VARCHAR(255) NOT NULL COMMENT '环境变量名',
    `env_value` VARCHAR(1000) NULL COMMENT '环境变量值',
    PRIMARY KEY (`server_id`, `env_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='MCP服务器环境变量表';

-- ----------------------------
-- 创建索引（如果不存在）
-- ----------------------------
CREATE INDEX IF NOT EXISTS `idx_mcp_servers_disabled` ON `mcp_servers`(`disabled`);
CREATE INDEX IF NOT EXISTS `idx_mcp_servers_type` ON `mcp_servers`(`type`);
CREATE INDEX IF NOT EXISTS `idx_mcp_server_args_server_id` ON `mcp_server_args`(`server_id`);
CREATE INDEX IF NOT EXISTS `idx_mcp_server_env_server_id` ON `mcp_server_env`(`server_id`);