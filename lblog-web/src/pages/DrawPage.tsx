import { useState, useRef, useEffect, useCallback } from 'react'
import { Input } from 'antd'
import { SendHorizonal, MessageSquarePlus, Bot, PenTool } from 'lucide-react'
import { DrawIoEmbed } from 'react-drawio'
import { useDiagram } from '../contexts/diagram-context'
import { drawChatStream } from '../services/draw'
import type { ChatMessageDTO, SseEvent } from '../types/draw'

interface DrawPageProps {
  onClose: () => void
}

const HELP_TIPS = [
  '画一个微服务架构图',
  '画一个登录流程图',
  '画一个网络拓扑图',
]

const DrawPage: React.FC<DrawPageProps> = ({ onClose }) => {
  const { chartXML, loadDiagram, drawioRef, onDrawioLoad, handleDiagramAutoSave } = useDiagram()
  const [messages, setMessages] = useState<ChatMessageDTO[]>([])
  const [input, setInput] = useState('')
  const [isLoading, setIsLoading] = useState(false)
  const [isDrawioReady, setIsDrawioReady] = useState(false)
  const [chatCollapsed, setChatCollapsed] = useState(false)
  const [docTitle, setDocTitle] = useState('无标题图表')
  const abortRef = useRef<AbortController | null>(null)
  const messagesEndRef = useRef<HTMLDivElement>(null)

  const scrollToBottom = useCallback(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' })
  }, [])

  useEffect(() => { scrollToBottom() }, [messages, scrollToBottom])

  const handleSend = useCallback(() => {
    const text = input.trim()
    if (!text || isLoading) return

    setInput('')
    const userMsg: ChatMessageDTO = { role: 'user', content: text }
    const updatedMessages = [...messages, userMsg]
    setMessages(updatedMessages)
    setMessages((prev) => [...prev, { role: 'assistant', content: '' }])

    setIsLoading(true)

    const controller = drawChatStream(
      { messages: updatedMessages, xml: chartXML || undefined },
      (event: SseEvent) => {
        if (event.type === 'text-delta') {
          setMessages((prev) => {
            const copy = [...prev]
            const last = copy[copy.length - 1]
            if (last?.role === 'assistant') {
              copy[copy.length - 1] = { ...last, content: last.content + (event.delta || '') }
            }
            return copy
          })
        } else if (event.type === 'tool-call') {
          const xml = event.arguments?.xml
          if (xml && typeof xml === 'string') {
            loadDiagram(xml)
          }
        } else if (event.type === 'error') {
          setMessages((prev) => {
            const copy = [...prev]
            const last = copy[copy.length - 1]
            if (last?.role === 'assistant') {
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
          if (last?.role === 'assistant') {
            copy[copy.length - 1] = { ...last, content: last.content + `\n[错误] ${error.message}` }
          }
          return copy
        })
      },
      () => { setIsLoading(false) },
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
    abortRef.current?.abort()
    abortRef.current = null
    setIsLoading(false)
  }

  const handleDrawioLoad = useCallback(() => {
    setIsDrawioReady(true)
    onDrawioLoad()
  }, [onDrawioLoad])

  return (
    <div style={{ height: '100%', background: '#f5f5f7', display: 'flex', flexDirection: 'column' }}>
      {/* 全局标题栏 */}
      <div style={{
        display: 'flex', alignItems: 'center', justifyContent: 'space-between',
        padding: '8px 16px', borderBottom: '1px solid #e5e5ea', flexShrink: 0,
      }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
          <PenTool size={18} color="#1677ff" />
          <input
            value={docTitle}
            onChange={e => setDocTitle(e.target.value)}
            placeholder="输入图表名称"
            style={{ fontSize: 14, fontWeight: 600, border: 'none', outline: 'none', background: 'none', padding: '2 6', width: 200, color: '#1d1d1f', borderBottom: '1px dashed transparent' }}
            onMouseEnter={e => e.currentTarget.style.borderBottomColor = '#d9d9d9'}
            onMouseLeave={e => e.currentTarget.style.borderBottomColor = 'transparent'}
          />
        </div>
        <div style={{ display: 'flex', alignItems: 'center' }}>
          <button type="button" onClick={onClose}
            style={{ width: 32, height: 32, borderRadius: 6, border: 'none', background: 'none', cursor: 'pointer', color: '#666', fontSize: 16, display: 'flex', alignItems: 'center', justifyContent: 'center' }}
            title="收起到工具箱"
            onMouseEnter={e => { e.currentTarget.style.background = '#e5e5ea'; e.currentTarget.style.color = '#000' }}
            onMouseLeave={e => { e.currentTarget.style.background = 'none'; e.currentTarget.style.color = '#666' }}
          >−</button>
        </div>
      </div>

      {/* 内容区 */}
      <div style={{ flex: 1, display: 'flex', minHeight: 0 }}>
        {/* Draw.io 画布 */}
      <div style={{ flex: 1, display: 'flex', flexDirection: 'column', padding: 8, minWidth: 0 }}>
        <div style={{
          flex: 1,
          borderRadius: 12,
          overflow: 'hidden',
          boxShadow: '0 2px 12px rgba(0,0,0,0.06)',
          border: '1px solid rgba(0,0,0,0.06)',
          position: 'relative',
          background: '#fff',
        }}>
          <div style={{ height: '100%', width: '100%' }}>
            <DrawIoEmbed
              ref={drawioRef}
              xml={chartXML || undefined}
              urlParameters={{
                ui: 'kennedy',
                spin: false,
                libraries: false,
                saveAndExit: false,
                noSaveBtn: true,
                noExitBtn: true,
                modified: false,
              }}
              onLoad={handleDrawioLoad}
              onAutoSave={handleDiagramAutoSave}
            />
          </div>
          {!isDrawioReady && (
            <div style={{
              position: 'absolute',
              inset: 0,
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              color: '#aeaeb2',
              fontSize: 14,
              background: '#fff',
            }}>
              Draw.io panel is loading...
            </div>
          )}
        </div>
      </div>

      {/* 分割线 */}
      <div
        onClick={() => setChatCollapsed(v => !v)}
        onMouseEnter={e => e.currentTarget.style.background = '#e5e5ea'}
        onMouseLeave={e => e.currentTarget.style.background = 'transparent'}
        style={{ width: 14, cursor: 'pointer', display: 'flex', alignItems: 'center', justifyContent: 'center', flexShrink: 0, alignSelf: 'stretch', transition: 'background 0.15s', position: 'relative' }}
        title={chatCollapsed ? '展开对话' : '收起对话'}
      >
        <div style={{ width: 3, height: 28, borderRadius: 2, background: chatCollapsed ? '#1677ff' : '#d9d9d9', transition: 'background 0.2s' }} />
        {chatCollapsed && (
          <span style={{ position: 'absolute', color: '#1677ff', fontSize: 10 }}>▶</span>
        )}
      </div>

      {/* 右侧：Chat 面板 */}
      <div style={{
        flexShrink: 0,
        display: 'flex',
        flexDirection: 'column',
      }}>
        {/* 可收起区域 */}
        <div style={{
          width: chatCollapsed ? 0 : 380,
          overflow: 'hidden',
          transition: 'width 0.2s cubic-bezier(0.4, 0, 0.2, 1)',
          display: 'flex',
          flexDirection: 'column',
          flex: 1,
        }}>
          <div style={{
            flex: 1,
            display: 'flex',
            flexDirection: 'column',
          background: '#fff',
          borderRadius: 12,
          boxShadow: '0 2px 12px rgba(0,0,0,0.06)',
          border: '1px solid rgba(0,0,0,0.06)',
          overflow: 'hidden',
        }}>
          {/* Header */}
          <div style={{
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between',
            padding: '12px 16px',
            borderBottom: '1px solid #e5e5ea',
          }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
              <Bot size={20} color="#1677ff" />
              <span style={{ fontWeight: 600, fontSize: 14, color: '#1d1d1f' }}>对话</span>
            </div>
            <div style={{ display: 'flex', alignItems: 'center', gap: 2 }}>
              <button type="button" onClick={handleNewChat} disabled={isLoading}
                style={{ background: 'none', border: 'none', cursor: isLoading ? 'not-allowed' : 'pointer', padding: '4 8', borderRadius: 6, color: '#666', opacity: isLoading ? 0.5 : 1 }}
                title="新建对话"
              >
                <MessageSquarePlus size={16} />
              </button>
            </div>
          </div>

          {/* Messages */}
          <div style={{
            flex: 1,
            overflow: 'auto',
            padding: '10px 14px',
            background: '#f5f5f7',
          }}>
            {messages.length === 0 ? (
              <div style={{ textAlign: 'center', paddingTop: 24 }}>
                <Bot size={32} color="#aeaeb2" style={{ marginBottom: 10 }} />
                <div style={{ fontSize: 13, color: '#aeaeb2', marginBottom: 14, lineHeight: 1.6 }}>
                  输入绘图指令，AI 将自动生成图表
                </div>
                {HELP_TIPS.map((tip) => (
                  <button key={tip} type="button" onClick={() => setInput(tip)}
                    style={{ display: 'inline-block', margin: 3, padding: '5px 12px', borderRadius: 10, border: '1px dashed #d9d9d9', background: '#fff', color: '#666', fontSize: 12, cursor: 'pointer' }}
                  >
                    {tip}
                  </button>
                ))}
              </div>
            ) : (
              messages.map((msg, i) => (
                <div key={i} style={{ marginBottom: 10, display: 'flex', flexDirection: msg.role === 'user' ? 'row-reverse' : 'row', alignItems: 'flex-start', gap: 6 }}>
                  <div style={{ width: 26, height: 26, borderRadius: '50%', background: msg.role === 'user' ? '#1677ff' : '#52c41a', display: 'flex', alignItems: 'center', justifyContent: 'center', flexShrink: 0 }}>
                    <Bot size={12} color="#fff" />
                  </div>
                  <div style={{ maxWidth: '78%', padding: '7px 12px', borderRadius: 12, background: msg.role === 'user' ? '#1677ff' : '#fff', color: msg.role === 'user' ? '#fff' : '#333', fontSize: 13, lineHeight: 1.5, whiteSpace: 'pre-wrap', wordBreak: 'break-word', boxShadow: msg.role === 'user' ? 'none' : '0 1px 3px rgba(0,0,0,0.06)' }}>
                    {msg.content || (isLoading && msg.role === 'assistant' ? '...' : '')}
                  </div>
                </div>
              ))
            )}
            <div ref={messagesEndRef} />
          </div>

          {/* Input */}
          <div style={{ padding: '10px 14px', borderTop: '1px solid #e5e5ea', display: 'flex', gap: 8, alignItems: 'flex-end', background: '#fff' }}>
            <Input.TextArea
              value={input}
              onChange={(e) => setInput(e.target.value)}
              onKeyDown={handleKeyDown}
              placeholder="输入绘图指令..."
              autoSize={{ minRows: 1, maxRows: 3 }}
              disabled={isLoading}
              style={{ flex: 1, borderRadius: 8, fontSize: 13 }}
            />
            {isLoading ? (
              <button type="button" onClick={handleStop}
                style={{ height: 32, padding: '0 12px', borderRadius: 8, border: '1px solid #ff4d4f', background: '#fff', color: '#ff4d4f', fontSize: 13, cursor: 'pointer', flexShrink: 0, display: 'flex', alignItems: 'center', gap: 4 }}
              >
                停止
              </button>
            ) : (
              <button type="button" onClick={handleSend} disabled={!input.trim()}
                style={{ height: 32, padding: '0 12px', borderRadius: 8, border: 'none', background: !input.trim() ? '#d9d9d9' : 'linear-gradient(135deg, #1677ff, #0958d9)', color: '#fff', fontSize: 13, cursor: !input.trim() ? 'not-allowed' : 'pointer', flexShrink: 0, display: 'flex', alignItems: 'center', gap: 4 }}
              >
                <SendHorizonal size={14} />
                发送
              </button>
            )}
          </div>
        </div>
      </div>
      </div>
    </div>
    </div>
  )
}
export default DrawPage
