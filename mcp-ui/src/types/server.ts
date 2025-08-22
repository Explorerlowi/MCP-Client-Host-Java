// MCP 服务器管理相关的类型定义

export enum TransportType {
  STDIO = 'STDIO',
  SSE = 'SSE',
  STREAMABLEHTTP = 'STREAMABLEHTTP'
}

export interface McpServerSpec {
  id: string;
  name?: string;
  description?: string;
  type: TransportType;
  url?: string;
  command?: string;
  args?: string[];
  env?: Record<string, string>;
  disabled: boolean;
  timeout?: number;
  createdAt?: Date;
  updatedAt?: Date;
}

export interface MCPServerHealth {
  serverId: string;
  connected: boolean;
  status: string;
  lastCheck: Date;
  errorMessage?: string;
  responseTime?: number;
  serverInfo?: string;
}

export interface ServerConnectionStats {
  totalServers: number;
  connectedServers: number;
  disconnectedServers: number;
  averageResponseTime?: number;
  lastUpdateTime?: Date;
  healthCheckTime?: number;
}

export interface ServerFormData {
  id: string;
  name: string;
  description: string;
  type: TransportType;
  url: string;
  command: string;
  args: string;
  env: string;
  disabled: boolean;
}

export interface ServerOperationResponse {
  message: string;
  serverId: string;
  status: 'success' | 'error';
}

export interface MCPTool {
  name: string;
  description: string;
  serverName: string;
  inputSchema?: string;
  outputSchema?: string;
}

export interface MCPResource {
  uri: string;
  name?: string;
  title?: string;
  description?: string;
  mimeType?: string;
  size?: number;
  text?: string;
  blob?: string;
  serverName: string;
  available?: boolean;
  annotations?: {
    audience?: string[];
    priority?: number;
    lastModified?: string;
  };
}

export interface MCPPrompt {
  name: string;
  title?: string;
  description?: string;
  serverName: string;
  disabled?: boolean;
  usageCount?: number;
  category?: string;
  arguments?: {
    name: string;
    description?: string;
    required?: boolean;
  }[];
  messages?: {
    role: string;
    content: {
      type: string;
      text?: string;
      image?: string;
      audio?: string;
      resource?: MCPResource;
      annotations?: {
        audience?: string[];
        priority?: number;
      };
    };
  }[];
}
