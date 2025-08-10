// 聊天相关的类型定义

export interface ChatMessage {
  id: string;
  role: 'user' | 'assistant' | 'system';
  content: string;
  timestamp: Date;
  metadata?: Record<string, any>;
  // 扩展：用于展示不同类型内容
  reasoningContent?: string; // 思考内容（来自 thinking 事件）
  extraContent?: string;     // 额外信息（complete.extraContent）
  isStreaming?: boolean;     // 是否处于流式中
}

export interface ChatRequest {
  message: string;
  conversationId?: string;
}

export interface ConversationResponse {
  content: string;
  conversationId: string;
  success: boolean;
  error?: string;
}

export interface MCPToolInfo {
  name: string;
  description: string;
  serverName: string;
  inputSchema: Record<string, any>;
}

export interface ConversationHistory {
  conversationId: string;
  messages: ChatMessage[];
  createdAt: Date;
  updatedAt: Date;
}

export interface MCPToolResult {
  toolName: string;
  serverName: string;
  success: boolean;
  result?: string;
  error?: string;
  executionTime?: number;
}