import { useState } from 'react'
import './App.css'
import ServerManagement from './components/server/ServerManagement'
import ChatInterface from './components/chat/ChatInterface'
import { Tabs, TabsContent, TabsList, TabsTrigger } from './components/ui/tabs'
import { Alert, AlertDescription, AlertTitle } from './components/ui/alert'
import { CircleAlert } from 'lucide-react'

function App() {
  const [activeTab, setActiveTab] = useState<'chat' | 'servers'>('chat')
  const [error, setError] = useState<string | null>(null)

  const clearError = () => {
    setError(null)
  }

  return (
    <div className="h-screen bg-gradient-to-br from-slate-950 via-black to-slate-900 text-cyan-300 relative overflow-hidden">
      {/* 背景动画网格 */}
      <div className="absolute inset-0 bg-[linear-gradient(rgba(0,255,170,0.03)_1px,transparent_1px),linear-gradient(90deg,rgba(0,255,170,0.03)_1px,transparent_1px)] bg-[size:50px_50px] animate-pulse"></div>
      
      {/* 顶部装饰线 */}
      <div className="absolute top-0 left-0 w-full h-1 bg-gradient-to-r from-transparent via-cyan-400 to-transparent shadow-[0_0_20px_rgba(0,255,170,0.5)]"></div>
      
      {/* 主容器 */}
      <div className="relative z-10 flex flex-col h-full">
        {/* 机甲风格导航栏 */}
        <nav className="bg-black/80 backdrop-blur-xl border-b border-cyan-400/30 shadow-[0_0_30px_rgba(0,255,170,0.2)]">
          <div className="flex items-center justify-between px-8 py-4">
            <div className="flex items-center space-x-4">
              <div className="w-8 h-8 bg-gradient-to-r from-cyan-400 to-emerald-400 rounded-full shadow-[0_0_20px_rgba(0,255,170,0.6)] animate-pulse"></div>
              <h1 className="font-['Orbitron'] text-2xl font-bold bg-gradient-to-r from-cyan-400 to-emerald-400 bg-clip-text text-transparent">
                MCP NEXUS
              </h1>
              <div className="hidden md:flex items-center space-x-2 text-xs text-cyan-400/60">
                <div className="w-2 h-2 bg-emerald-400 rounded-full animate-pulse"></div>
                <span>SYSTEM ONLINE</span>
              </div>
            </div>
            
            <Tabs value={activeTab} onValueChange={(v) => setActiveTab(v as any)}>
              <TabsList className="bg-slate-900/50 border border-cyan-400/30 backdrop-blur-sm">
                <TabsTrigger 
                  value="chat" 
                  className="data-[state=active]:bg-gradient-to-r data-[state=active]:from-cyan-500/20 data-[state=active]:to-emerald-500/20 data-[state=active]:text-cyan-300 data-[state=active]:shadow-[0_0_10px_rgba(0,255,170,0.3)]"
                >
                  <span className="flex items-center space-x-2">
                    <div className="w-2 h-2 bg-cyan-400 rounded-full"></div>
                    <span>AI 终端</span>
                  </span>
                </TabsTrigger>
                <TabsTrigger 
                  value="servers"
                  className="data-[state=active]:bg-gradient-to-r data-[state=active]:from-orange-500/20 data-[state=active]:to-red-500/20 data-[state=active]:text-orange-300 data-[state=active]:shadow-[0_0_10px_rgba(255,107,53,0.3)]"
                >
                  <span className="flex items-center space-x-2">
                    <div className="w-2 h-2 bg-orange-400 rounded-full"></div>
                    <span>服务器集群</span>
                  </span>
                </TabsTrigger>
              </TabsList>
            </Tabs>
          </div>
        </nav>

        {/* 错误提示 - 机甲风格 */}
        {error && (
          <div className="mx-8 mt-4">
            <Alert variant="destructive" className="bg-red-950/50 border-red-500/50 backdrop-blur-sm shadow-[0_0_20px_rgba(239,68,68,0.3)]">
              <CircleAlert className="h-4 w-4 text-red-400" />
              <AlertTitle className="text-red-300">SYSTEM ALERT</AlertTitle>
              <AlertDescription className="text-red-200">
                {error}
                <button 
                  onClick={clearError} 
                  className="ml-3 px-2 py-1 text-xs bg-red-500/20 hover:bg-red-500/30 border border-red-500/50 rounded transition-colors"
                >
                  DISMISS
                </button>
              </AlertDescription>
            </Alert>
          </div>
        )}

        {/* 主内容区域 */}
        <main className="flex-1 p-8 overflow-hidden">
          <Tabs value={activeTab} onValueChange={(v) => setActiveTab(v as any)} className="h-full">
            <TabsContent value="chat" className="h-full">
              <ChatInterface />
            </TabsContent>
            <TabsContent value="servers" className="h-full">
              <ServerManagement onError={setError} />
            </TabsContent>
          </Tabs>
        </main>
      </div>
      
      {/* 底部装饰线 */}
      <div className="absolute bottom-0 left-0 w-full h-1 bg-gradient-to-r from-transparent via-orange-400 to-transparent shadow-[0_0_20px_rgba(255,107,53,0.5)]"></div>
    </div>
  )
}

export default App
