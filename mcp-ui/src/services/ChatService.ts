// 聊天服务（SSE 版）：负责与后端 /api/chat2Agent 通信并管理前端消息状态

import type { ChatMessage } from '../types/chat'

type ListenerMap = {
  messagesChanged: Array<(messages: ChatMessage[]) => void>
  loadingChanged: Array<(loading: boolean) => void>
  connectionChanged: Array<(state: { isConnecting: boolean; isConnected: boolean; sessionId: string | null }) => void>
  error: Array<(error: Error) => void>
}

interface ChatServiceOptions {
  chatId?: number
  userId?: number
  agentId?: number
  baseUrl?: string
}

export default class ChatService {
  private chatId: number
  private userId: number
  private agentId: number
  private baseUrl: string

  private messages: ChatMessage[] = []
  private isLoading = false
  private isConnecting = false

  private eventSource: EventSource | null = null
  private sessionId: string | null = null
  private currentAssistantMessageId: string | null = null

  private listeners: ListenerMap = {
    messagesChanged: [],
    loadingChanged: [],
    connectionChanged: [],
    error: []
  }

  constructor(options: ChatServiceOptions = {}) {
    this.chatId = options.chatId ?? 1
    this.userId = options.userId ?? 1
    this.agentId = options.agentId ?? 1
    const envBase = (import.meta as any).env?.VITE_APP_BASE_API as string | undefined
    this.baseUrl = options.baseUrl ?? envBase ?? '/api'
  }

  // 事件监听管理
  on<T extends keyof ListenerMap>(event: T, cb: ListenerMap[T][number]) {
    (this.listeners[event] as any).push(cb)
  }

  off<T extends keyof ListenerMap>(event: T, cb: ListenerMap[T][number]) {
    const arr = this.listeners[event] as any[]
    const idx = arr.indexOf(cb as any)
    if (idx > -1) arr.splice(idx, 1)
  }

  private emit<T extends keyof ListenerMap>(event: T, payload: Parameters<ListenerMap[T][number]>[0]) {
    ;(this.listeners[event] as any[]).forEach((fn: any) => fn(payload))
  }

  // 对外数据获取
  getMessages(): ChatMessage[] {
    return [...this.messages]
  }

  getLoading(): boolean {
    return this.isLoading
  }

  setAgentId(agentId: number) {
    this.agentId = agentId
  }

  setChatId(chatId: number) {
    this.chatId = chatId
  }

  clearMessages() {
    this.messages = []
    this.emit('messagesChanged', this.getMessages())
  }

  // 调后端清空会话消息
  async clearHistory(): Promise<boolean> {
    try {
      const url = `${this.baseUrl}/chat2Agent/chat/${this.chatId}/messages?userId=${this.userId}`
      const res = await fetch(url, { method: 'DELETE' })
      if (!res.ok) throw new Error(`HTTP ${res.status}: ${res.statusText}`)
      const json = await res.json().catch(() => ({}))
      if (json && typeof json === 'object' && 'code' in json && json.code !== 200) {
        throw new Error((json as any).msg || '清空会话失败')
      }
      this.clearMessages()
      return true
    } catch (e) {
      this.emit('error', e instanceof Error ? e : new Error('清空会话失败'))
      return false
    }
  }

  // 初始化加载最近10条（进入页面调用）
  async loadLatest(limit: number = 10) {
    const params = new URLSearchParams({
      userId: String(this.userId),
      page: '1',
      size: String(limit),
      descOrder: 'true'
    })
    const res = await fetch(`${this.baseUrl}/chat2Agent/history/${this.chatId}?${params.toString()}`)
    if (!res.ok) return
    const data = await res.json().catch(() => null)
    if (!data || data.code !== 200 || !Array.isArray(data.data)) return
    const list: ChatMessage[] = data.data.map((m: any) => ({
      id: String(m.chatMessage_id ?? m.id ?? m.chatMessageId ?? `${m.sort_id ?? m.sortId}`),
      role: ((m.sender_role ?? m.senderRole) === 1
        ? 'user'
        : (m.sender_role ?? m.senderRole) === 2
        ? 'assistant'
        : 'system') as any,
      content: m.message_content ?? m.messageContent ?? '',
      timestamp: new Date((m.create_time ?? m.createTime) ?? Date.now()),
      reasoningContent: m.reasoning_content ?? m.reasoningContent ?? '',
      extraContent: m.extra_content ?? m.extraContent ?? '',
      metadata: { sortId: m.sort_id ?? m.sortId }
    }))
    // 后端按正序返回，直接使用，最新的在底部
    this.messages = list
    this.emit('messagesChanged', this.getMessages())
  }

  // 上滑加载更多（基于最早一条的 sort_id 游标）
  async loadMore() {
    if (this.messages.length === 0) return
    // 找到当前最早一条消息的 sort_id
    const earliest = this.messages[0]
    const earliestSortId = earliest?.metadata?.sortId
    const params = new URLSearchParams({
      userId: String(this.userId),
      size: '20',
      descOrder: 'true',
      lastSortId: String(earliestSortId ?? 0)
    })
    const res = await fetch(`${this.baseUrl}/chat2Agent/history/${this.chatId}?${params.toString()}`)
    if (!res.ok) return
    const data = await res.json().catch(() => null)
    if (!data || data.code !== 200 || !Array.isArray(data.data) || data.data.length === 0) return
    const list: ChatMessage[] = data.data.map((m: any) => ({
      id: String(m.chatMessage_id ?? m.id ?? m.chatMessageId ?? `${m.sort_id ?? m.sortId}`),
      role: ((m.sender_role ?? m.senderRole) === 1
        ? 'user'
        : (m.sender_role ?? m.senderRole) === 2
        ? 'assistant'
        : 'system') as any,
      content: m.message_content ?? m.messageContent ?? '',
      timestamp: new Date((m.create_time ?? m.createTime) ?? Date.now()),
      reasoningContent: m.reasoning_content ?? m.reasoningContent ?? '',
      extraContent: m.extra_content ?? m.extraContent ?? '',
      metadata: { sortId: m.sort_id ?? m.sortId }
    }))
    // list 是倒序（老→更老），插入到现有最前面，保持整体正序
    this.messages = [...list.reverse(), ...this.messages]
    this.emit('messagesChanged', this.getMessages())
  }

  // 发送消息（建立 SSE → 追加用户消息 → 创建助手占位 → POST /send）
  async sendMessage(text: string) {
    const content = text?.trim()
    if (!content || this.isLoading) return

    // 1) 追加用户消息
    const userMsg: ChatMessage = {
      id: `${Date.now()}-user`,
      role: 'user',
      content,
      timestamp: new Date()
    }
    this.messages.push(userMsg)
    this.emit('messagesChanged', this.getMessages())

    // 2) 设置加载状态
    this.setLoading(true)

    try {
      // 3) 确保 SSE 连接
      await this.ensureSSEConnection()

      // 4) 创建助手占位消息
      const assistantId = `${Date.now()}-assistant`
      this.currentAssistantMessageId = assistantId
      const assistantMsg: ChatMessage = {
        id: assistantId,
        role: 'assistant',
        content: '',
        timestamp: new Date()
      }
      this.messages.push(assistantMsg)
      this.emit('messagesChanged', this.getMessages())

      // 5) POST /send（附带所选服务器列表）
      const url = `${this.baseUrl}/chat2Agent/send`
      const body = new URLSearchParams({
        sessionId: this.sessionId || '',
        message: content,
        chatId: String(this.chatId),
        userId: String(this.userId),
        agentId: String(this.agentId)
      })

      // 从 localStorage 读取服务器选择并附加
      try {
        const saved = localStorage.getItem('mcp_selected_servers') || ''
        if (saved) {
          body.append('servers', saved)
        }
      } catch {}

      const res = await fetch(url, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/x-www-form-urlencoded'
        },
        body
      })

      if (!res.ok) {
        const errText = await res.text().catch(() => '')
        throw new Error(`HTTP ${res.status}: ${res.statusText}${errText ? ` - ${errText}` : ''}`)
      }

      const json = await res.json().catch(() => ({}))
      if (json && typeof json === 'object' && 'code' in json && json.code !== 200) {
        throw new Error((json as any).msg || '消息发送失败')
      }
    } catch (e) {
      const err = e instanceof Error ? e : new Error('发送消息失败')
      this.appendAssistantError(`抱歉，处理您的请求时出现错误：${err.message}`)
      this.setLoading(false)
      this.closeSSE()
      this.emit('error', err)
    }
  }

  // 停止生成
  async stopGeneration(): Promise<boolean> {
    if (!this.sessionId) return false
    try {
      const url = `${this.baseUrl}/chat2Agent/stop`
      const body = new URLSearchParams({
        sessionId: this.sessionId,
        chatId: String(this.chatId),
        userId: String(this.userId)
      })
      const res = await fetch(url, {
        method: 'POST',
        headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
        body
      })
      if (!res.ok) throw new Error(`HTTP ${res.status}: ${res.statusText}`)
      // 提前关闭加载状态与连接，避免“正在思考”残留
      if (this.currentAssistantMessageId) {
        const idx = this.messages.findIndex(m => m.id === this.currentAssistantMessageId)
        if (idx >= 0) {
          this.messages[idx] = { ...this.messages[idx], isStreaming: false }
          this.emit('messagesChanged', this.getMessages())
        }
      }
      this.setLoading(false)
      setTimeout(() => this.closeSSE(), 100)
      return true
    } catch (e) {
      this.emit('error', e instanceof Error ? e : new Error('停止生成失败'))
      return false
    }
  }

  // 内部：SSE 建连与事件绑定
  private async ensureSSEConnection() {
    if (this.eventSource && this.eventSource.readyState === EventSource.OPEN) return
    this.sessionId = this.generateSessionId()
    const sseUrl = `${this.baseUrl}/chat2Agent/stream/${encodeURIComponent(this.sessionId)}`

    this.setConnecting(true)
    await new Promise<void>((resolve, reject) => {
      try {
        this.eventSource = new EventSource(sseUrl)
        this.eventSource.onopen = () => {
          this.setConnecting(false)
          resolve()
        }
        this.bindSSEHandlers()
        this.eventSource.onerror = () => {
          this.setConnecting(false)
          reject(new Error('SSE连接失败'))
        }
        setTimeout(() => {
          if (this.isConnecting) {
            this.setConnecting(false)
            this.closeSSE()
            reject(new Error('SSE连接超时'))
          }
        }, 15000)
      } catch (e) {
        this.setConnecting(false)
        reject(e as Error)
      }
    })
  }

  private bindSSEHandlers() {
    if (!this.eventSource) return
    // 思考事件：解析 JSON 并累加到 reasoningContent
    this.eventSource.addEventListener('thinking', (ev) => {
      this.setLoading(true)
      const data = (ev as MessageEvent).data
      try {
        const obj = typeof data === 'string' ? JSON.parse(data) : data
        const piece = obj?.content ?? ''
        const id = this.currentAssistantMessageId
        if (!id) return
        const idx = this.messages.findIndex(m => m.id === id)
        if (idx >= 0) {
          const prev = this.messages[idx].reasoningContent || ''
          this.messages[idx] = {
            ...this.messages[idx],
            isStreaming: true,
            reasoningContent: prev + String(piece)
          }
          this.emit('messagesChanged', this.getMessages())
        }
      } catch {
        // 忽略解析失败
      }
    })

    // 消息增量
    this.eventSource.addEventListener('message', (ev) => {
      const raw = (ev as MessageEvent).data
      let delta = ''
      try {
        const obj = typeof raw === 'string' ? JSON.parse(raw) : raw
        delta = obj?.content ?? String(raw ?? '')
      } catch {
        delta = String(raw ?? '')
      }
      const id = this.currentAssistantMessageId
      if (!id) return
      const idx = this.messages.findIndex(m => m.id === id)
      if (idx >= 0) {
        this.messages[idx] = {
          ...this.messages[idx],
          content: (this.messages[idx].content || '') + String(delta)
        }
        this.emit('messagesChanged', this.getMessages())
      }
    })

    // 完成
    this.eventSource.addEventListener('complete', (ev) => {
      try {
        const data = (ev as MessageEvent).data
        const obj = typeof data === 'string' ? JSON.parse(data) : data
        const full = obj?.fullContent ?? ''
        const extra = obj?.extraContent ?? ''
        const id = this.currentAssistantMessageId
        if (id) {
          const idx = this.messages.findIndex(m => m.id === id)
          if (idx >= 0) {
            this.messages[idx] = {
              ...this.messages[idx],
              content: String(full),
              extraContent: typeof extra === 'string' ? extra : JSON.stringify(extra),
              isStreaming: false
            }
            this.emit('messagesChanged', this.getMessages())
          }
        }
      } catch {
        // 忽略解析失败
      }
      this.setLoading(false)
      setTimeout(() => this.closeSSE(), 400)
    })

    // 错误
    this.eventSource.addEventListener('error', (ev) => {
      const content = (ev as MessageEvent).data || '未知错误'
      this.appendAssistantError(`\n\n❌ 错误：${content}`)
      // 错误时也将流状态置为结束
      if (this.currentAssistantMessageId) {
        const idx = this.messages.findIndex(m => m.id === this.currentAssistantMessageId)
        if (idx >= 0) {
          this.messages[idx] = { ...this.messages[idx], isStreaming: false }
          this.emit('messagesChanged', this.getMessages())
        }
      }
      this.setLoading(false)
      this.closeSSE()
    })

    // 停止
    this.eventSource.addEventListener('stopped', (ev) => {
      const data = (ev as MessageEvent).data
      if (data) {
        try {
          const obj = JSON.parse(data)
          if (obj?.fullContent && this.currentAssistantMessageId) {
            const idx = this.messages.findIndex(m => m.id === this.currentAssistantMessageId)
            if (idx >= 0) {
              this.messages[idx] = { ...this.messages[idx], content: String(obj.fullContent) }
            }
          }
        } catch {
          // 忽略解析错误
        }
      }
      this.emit('messagesChanged', this.getMessages())
      this.setLoading(false)
      setTimeout(() => this.closeSSE(), 100)
    })
  }

  private appendAssistantError(text: string) {
    if (this.currentAssistantMessageId) {
      const idx = this.messages.findIndex(m => m.id === this.currentAssistantMessageId)
      if (idx >= 0) {
        this.messages[idx] = {
          ...this.messages[idx],
          content: (this.messages[idx].content || '') + text,
          metadata: { ...(this.messages[idx].metadata || {}), isError: true }
        }
        this.emit('messagesChanged', this.getMessages())
        return
      }
    }
    // 如果没有占位消息，单独追加一条错误消息
    const errMsg: ChatMessage = {
      id: `${Date.now()}-error`,
      role: 'assistant',
      content: text,
      timestamp: new Date(),
      metadata: { isError: true }
    }
    this.messages.push(errMsg)
    this.emit('messagesChanged', this.getMessages())
  }

  private setLoading(v: boolean) {
    this.isLoading = v
    this.emit('loadingChanged', v)
  }

  private setConnecting(v: boolean) {
    this.isConnecting = v
    this.emit('connectionChanged', {
      isConnecting: v,
      isConnected: !!this.eventSource && this.eventSource.readyState === EventSource.OPEN,
      sessionId: this.sessionId
    })
  }

  private generateSessionId(): string {
    return `session_${this.userId}_${this.chatId}`
  }

  private closeSSE() {
    if (this.eventSource) {
      try { this.eventSource.close() } catch {}
    }
    this.eventSource = null
    this.sessionId = null
    this.currentAssistantMessageId = null
    this.setConnecting(false)
  }
}


