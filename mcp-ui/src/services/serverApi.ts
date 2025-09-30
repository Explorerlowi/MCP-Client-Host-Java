// MCP 服务器管理 API 服务

import axios from 'axios';
import type { McpServerSpec, MCPServerHealth, ServerConnectionStats, ServerOperationResponse, MCPTool, MCPResource, MCPPrompt } from '../types/server';
// 移除未使用的模拟数据导入，统一使用真实 API

const API_BASE_URL = '/api/mcp/servers';

export class ServerApiService {
  
  /**
   * 获取所有服务器配置
   */
  static async getAllServers(): Promise<McpServerSpec[]> {
    try {
      const response = await axios.get<McpServerSpec[]>(API_BASE_URL);
      return response.data;
    } catch (error) {
      console.error('获取服务器列表失败:', error);
      throw new Error('获取服务器列表失败');
    }
  }

  /**
   * 添加或更新服务器配置
   */
  static async addOrUpdateServer(spec: McpServerSpec): Promise<ServerOperationResponse> {
    try {
      const response = await axios.post<ServerOperationResponse>(API_BASE_URL, spec);
      return response.data;
    } catch (error) {
      console.error('保存服务器配置失败:', error);
      throw new Error('保存服务器配置失败');
    }
  }

  /**
   * 删除服务器配置
   */
  static async deleteServer(serverId: string): Promise<void> {
    try {
      await axios.delete(`${API_BASE_URL}/${serverId}`);
    } catch (error) {
      console.error('删除服务器配置失败:', error);
      throw new Error('删除服务器配置失败');
    }
  }

  /**
   * 获取单个服务器健康状态
   */
  static async getServerHealth(serverId: string): Promise<MCPServerHealth> {
    try {
      const response = await axios.get(`${API_BASE_URL}/${serverId}/health`);
      const item = response.data;
      // 处理后端返回的字段映射
      return {
        serverId: item.server_id || item.serverId,
        connected: item.connected,
        status: item.status,
        lastCheck: item.last_check ? new Date(item.last_check * 1000) :
                  item.lastCheckTime ? new Date(item.lastCheckTime) :
                  new Date(),
        errorMessage: item.error_message || item.errorMessage,
        responseTime: item.response_time_ms || item.responseTime,
        serverInfo: item.server_info || item.serverInfo
      };
    } catch (error) {
      console.error('获取服务器健康状态失败:', error);
      throw new Error('获取服务器健康状态失败');
    }
  }

  /**
   * 获取所有服务器健康状态
   */
  static async getAllServerHealth(): Promise<MCPServerHealth[]> {
    try {
      const response = await axios.get(`${API_BASE_URL}/health`);
      // 处理后端返回的字段映射
      const healthData = response.data.map((item: any) => ({
        serverId: item.server_id || item.serverId,
        connected: item.connected,
        status: item.status,
        lastCheck: item.last_check ? new Date(item.last_check * 1000) :
                  item.lastCheckTime ? new Date(item.lastCheckTime) :
                  new Date(),
        errorMessage: item.error_message || item.errorMessage,
        responseTime: item.response_time_ms || item.responseTime,
        serverInfo: item.server_info || item.serverInfo
      }));
      return healthData;
    } catch (error) {
      console.error('获取所有服务器健康状态失败:', error);
      throw new Error('获取所有服务器健康状态失败');
    }
  }

  /**
   * 重新连接服务器
   */
  static async reconnectServer(serverId: string): Promise<string> {
    try {
      const response = await axios.post<string>(`${API_BASE_URL}/${serverId}/reconnect`);
      return response.data;
    } catch (error) {
      console.error('重新连接服务器失败:', error);
      throw new Error('重新连接服务器失败');
    }
  }



  /**
   * 获取连接统计信息
   */
  static async getConnectionStats(): Promise<ServerConnectionStats> {
    try {
      const response = await axios.get<ServerConnectionStats>(`${API_BASE_URL}/stats`);
      return response.data;
    } catch (error) {
      console.error('获取连接统计信息失败:', error);
      throw new Error('获取连接统计信息失败');
    }
  }



  /**
   * 从JSON配置导入服务器
   */
  static async importFromJson(jsonConfig: string): Promise<{ status: string; message: string; loadedCount: number }> {
    try {
      const response = await axios.post<{ status: string; message: string; loadedCount: number }>('/api/mcp/config/import', jsonConfig, {
        headers: {
          'Content-Type': 'application/json'
        }
      });
      return response.data;
    } catch (error) {
      console.error('JSON配置导入失败:', error);
      throw new Error('JSON配置导入失败');
    }
  }

  /**
   * 导出所有服务器配置为JSON
   */
  static async exportToJson(): Promise<string> {
    try {
      const response = await axios.get<string>('/api/mcp/config/export', {
        headers: {
          'Accept': 'application/json'
        }
      });
      return response.data;
    } catch (error) {
      console.error('配置导出失败:', error);
      throw new Error('配置导出失败');
    }
  }



  /**
   * 关闭服务器连接
   */
  static async shutdownServer(serverId: string): Promise<string> {
    try {
      const response = await axios.post<string>(`${API_BASE_URL}/${serverId}/shutdown`);
      return response.data;
    } catch (error) {
      console.error('关闭服务器连接失败:', error);
      throw new Error('关闭服务器连接失败');
    }
  }

  /**
   * 获取服务器的工具列表
   */
  static async getServerTools(serverId: string): Promise<MCPTool[]> {
    try {
      const response = await axios.get<MCPTool[]>(`${API_BASE_URL}/${serverId}/tools`);
      return response.data;
    } catch (error) {
      console.error('获取服务器工具列表失败:', error);
      throw new Error('获取服务器工具列表失败');
    }
  }



  /**
   * 获取服务器的资源列表
   */
  static async getServerResources(serverId: string): Promise<MCPResource[]> {
    try {
      const response = await axios.get<MCPResource[]>(`${API_BASE_URL}/${serverId}/resources`);
      return response.data;
    } catch (error) {
      console.error('获取服务器资源列表失败:', error);
      throw new Error('获取服务器资源列表失败');
    }
  }



  /**
   * 获取服务器的提示模板列表
   */
  static async getServerPrompts(serverId: string): Promise<MCPPrompt[]> {
    try {
      const response = await axios.get<MCPPrompt[]>(`${API_BASE_URL}/${serverId}/prompts`);
      return response.data;
    } catch (error) {
      console.error('获取服务器提示模板列表失败:', error);
      throw new Error('获取服务器提示模板列表失败');
    }
  }
}
