-- ----------------------------
-- MCP Client 数据库初始化脚本
-- 只在表不存在时创建，保留现有数据
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