import React, { useState, useRef, useEffect } from 'react';
import { Textarea } from '../ui/textarea'
import { Button } from '../ui/button'
import { Send, Square } from 'lucide-react'
import { ServerApiService } from '../../services/serverApi'
import type { McpServerSpec, MCPServerHealth } from '../../types/server'

interface MessageInputProps {
  onSendMessage: (message: string) => void;
  onStop?: () => void; // 正在流式时点击停止
  isStreaming?: boolean; // 是否正在接收
  placeholder?: string;
}

const MessageInput: React.FC<MessageInputProps> = ({
  onSendMessage,
  onStop,
  isStreaming = false,
  placeholder = '输入消息...'
}) => {
  const [message, setMessage] = useState('');
  const textareaRef = useRef<HTMLTextAreaElement>(null);
  const DRAFT_KEY = 'mcp_chat_input_draft';
  const SELECTED_SERVERS_KEY = 'mcp_selected_servers';

  // 工具（服务器）选择相关状态
  const [showPicker, setShowPicker] = useState(false);
  const [servers, setServers] = useState<McpServerSpec[]>([]);
  const [healthList, setHealthList] = useState<MCPServerHealth[]>([]);
  const [selectedServers, setSelectedServers] = useState<string[]>([]);

  // 自动调整文本框高度
  const adjustTextareaHeight = () => {
    const textarea = textareaRef.current;
    if (textarea) {
      textarea.style.height = 'auto';
      textarea.style.height = `${Math.min(textarea.scrollHeight, 120)}px`;
    }
  };

  useEffect(() => {
    adjustTextareaHeight();
  }, [message]);

  // 初始加载草稿
  useEffect(() => {
    try {
      const saved = localStorage.getItem(DRAFT_KEY);
      if (saved) {
        setMessage(saved);
      }
      const savedServers = localStorage.getItem(SELECTED_SERVERS_KEY);
      if (savedServers) {
        setSelectedServers(savedServers.split(',').filter(Boolean));
      }
    } catch (err) {
      // 忽略本地存储异常
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  // 文本变化时保存草稿
  useEffect(() => {
    try {
      localStorage.setItem(DRAFT_KEY, message);
    } catch (err) {
      // 忽略本地存储异常
    }
  }, [message]);

  // 加载服务器与健康状态，仅显示在线的服务器供选择
  useEffect(() => {
    (async () => {
      try {
        const [list, health] = await Promise.all([
          ServerApiService.getAllServers(),
          ServerApiService.getAllServerHealth()
        ]);
        setServers(list);
        setHealthList(health);
      } catch (e) {
        // 忽略加载失败，不影响聊天
      }
    })();
  }, []);

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (isStreaming) {
      // 正在流式 → 触发停止
      onStop?.();
      return;
    }
    if (message.trim()) {
      onSendMessage(message.trim());
      setMessage('');
      try {
        localStorage.removeItem(DRAFT_KEY);
      } catch (err) {
        // 忽略本地存储异常
      }
    }
  };

  const handleKeyDown = (e: React.KeyboardEvent) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault();
      handleSubmit(e);
    }
  };

  const handleChange = (e: React.ChangeEvent<HTMLTextAreaElement>) => {
    setMessage(e.target.value);
  };

  const onlineServerIds = healthList.filter(h => h.connected).map(h => h.serverId);
  const displayServers = servers.filter(s => onlineServerIds.includes(s.id));

  const toggleServer = (id: string) => {
    setSelectedServers(prev => {
      const next = prev.includes(id) ? prev.filter(x => x !== id) : [...prev, id];
      try {
        localStorage.setItem(SELECTED_SERVERS_KEY, next.join(','));
      } catch {}
      return next;
    });
  };

  const handleSelectAll = () => {
    const all = displayServers.map(s => s.id);
    setSelectedServers(all);
    try { localStorage.setItem(SELECTED_SERVERS_KEY, all.join(',')); } catch {}
  };

  const handleClearAll = () => {
    setSelectedServers([]);
    try { localStorage.setItem(SELECTED_SERVERS_KEY, ''); } catch {}
  };

  return (
    <div className="relative">
      <form onSubmit={handleSubmit} className="space-y-3">
        <div className="flex items-end space-x-4">
          <div className="flex-1 relative">
            {/* 输入框发光边框 */}
            <div className="absolute inset-0 bg-gradient-to-r from-cyan-400/20 to-emerald-400/20 rounded-lg blur-sm"></div>
            <Textarea
              ref={textareaRef}
              value={message}
              onChange={handleChange}
              onKeyDown={handleKeyDown}
              placeholder={placeholder}
              disabled={isStreaming}
              className="relative bg-slate-900/50 border-cyan-400/30 text-cyan-100 placeholder:text-cyan-400/50 backdrop-blur-sm shadow-[0_0_20px_rgba(0,255,170,0.1)] focus:shadow-[0_0_30px_rgba(0,255,170,0.2)] focus:border-cyan-400/50 transition-all duration-300"
              rows={1}
            />
            {/* 输入框内部装饰 */}
            <div className="absolute top-2 right-2 flex space-x-1">
              <div className="w-2 h-2 bg-cyan-400 rounded-full opacity-60 animate-pulse"></div>
              <div className="w-2 h-2 bg-emerald-400 rounded-full opacity-40 animate-pulse" style={{animationDelay: '0.5s'}}></div>
            </div>
          </div>
          
          <Button 
            type="submit" 
            disabled={!isStreaming && !message.trim()} 
            className={
              isStreaming 
                ? "bg-red-500/20 hover:bg-red-500/30 border border-red-500/50 text-red-300 shadow-[0_0_20px_rgba(239,68,68,0.3)] min-w-[120px]" 
                : "bg-gradient-to-r from-cyan-500/20 to-emerald-500/20 hover:from-cyan-500/30 hover:to-emerald-500/30 border border-cyan-400/50 text-cyan-300 shadow-[0_0_20px_rgba(0,255,170,0.3)] min-w-[120px]"
            }
            title={isStreaming ? '停止生成' : '发送消息 (Enter)'}
          >
            {isStreaming ? (
              <>
                <Square className="h-4 w-4 mr-2" />
                中断传输
              </>
            ) : (
              <>
                <Send className="h-4 w-4 mr-2" />
                发送指令
              </>
            )}
          </Button>
        </div>
        
        <div className="flex items-center justify-between text-xs text-cyan-400/60">
          <div className="flex items-center space-x-4">
            <span>
              {isStreaming ? '⚡ 神经网络响应中...' : '💬 准备就绪'}
            </span>
            <span>•</span>
            <span>
              {isStreaming ? '按 Enter 或点击按钮中断' : 'Enter 发送 • Shift+Enter 换行'}
            </span>
          </div>
          
          {/* 字符计数器 */}
          <div className="flex items-center space-x-2">
            <span>{message.length}/2000</span>
            <div className="w-1 h-4 bg-gradient-to-t from-cyan-400 to-emerald-400 rounded-full opacity-60"></div>
          </div>
        </div>

        {/* 工具（服务器）选择按钮与面板 */}
        <div className="flex items-center justify-between">
          <div className="text-xs text-cyan-400/60">
            已选择服务器: {selectedServers.length} / {displayServers.length}
          </div>
          <div className="space-x-2">
            <Button
              type="button"
              className="bg-slate-800/60 hover:bg-slate-800/80 border border-cyan-400/40 text-cyan-300"
              onClick={() => setShowPicker(v => !v)}
            >
              {showPicker ? '收起工具选择' : '选择MCP工具'}
            </Button>
          </div>
        </div>

        {showPicker && (
          <div className="mt-2 p-3 rounded-md border border-cyan-400/30 bg-slate-900/40">
            <div className="flex items-center justify-between mb-2">
              <div className="text-sm text-cyan-300">在线服务器</div>
              <div className="space-x-2">
                <Button type="button" className="h-7 px-2 text-xs" onClick={handleSelectAll}>全选</Button>
                <Button type="button" className="h-7 px-2 text-xs" onClick={handleClearAll}>清空</Button>
              </div>
            </div>
            <div className="grid grid-cols-2 gap-2 max-h-40 overflow-auto pr-1">
              {displayServers.length === 0 && (
                <div className="text-xs text-cyan-400/60">暂无在线服务器</div>
              )}
              {displayServers.map(s => (
                <label key={s.id} className="flex items-center space-x-2 text-sm text-cyan-100/90">
                  <input
                    type="checkbox"
                    className="accent-emerald-400"
                    checked={selectedServers.includes(s.id)}
                    onChange={() => toggleServer(s.id)}
                  />
                  <span>{s.name || s.id}</span>
                </label>
              ))}
            </div>
          </div>
        )}
      </form>
    </div>
  );
};

export default MessageInput;