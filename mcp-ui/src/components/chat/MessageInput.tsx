import React, { useState, useRef, useEffect } from 'react';
import { Textarea } from '../ui/textarea'
import { Button } from '../ui/button'
import { Send, Square } from 'lucide-react'

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
      </form>
    </div>
  );
};

export default MessageInput;