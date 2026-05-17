import { useState, useRef, useEffect, useCallback } from 'react'
import { Input } from 'antd'
import { SendHorizonal, MessageSquarePlus, PanelLeft, PanelRightClose, X, Bot } from 'lucide-react'
import { DrawIoEmbed } from 'react-drawio'
import { useDiagram } from '../contexts/diagram-context'
import { drawChatStream } from '../services/draw'
import type { ChatMessageDTO, SseEvent } from '../types/draw'

interface DisplayMessage extends ChatMessageDTO {
  reasoning?: string
}

interface DrawChatPanelProps {
  open: boolean
  onClose: () => void
}

const HELP_TIPS = [
  '画一个微服务架构图',
  '画一个登录流程图',
  '画一个网络拓扑图',
]

const DrawChatPanel: React.FC<DrawChatPanelProps> = ({ open, onClose }) => {
  const { chartXML, loadDiagram, drawioRef, onDrawioLoad, handleDiagramAutoSave } = useDiagram()
  const [messages, setMessages] = useState<DisplayMessage[]>([])
  const [input, setInput] = useState('')
  const [isLoading, setIsLoading] = useState(false)
  const [collapsed, setCollapsed] = useState(false)
  const abortRef = useRef<AbortController | null>(null)
  const messagesEndRef = useRef<HTMLDivElement>(null)
  const reasoningAccumRef = useRef('')

  const scrollToBottom = useCallback(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' })
  }, [])

  useEffect(() => {
    scrollToBottom()
  }, [messages, scrollToBottom])

  const handleSend = useCallback(() => {
    const text = input.trim()
    if (!text || isLoading) return

    setInput('')
    reasoningAccumRef.current = ''
    const userMsg: ChatMessageDTO = { role: 'user', content: text }
    const updatedMessages = [...messages, userMsg]
    setMessages(updatedMessages)
    setMessages((prev) => [...prev, { role: 'assistant', content: '' }])

    setIsLoading(true)

    const controller = drawChatStream(
      { messages: updatedMessages, xml: chartXML || undefined },
      (event: SseEvent) => {
        if (event.type === 'reasoning') {
          reasoningAccumRef.current += (event.delta || '')
          setMessages((prev) => {
            const copy = [...prev]
            const last = copy[copy.length - 1]
            if (last?.role === 'assistant') {
              copy[copy.length - 1] = { ...last, reasoning: reasoningAccumRef.current }
            }
            return copy
          })
        } else if (event.type === 'text-delta') {
          setMessages((prev) => {
            const copy = [...prev]
            const last = copy[copy.length - 1]
            if (last && last.role === 'assistant') {
              copy[copy.length - 1] = { ...last, content: last.content + (event.content || '') }
            }
            return copy
          })
        } else if (event.type === 'tool-call') {
          const xml = event.content?.xml || event.content
          if (xml && typeof xml === 'string') {
            loadDiagram(xml)
          }
        } else if (event.type === 'error') {
          setMessages((prev) => {
            const copy = [...prev]
            const last = copy[copy.length - 1]
            if (last && last.role === 'assistant') {
              copy[copy.length - 1] = { ...last, content: last.content + `\n[错误] ${event.content || '请求失败'}` }
            }
            return copy
          })
        }
      },
      (error: Error) => {
        setMessages((prev) => {
          const copy = [...prev]
          const last = copy[copy.length - 1]
          if (last && last.role === 'assistant') {
            copy[copy.length - 1] = { ...last, content: last.content + `\n[错误] ${error.message}` }
          }
          return copy
        })
      },
      () => {
        setIsLoading(false)
      },
    )

    abortRef.current = controller
  }, [input, isLoading, messages, chartXML, loadDiagram])

  const handleStop = useCallback(() => {
    abortRef.current?.abort()
    abortRef.current = null
    setIsLoading(false)
  }, [])

  const handleKeyDown = (e: React.KeyboardEvent) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault()
      handleSend()
    }
  }

  const handleNewChat = () => {
    setMessages([])
    setInput('')
    reasoningAccumRef.current = ''
    abortRef.current?.abort()
    abortRef.current = null
    setIsLoading(false)
  }

  const handleDrawioLoad = () => {
    setTimeout(() => onDrawioLoad(), 100)
  }

  if (!open && !collapsed) return null

  const collapsedBarStyle: React.CSSProperties = {
    position: 'fixed',
    top: 0,
    right: 0,
    width: 56,
    height: '100vh',
    background: '#fff',
    borderLeft: '1px solid #e5e5ea',
    display: 'flex',
    flexDirection: 'column',
    alignItems: 'center',
    paddingTop: 16,
    zIndex: 1100,
    boxShadow: '-2px 0 12px rgba(0,0,0,0.06)',
  }

  if (collapsed && open) {
    return (
      <div style={collapsedBarStyle}>
        <button
          type="button"
          onClick={() => setCollapsed(false)}
          style={{
            background: 'none',
            border: 'none',
            cursor: 'pointer',
            padding: 8,
            borderRadius: 8,
            color: '#666',
          }}
          title="展开面板"
        >
          <PanelLeft size={20} />
        </button>
        <div
          style={{
            writingMode: 'vertical-rl',
            fontSize: 13,
            fontWeight: 500,
            color: '#999',
            marginTop: 32,
            letterSpacing: '0.05em',
          }}
        >
          AI 绘图
        </div>
      </div>
    )
  }

  const handleClose = () => {
    abortRef.current?.abort()
    abortRef.current = null
    setIsLoading(false)
    setCollapsed(false)
    onClose()
  }

  return (
    <>
      <div
        style={{
          position: 'fixed',
          inset: 0,
          background: 'rgba(0,0,0,0.3)',
          zIndex: 1050,
        }}
        onClick={handleClose}
      />
      <div
        style={{
          position: 'fixed',
          top: 0,
          right: 0,
          width: 520,
          maxWidth: '100vw',
          height: '100vh',
          background: '#fff',
          zIndex: 1100,
          display: 'flex',
          flexDirection: 'column',
          boxShadow: '-4px 0 24px rgba(0,0,0,0.1)',
        }}
      >
        <div
          style={{
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between',
            padding: '14px 20px',
            borderBottom: '1px solid #e5e5ea',
          }}
        >
          <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
            <button
              type="button"
              onClick={handleNewChat}
              disabled={isLoading}
              style={{
                background: 'none',
                border: 'none',
                cursor: isLoading ? 'not-allowed' : 'pointer',
                padding: 6,
                borderRadius: 8,
                color: '#666',
                opacity: isLoading ? 0.5 : 1,
              }}
              title="新建对话"
            >
              <MessageSquarePlus size={20} />
            </button>
            <Bot size={22} color="#1677ff" />
            <span style={{ fontWeight: 600, fontSize: 15, color: '#1d1d1f' }}>AI 绘图助手</span>
          </div>
          <div style={{ display: 'flex', alignItems: 'center', gap: 4 }}>
            <button
              type="button"
              onClick={() => setCollapsed(true)}
              style={{
                background: 'none',
                border: 'none',
                cursor: 'pointer',
                padding: 6,
                borderRadius: 8,
                color: '#666',
              }}
              title="收起面板"
            >
              <PanelRightClose size={20} />
            </button>
            <button
              type="button"
              onClick={handleClose}
              style={{
                background: 'none',
                border: 'none',
                cursor: 'pointer',
                padding: 6,
                borderRadius: 8,
                color: '#666',
              }}
              title="关闭"
            >
              <X size={20} />
            </button>
          </div>
        </div>

        <div style={{ flex: '0 0 280px', borderBottom: '1px solid #e5e5ea', position: 'relative' }}>
          <div style={{ width: '100%', height: '100%' }}>
            <DrawIoEmbed
              ref={drawioRef}
              xml={chartXML || undefined}
              urlParameters={{
                ui: 'min',
                spin: true,
                libraries: false,
                noSaveBtn: true,
                noExitBtn: true,
                modified: false,
              }}
              onLoad={handleDrawioLoad}
              onAutoSave={handleDiagramAutoSave}
            />
          </div>
          {!chartXML && (
            <div
              style={{
                position: 'absolute',
                top: 0,
                left: 0,
                right: 0,
                bottom: 0,
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                color: '#aeaeb2',
                fontSize: 13,
                pointerEvents: 'none',
                background: '#fafafa',
              }}
            >
              当 AI 生成图表后会在此显示
            </div>
          )}
        </div>

        <div
          style={{
            flex: 1,
            overflow: 'auto',
            padding: '12px 16px',
            background: '#f5f5f7',
          }}
        >
          {messages.length === 0 ? (
            <div style={{ textAlign: 'center', paddingTop: 32 }}>
              <Bot size={36} color="#aeaeb2" style={{ marginBottom: 12 }} />
              <div style={{ fontSize: 13, color: '#aeaeb2', marginBottom: 16, lineHeight: 1.6 }}>
                输入绘图指令，AI 将自动生成图表
              </div>
              {HELP_TIPS.map((tip) => (
                <button
                  key={tip}
                  type="button"
                  onClick={() => setInput(tip)}
                  style={{
                    display: 'inline-block',
                    margin: 4,
                    padding: '6px 14px',
                    borderRadius: 12,
                    border: '1px dashed #d9d9d9',
                    background: '#fff',
                    color: '#666',
                    fontSize: 13,
                    cursor: 'pointer',
                  }}
                >
                  {tip}
                </button>
              ))}
            </div>
          ) : (
            messages.map((msg, i) => (
              <div
                key={i}
                style={{
                  marginBottom: 10,
                  display: 'flex',
                  flexDirection: msg.role === 'user' ? 'row-reverse' : 'row',
                  alignItems: 'flex-start',
                  gap: 8,
                }}
              >
                <div
                  style={{
                    width: 28,
                    height: 28,
                    borderRadius: '50%',
                    background: msg.role === 'user' ? '#1677ff' : '#52c41a',
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                    flexShrink: 0,
                  }}
                >
                  <Bot size={14} color="#fff" />
                </div>
                <div
                  style={{
                    maxWidth: '75%',
                    padding: '8px 14px',
                    borderRadius: 14,
                    background: msg.role === 'user' ? '#1677ff' : '#fff',
                    color: msg.role === 'user' ? '#fff' : '#333',
                    fontSize: 14,
                    lineHeight: 1.6,
                    whiteSpace: 'pre-wrap',
                    wordBreak: 'break-word',
                    boxShadow: msg.role === 'user' ? 'none' : '0 1px 3px rgba(0,0,0,0.06)',
                  }}
                >
                  {msg.role === 'assistant' && msg.reasoning && (
                    <details style={{ marginBottom: 6, background: '#f0f5ff', borderRadius: 6, padding: '4px 8px' }}>
                      <summary style={{ cursor: 'pointer', fontSize: 11, color: '#1677ff', userSelect: 'none', fontWeight: 500 }}>
                        思考过程
                      </summary>
                      <div style={{ marginTop: 4, fontSize: 12, color: '#666', lineHeight: 1.5, whiteSpace: 'pre-wrap' }}>
                        {msg.reasoning}
                      </div>
                    </details>
                  )}
                  {msg.content || (isLoading && msg.role === 'assistant' ? (
                    <span style={{ display: 'inline-flex', gap: 3 }}>
                      <span style={{ animation: 'pulse 1.4s infinite both' }}>.</span>
                      <span style={{ animation: 'pulse 1.4s infinite both', animationDelay: '0.2s' }}>.</span>
                      <span style={{ animation: 'pulse 1.4s infinite both', animationDelay: '0.4s' }}>.</span>
                    </span>
                  ) : '')}
                </div>
              </div>
            ))
          )}
          <div ref={messagesEndRef} />
        </div>

        <div
          style={{
            padding: '12px 16px',
            borderTop: '1px solid #e5e5ea',
            display: 'flex',
            gap: 8,
            alignItems: 'flex-end',
            background: '#fff',
          }}
        >
          <Input.TextArea
            value={input}
            onChange={(e) => setInput(e.target.value)}
            onKeyDown={handleKeyDown}
            placeholder="输入绘图指令..."
            autoSize={{ minRows: 1, maxRows: 4 }}
            disabled={isLoading}
            style={{ flex: 1, borderRadius: 10, fontSize: 14 }}
          />
          {isLoading ? (
            <button
              type="button"
              onClick={handleStop}
              style={{
                height: 36,
                padding: '0 16px',
                borderRadius: 10,
                border: '1px solid #ff4d4f',
                background: '#fff',
                color: '#ff4d4f',
                fontSize: 14,
                cursor: 'pointer',
                flexShrink: 0,
                display: 'flex',
                alignItems: 'center',
                gap: 4,
              }}
            >
              停止
            </button>
          ) : (
            <button
              type="button"
              onClick={handleSend}
              disabled={!input.trim()}
              style={{
                height: 36,
                padding: '0 16px',
                borderRadius: 10,
                border: 'none',
                background: !input.trim() ? '#d9d9d9' : 'linear-gradient(135deg, #1677ff, #0958d9)',
                color: '#fff',
                fontSize: 14,
                cursor: !input.trim() ? 'not-allowed' : 'pointer',
                flexShrink: 0,
                display: 'flex',
                alignItems: 'center',
                gap: 6,
                transition: 'opacity 0.2s',
              }}
            >
              <SendHorizonal size={16} />
              发送
            </button>
          )}
        </div>
      </div>
    </>
  )
}

export default DrawChatPanel
