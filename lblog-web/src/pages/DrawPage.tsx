import { useState, useRef, useEffect, useCallback } from 'react'
import { Input, Dropdown, message } from 'antd'
import type { MenuProps } from 'antd'
import { SendHorizonal, MessageSquarePlus, Bot, PenTool, Save, FolderOpen, PanelRightClose } from 'lucide-react'
import { DrawIoEmbed } from 'react-drawio'
import { useDiagram } from '../contexts/diagram-context'
import { getSiteConfig } from '../services/api'
import { drawChatStream } from '../services/draw'
import type { ChatMessageDTO, SseEvent } from '../types/draw'

interface DisplayMessage extends ChatMessageDTO {
  reasoning?: string
}
import SaveDiagramModal from '../components/SaveDiagramModal'
import DiagramManagerModal from '../components/DiagramManagerModal'

interface DrawPageProps {
  onClose: () => void
}

const HELP_TIPS = [
  '画一个微服务架构图',
  '画一个登录流程图',
  '画一个网络拓扑图',
]

const DrawPage: React.FC<DrawPageProps> = ({ onClose }) => {
  const [aiChatEnabled, setAiChatEnabled] = useState(false)
  useEffect(() => {
    getSiteConfig().then(r => setAiChatEnabled(r.data?.aiDrawChatEnabled ?? false)).catch(() => {})
  }, [])
  const {
    chartXML, loadDiagram, drawioRef, onDrawioLoad, handleDiagramAutoSave,
    sessionId, sessionTitle, isDirty, saving, handleExportResult,
    setSessionTitle, saveDiagram, saveAsDiagram, openDiagram,
  } = useDiagram()

  const [messages, setMessages] = useState<DisplayMessage[]>([])
  const [input, setInput] = useState('')
  const [isLoading, setIsLoading] = useState(false)
  const [isDrawioReady, setIsDrawioReady] = useState(false)
  const [isCollapsed, setIsCollapsed] = useState(false)
  const [panelWidth, setPanelWidth] = useState(380)
  const [isDragging, setIsDragging] = useState(false)
  const [docTitle, setDocTitle] = useState(sessionTitle)
  const abortRef = useRef<AbortController | null>(null)
  const messagesEndRef = useRef<HTMLDivElement>(null)
  const reasoningAccumRef = useRef('')
  const panelWidthRef = useRef(380)
  const contentAreaRef = useRef<HTMLDivElement>(null)

  // 弹窗状态
  const [saveModalOpen, setSaveModalOpen] = useState(false)
  const [saveModalMode, setSaveModalMode] = useState<'save' | 'saveAs'>('save')
  const [managerOpen, setManagerOpen] = useState(false)

  // 同步 sessionTitle → docTitle
  useEffect(() => {
    setDocTitle(sessionTitle)
  }, [sessionTitle])

  const scrollToBottom = useCallback(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' })
  }, [])

  useEffect(() => { scrollToBottom() }, [messages, scrollToBottom])

  // beforeunload 提示
  useEffect(() => {
    const handler = (e: BeforeUnloadEvent) => {
      if (isDirty) {
        e.preventDefault()
        e.returnValue = ''
      }
    }
    window.addEventListener('beforeunload', handler)
    return () => window.removeEventListener('beforeunload', handler)
  }, [isDirty])

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
    reasoningAccumRef.current = ''
    abortRef.current?.abort()
    abortRef.current = null
    setIsLoading(false)
  }

  const handleDrawioLoad = useCallback(() => {
    setIsDrawioReady(true)
    onDrawioLoad()
  }, [onDrawioLoad])

  // ---- 可拖动分割线 ----
  const handleDividerMouseDown = useCallback((e: React.MouseEvent) => {
    if (isCollapsed) return
    e.preventDefault()

    const startX = e.clientX
    const startWidth = panelWidthRef.current

    setIsDragging(true)
    document.body.style.userSelect = 'none'
    document.body.style.cursor = 'col-resize'

    const handleMouseMove = (moveEvent: MouseEvent) => {
      const maxWidth = (contentAreaRef.current?.clientWidth ?? 800) * 0.5
      const newWidth = Math.max(280, Math.min(startWidth - (moveEvent.clientX - startX), maxWidth))
      panelWidthRef.current = newWidth
      setPanelWidth(newWidth)
    }

    const handleMouseUp = () => {
      setIsDragging(false)
      document.body.style.userSelect = ''
      document.body.style.cursor = ''
      document.removeEventListener('mousemove', handleMouseMove)
      document.removeEventListener('mouseup', handleMouseUp)
    }

    document.addEventListener('mousemove', handleMouseMove)
    document.addEventListener('mouseup', handleMouseUp)
  }, [isCollapsed])

  const handleDividerClick = useCallback(() => {
    if (isCollapsed) {
      setIsCollapsed(false)
    }
  }, [isCollapsed])

  // ---- 存储操作 ----

  const handleSave = useCallback(async () => {
    if (sessionId !== null) {
      try {
        await saveDiagram()
        message.success('已保存')
      } catch (e: any) {
        message.error(e.message || '保存失败')
      }
    } else {
      setSaveModalMode('save')
      setSaveModalOpen(true)
    }
  }, [sessionId, saveDiagram])

  const handleSaveAs = useCallback(() => {
    setSaveModalMode('saveAs')
    setSaveModalOpen(true)
  }, [])

  // 键盘快捷键（用 ref 避免闭包过期）
  const handleSaveRef = useRef(handleSave)
  const handleSaveAsRef = useRef(handleSaveAs)
  handleSaveRef.current = handleSave
  handleSaveAsRef.current = handleSaveAs

  useEffect(() => {
    const handler = (e: KeyboardEvent) => {
      if ((e.ctrlKey || e.metaKey) && e.key === 's') {
        e.preventDefault()
        if (e.shiftKey) {
          handleSaveAsRef.current()
        } else {
          handleSaveRef.current()
        }
      }
      if ((e.ctrlKey || e.metaKey) && e.key === 'o') {
        e.preventDefault()
        setManagerOpen(true)
      }
    }
    window.addEventListener('keydown', handler)
    return () => window.removeEventListener('keydown', handler)
  }, [])

  const handleSaveModalSave = async (title: string, description?: string, tags?: string) => {
    await saveAsDiagram(title, description, tags)
    message.success('已保存')
  }

  const handleOpenFromManager = async (id: number) => {
    if (isDirty) {
      // 已通过 beforeunload 确认放弃更改时可以进入
    }
    await openDiagram(id)
  }

  const handleDocTitleChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const v = e.target.value
    setDocTitle(v)
    setSessionTitle(v)
  }

  // 保存按钮下拉菜单（仅已有图表时显示"另存为"）
  const saveMenuItems: MenuProps['items'] = [
    { key: 'save', label: '保存', onClick: handleSave },
    ...(sessionId !== null ? [{ key: 'saveAs', label: '另存为...', onClick: handleSaveAs }] : []),
  ]

  return (
    <div style={{ height: '100%', background: '#f5f5f7', display: 'flex', flexDirection: 'column' }}>
      {/* 全局标题栏 */}
      <div style={{
        display: 'flex', alignItems: 'center', justifyContent: 'space-between',
        padding: '8px 16px', borderBottom: '1px solid #e5e5ea', flexShrink: 0,
      }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
          <PenTool size={18} color="#1677ff" />

          {/* 打开按钮 */}
          <button type="button" onClick={() => setManagerOpen(true)}
            style={{ height: 30, padding: '0 10px', borderRadius: 6, border: '1px solid #d9d9d9', background: '#fff', cursor: 'pointer', color: '#333', fontSize: 12, display: 'flex', alignItems: 'center', gap: 4 }}
            title="打开图表 (Ctrl+O)"
          >
            <FolderOpen size={14} />
            打开
          </button>

          {/* 保存按钮（带下拉） */}
          <Dropdown menu={{ items: saveMenuItems }} trigger={['click']}>
            <button type="button"
              style={{ height: 30, padding: '0 10px', borderRadius: 6, border: '1px solid #1677ff', background: '#1677ff', cursor: 'pointer', color: '#fff', fontSize: 12, display: 'flex', alignItems: 'center', gap: 4, opacity: saving ? 0.6 : 1 }}
              title="保存 (Ctrl+S)"
              disabled={saving}
            >
              <Save size={14} />
              {saving ? '保存中...' : '保存'}
            </button>
          </Dropdown>

          {/* 标题输入 */}
          <input
            value={docTitle}
            onChange={handleDocTitleChange}
            placeholder="输入图表名称"
            style={{ fontSize: 14, fontWeight: 600, border: 'none', outline: 'none', background: 'none', padding: '2 6', width: 200, color: '#1d1d1f', borderBottom: '1px dashed transparent' }}
            onMouseEnter={e => e.currentTarget.style.borderBottomColor = '#d9d9d9'}
            onMouseLeave={e => e.currentTarget.style.borderBottomColor = 'transparent'}
          />

          {/* Dirty 指示 */}
          {isDirty && (
            <span style={{ fontSize: 12, color: '#ff4d4f', display: 'flex', alignItems: 'center', gap: 3 }}>
              <span style={{ width: 6, height: 6, borderRadius: '50%', background: '#ff4d4f', display: 'inline-block' }} />
              未保存
            </span>
          )}
          {!isDirty && sessionId !== null && (
            <span style={{ fontSize: 12, color: '#52c41a', display: 'flex', alignItems: 'center', gap: 3 }}>
              <span style={{ width: 6, height: 6, borderRadius: '50%', background: '#52c41a', display: 'inline-block' }} />
              已保存
            </span>
          )}
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
      <div ref={contentAreaRef} style={{ flex: 1, display: 'flex', minHeight: 0 }}>
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
              autosave={true}
              exportFormat="xmlsvg"
              urlParameters={{
                ui: 'kennedy',
                spin: false,
                libraries: false,
                saveAndExit: false,
                noExitBtn: true,
              }}
              onLoad={handleDrawioLoad}
              onAutoSave={handleDiagramAutoSave}
              onExport={handleExportResult}
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

      {aiChatEnabled && (<>
      {/* 分割线（可拖动） */}
      <div
        onMouseDown={handleDividerMouseDown}
        onClick={handleDividerClick}
        onMouseEnter={e => { e.currentTarget.style.background = isCollapsed ? '#d6e4ff' : '#e5e5ea' }}
        onMouseLeave={e => e.currentTarget.style.background = 'transparent'}
        style={{
          width: 14,
          cursor: isCollapsed ? 'pointer' : 'col-resize',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          flexShrink: 0,
          alignSelf: 'stretch',
          transition: 'background 0.15s',
          position: 'relative',
          userSelect: 'none',
        }}
        title={isCollapsed ? '展开对话' : '拖动调整宽度'}
      >
        <div style={{
          width: 3,
          height: isCollapsed ? 28 : 40,
          borderRadius: 2,
          background: isCollapsed ? '#1677ff' : '#d9d9d9',
          transition: 'background 0.2s, height 0.2s',
        }} />
        {isCollapsed && (
          <span style={{ position: 'absolute', color: '#1677ff', fontSize: 10 }}>▶</span>
        )}
      </div>

      {/* 右侧：Chat 面板 */}
      <div style={{
        width: isCollapsed ? 0 : panelWidth,
        overflow: 'hidden',
        transition: isDragging ? 'none' : 'width 0.2s cubic-bezier(0.4, 0, 0.2, 1)',
        flexShrink: 0,
        display: 'flex',
        flexDirection: 'column',
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
              <button type="button" onClick={() => setIsCollapsed(true)}
                style={{ background: 'none', border: 'none', cursor: 'pointer', padding: '4 8', borderRadius: 6, color: '#666' }}
                title="收起面板"
              >
                <PanelRightClose size={16} />
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
      </>)}
    </div>

      {/* 保存对话框 */}
      <SaveDiagramModal
        open={saveModalOpen}
        onClose={() => setSaveModalOpen(false)}
        onSave={handleSaveModalSave}
        initialTitle={docTitle}
        mode={saveModalMode}
      />

      {/* 图表浏览器 */}
      <DiagramManagerModal
        open={managerOpen}
        onClose={() => setManagerOpen(false)}
        onOpenDiagram={handleOpenFromManager}
      />
    </div>
  )
}
export default DrawPage
