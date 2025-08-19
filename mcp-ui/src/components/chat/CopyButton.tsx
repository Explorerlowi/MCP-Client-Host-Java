import React, { useState, useRef, useEffect } from 'react';
import type { ChatMessage } from '../../types/chat';

interface CopyButtonProps {
  messages: ChatMessage[];
  className?: string;
}

const CopyButton: React.FC<CopyButtonProps> = ({ messages, className = '' }) => {
  const [showMenu, setShowMenu] = useState(false);
  const [copied, setCopied] = useState<string | null>(null);
  const menuRef = useRef<HTMLDivElement>(null);
  const buttonRef = useRef<HTMLButtonElement>(null);

  // 点击外部关闭菜单
  useEffect(() => {
    const handleClickOutside = (event: MouseEvent) => {
      if (menuRef.current && !menuRef.current.contains(event.target as Node) &&
          buttonRef.current && !buttonRef.current.contains(event.target as Node)) {
        setShowMenu(false);
      }
    };

    document.addEventListener('mousedown', handleClickOutside);
    return () => document.removeEventListener('mousedown', handleClickOutside);
  }, []);

  // 复制到剪贴板
  const copyToClipboard = async (text: string, type: string) => {
    try {
      await navigator.clipboard.writeText(text);
      setCopied(type);
      setShowMenu(false);
      setTimeout(() => setCopied(null), 2000);
    } catch (err) {
      console.error('复制失败:', err);
    }
  };

  // 获取消息内容（不包含思考内容）
  const getMessageContent = () => {
    return messages
      .map(msg => {
        // 移除工具调用结果标记
        const cleanContent = (msg.content || '').replace(/\[工具调用结果\].*?\[\/工具调用结果\]/gs, '').trim();
        return cleanContent;
      })
      .filter(content => content)
      .join('\n\n');
  };

  // 获取思考内容
  const getReasoningContent = () => {
    return messages
      .map(msg => msg.reasoningContent || '')
      .filter(content => content.trim())
      .join('\n\n');
  };

  // 获取全部内容（按多轮思考回复顺序排列）
  const getAllContent = () => {
    const parts: string[] = [];
    
    messages.forEach((msg, index) => {
      // 添加思考内容
      if (msg.reasoningContent && msg.reasoningContent.trim()) {
        parts.push(`【思考 ${index + 1}】\n${msg.reasoningContent.trim()}`);
      }
      
      // 添加回复内容
      const cleanContent = (msg.content || '').replace(/\[工具调用结果\].*?\[\/工具调用结果\]/gs, '').trim();
      if (cleanContent) {
        parts.push(`【回复 ${index + 1}】\n${cleanContent}`);
      }
    });
    
    return parts.join('\n\n');
  };

  const messageContent = getMessageContent();
  const reasoningContent = getReasoningContent();
  const allContent = getAllContent();

  return (
    <div className={`relative ${className}`}>
      {/* 复制按钮 */}
      <button
        ref={buttonRef}
        onClick={() => setShowMenu(!showMenu)}
        className="group/copy w-8 h-8 rounded-full bg-slate-800/80 hover:bg-slate-700/80 border border-cyan-400/30 hover:border-cyan-400/50 flex items-center justify-center transition-all duration-200 shadow-lg hover:shadow-cyan-400/20"
        title="复制消息"
      >
        {copied ? (
          <svg className="w-4 h-4 text-emerald-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M5 13l4 4L19 7" />
          </svg>
        ) : (
          <svg className="w-4 h-4 text-cyan-400 group-hover/copy:text-cyan-300" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M8 16H6a2 2 0 01-2-2V6a2 2 0 012-2h8a2 2 0 012 2v2m-6 12h8a2 2 0 002-2v-8a2 2 0 00-2-2h-8a2 2 0 00-2 2v8a2 2 0 002 2z" />
          </svg>
        )}
      </button>

      {/* 复制菜单 */}
      {showMenu && (
        <div
          ref={menuRef}
          className="absolute bottom-full left-0 mb-2 w-48 bg-slate-800/95 backdrop-blur-sm border border-cyan-400/30 rounded-lg shadow-xl shadow-cyan-400/10 z-50"
        >
          <div className="p-2 space-y-1">
            {/* 复制消息内容 */}
            {messageContent && (
              <button
                onClick={() => copyToClipboard(messageContent, 'message')}
                className="w-full text-left px-3 py-2 text-sm text-cyan-200 hover:bg-cyan-400/10 hover:text-cyan-100 rounded-md transition-colors flex items-center space-x-2"
              >
                <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M8 12h.01M12 12h.01M16 12h.01M21 12c0 4.418-4.03 8-9 8a9.863 9.863 0 01-4.255-.949L3 20l1.395-3.72C3.512 15.042 3 13.574 3 12c0-4.418 4.03-8 9-8s9 3.582 9 8z" />
                </svg>
                <span>复制消息内容</span>
              </button>
            )}

            {/* 复制思考内容 */}
            {reasoningContent && (
              <button
                onClick={() => copyToClipboard(reasoningContent, 'reasoning')}
                className="w-full text-left px-3 py-2 text-sm text-cyan-200 hover:bg-cyan-400/10 hover:text-cyan-100 rounded-md transition-colors flex items-center space-x-2"
              >
                <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9.663 17h4.673M12 3v1m6.364 1.636l-.707.707M21 12h-1M4 12H3m3.343-5.657l-.707-.707m2.828 9.9a5 5 0 117.072 0l-.548.547A3.374 3.374 0 0014 18.469V19a2 2 0 11-4 0v-.531c0-.895-.356-1.754-.988-2.386l-.548-.547z" />
                </svg>
                <span>复制思考内容</span>
              </button>
            )}

            {/* 复制全部内容 */}
            {allContent && (
              <button
                onClick={() => copyToClipboard(allContent, 'all')}
                className="w-full text-left px-3 py-2 text-sm text-cyan-200 hover:bg-cyan-400/10 hover:text-cyan-100 rounded-md transition-colors flex items-center space-x-2"
              >
                <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z" />
                </svg>
                <span>复制全部内容</span>
              </button>
            )}
          </div>

          {/* 菜单箭头 */}
          <div className="absolute top-full left-4 w-0 h-0 border-l-4 border-l-transparent border-r-4 border-r-transparent border-t-4 border-t-cyan-400/30"></div>
        </div>
      )}

      {/* 复制成功提示 */}
      {copied && (
        <div className="absolute bottom-full left-1/2 transform -translate-x-1/2 mb-2 px-3 py-1 bg-emerald-500/90 text-emerald-100 text-xs rounded-md whitespace-nowrap">
          {copied === 'message' && '消息内容已复制'}
          {copied === 'reasoning' && '思考内容已复制'}
          {copied === 'all' && '全部内容已复制'}
        </div>
      )}
    </div>
  );
};

export default CopyButton;
