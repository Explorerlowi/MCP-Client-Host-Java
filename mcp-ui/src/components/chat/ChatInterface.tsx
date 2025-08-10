import React, { useEffect, useMemo, useRef, useState } from 'react';
import type { ChatMessage } from '../../types/chat';
import MessageList from './MessageList';
import MessageInput from './MessageInput';
import './ChatInterface.css';
import { Card } from '../ui/card';
import { ScrollArea } from '../ui/scroll-area';
import { Button } from '../ui/button';
import { AlertDialog, AlertDialogAction, AlertDialogCancel, AlertDialogContent, AlertDialogDescription, AlertDialogFooter, AlertDialogHeader, AlertDialogTitle, AlertDialogTrigger } from '../ui/alert-dialog';
import { Square, Trash2 } from 'lucide-react';
import ChatService from '../../services/ChatService';

interface ChatInterfaceProps {
  conversationId?: string;
  onConversationChange?: (conversationId: string) => void;
}

const ChatInterface: React.FC<ChatInterfaceProps> = ({
  conversationId,
  onConversationChange
}) => {
  const [messages, setMessages] = useState<ChatMessage[]>([]);
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const chatService = useMemo(() => new ChatService({ baseUrl: '/api' }), []);
  const [currentConversationId, setCurrentConversationId] = useState<string>(
    conversationId || ''
  );
  const messagesEndRef = useRef<HTMLDivElement>(null);
  const containerRef = useRef<HTMLDivElement>(null);
  const isLoadingMoreRef = useRef(false);
  const prevScrollHeightRef = useRef<number>(0);

  // 自动滚动到最新消息
  const scrollToBottom = (smooth: boolean = false) => {
    messagesEndRef.current?.scrollIntoView({ behavior: smooth ? 'smooth' : 'instant' });
  };

  // 不再在每次消息变更时强制滚底，避免“上滑加载更多”被打断

  // 初次加载最新10条
  useEffect(() => {
    (async () => {
      await chatService.loadLatest(10)
      // 首屏加载后立即滚到底部，不使用动画
      scrollToBottom(false)
    })()
  }, [chatService])

  // 绑定 ChatService 事件
  useEffect(() => {
    const onMsgs = (list: ChatMessage[]) => {
      const prevLength = messages.length
      setMessages(list)
      // 只有当消息增加时才滚动到底部（避免加载更多时的干扰）
      if (list.length > prevLength) {
        // 立即滚动到底部，不使用动画
        setTimeout(() => scrollToBottom(false), 50)
      }
    }
    const onLoading = (v: boolean) => {
      setIsLoading(v)
      if (v) scrollToBottom(false) // 开始生成时立即滚到最底部，不使用动画
    }
    const onError = (e: Error) => setError(e.message)
    chatService.on('messagesChanged', onMsgs)
    chatService.on('loadingChanged', onLoading)
    chatService.on('error', onError)
    // 初始化
    setMessages(chatService.getMessages())
    setIsLoading(chatService.getLoading())
    return () => {
      chatService.off('messagesChanged', onMsgs)
      chatService.off('loadingChanged', onLoading)
      chatService.off('error', onError)
    }
  }, [chatService])

  // 上滑加载更多：保持可视区域不跳动
  useEffect(() => {
    const container = containerRef.current
    if (!container) return
    const onScroll = async () => {
      if (isLoadingMoreRef.current) return
      if (container.scrollTop <= 0) {
        isLoadingMoreRef.current = true
        prevScrollHeightRef.current = container.scrollHeight
        await chatService.loadMore()
        // 等待下一帧让DOM渲染完成再校正滚动位置
        requestAnimationFrame(() => {
          const newHeight = container.scrollHeight
          const delta = newHeight - prevScrollHeightRef.current
          container.scrollTop = delta
          isLoadingMoreRef.current = false
        })
      }
    }
    container.addEventListener('scroll', onScroll)
    return () => container.removeEventListener('scroll', onScroll)
  }, [chatService])

  const handleSendMessage = async (content: string) => {
    setError(null)
    await chatService.sendMessage(content)
  }

  const handleClearChat = () => {
    const ok = window.confirm('确定要清空当前对话的所有消息吗？该操作不可撤销。')
    if (!ok) return
    ;(async () => {
      const success = await chatService.clearHistory()
      if (success) {
        setMessages([])
        setCurrentConversationId('')
        setError(null)
        onConversationChange?.('')
      }
    })()
  };

  return (
    <div className="h-full flex flex-col max-w-6xl mx-auto">
      {/* 机甲风格聊天容器 - 填充可用高度 */}
      <div className="h-full bg-black/40 backdrop-blur-xl border border-cyan-400/30 rounded-2xl shadow-[0_0_40px_rgba(0,255,170,0.15)] overflow-hidden flex flex-col"  style={{ minHeight: '600px' }}>
        {/* 顶部装饰条 */}
        <div className="w-full h-1 bg-gradient-to-r from-cyan-400 via-emerald-400 to-cyan-400 shadow-[0_0_10px_rgba(0,255,170,0.6)] flex-shrink-0"></div>
        
        {/* 聊天头部 - 机甲控制台风格 - 固定高度 */}
        <div className="flex items-center justify-between p-4 border-b border-cyan-400/20 bg-gradient-to-r from-slate-950/50 to-slate-900/50 flex-shrink-0">
          <div className="flex items-center space-x-4">
            <div className="relative">
              <div className="w-8 h-8 bg-gradient-to-r from-cyan-400 to-emerald-400 rounded-full shadow-[0_0_20px_rgba(0,255,170,0.6)] animate-pulse"></div>
              <div className="absolute -top-1 -right-1 w-3 h-3 bg-emerald-400 rounded-full animate-ping"></div>
            </div>
            <div>
              <h2 className="font-['Orbitron'] text-lg font-bold bg-gradient-to-r from-cyan-300 to-emerald-300 bg-clip-text text-transparent">
                AI 神经网络终端
              </h2>
              <div className="flex items-center space-x-2 text-xs text-cyan-400/60">
                <div className="w-2 h-2 bg-emerald-400 rounded-full animate-pulse"></div>
                <span>神经链接已建立</span>
                {currentConversationId && (
                  <>
                    <span>•</span>
                    <span>会话ID: {currentConversationId.slice(0, 8)}...</span>
                  </>
                )}
              </div>
            </div>
          </div>
          
          <div className="flex items-center space-x-3">
            {isLoading && (
              <Button 
                onClick={() => chatService.stopGeneration()} 
                className="bg-red-500/20 hover:bg-red-500/30 border border-red-500/50 text-red-300 shadow-[0_0_10px_rgba(239,68,68,0.3)]" 
                size="sm"
              >
                <Square className="h-4 w-4 mr-1" /> 
                中断传输
              </Button>
            )}
            
            <AlertDialog>
              <AlertDialogTrigger asChild>
                <Button 
                  className="bg-orange-500/20 hover:bg-orange-500/30 border border-orange-500/50 text-orange-300 shadow-[0_0_10px_rgba(255,107,53,0.3)]" 
                  size="sm"
                >
                  <Trash2 className="h-4 w-4 mr-1" /> 
                  清除记录
                </Button>
              </AlertDialogTrigger>
              <AlertDialogContent className="bg-slate-950/95 border-cyan-400/30 backdrop-blur-xl">
                <AlertDialogHeader>
                  <AlertDialogTitle className="text-cyan-300 font-['Orbitron']">系统清除确认</AlertDialogTitle>
                  <AlertDialogDescription className="text-cyan-200/80">
                    即将清除当前会话的所有神经网络记录。此操作无法撤销。
                  </AlertDialogDescription>
                </AlertDialogHeader>
                <AlertDialogFooter>
                  <AlertDialogCancel className="bg-slate-800/50 border-slate-600 text-slate-300">取消</AlertDialogCancel>
                  <AlertDialogAction 
                    onClick={handleClearChat}
                    className="bg-red-500/20 hover:bg-red-500/30 border border-red-500/50 text-red-300"
                  >
                    确认清除
                  </AlertDialogAction>
                </AlertDialogFooter>
              </AlertDialogContent>
            </AlertDialog>
          </div>
        </div>

        {/* 消息区域 - 全息显示效果 - 自动填充剩余空间 */}
        <div className="flex-1 relative overflow-hidden">
          <ScrollArea className="h-full p-4" ref={containerRef as any}>
            <div className="space-y-4">
              <MessageList messages={messages} />
              <div ref={messagesEndRef} />
            </div>
          </ScrollArea>
          
          {/* 侧边全息装饰 */}
          <div className="absolute top-0 right-0 w-1 h-full bg-gradient-to-b from-cyan-400/30 to-emerald-400/30 shadow-[0_0_10px_rgba(0,255,170,0.3)]"></div>
        </div>

        {/* 输入区域 - 控制台风格 - 固定高度 */}
        <div className="p-4 border-t border-cyan-400/20 bg-gradient-to-r from-slate-950/30 to-slate-900/30 flex-shrink-0">
          <MessageInput
            onSendMessage={handleSendMessage}
            onStop={() => chatService.stopGeneration()}
            isStreaming={isLoading}
            placeholder="输入指令与AI神经网络通信..."
          />
        </div>
      </div>
    </div>
  );
};

export default ChatInterface;