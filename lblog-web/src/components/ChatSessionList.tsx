import { useState, useRef, useEffect } from 'react'
import { message, Modal } from 'antd'
import { MessageSquarePlus, Trash2, MessageSquareText, Pencil } from 'lucide-react'
import type { ChatSessionVO } from '../types/chat'

interface ChatSessionListProps {
  sessions: ChatSessionVO[]
  currentSessionId: number | null
  loading: boolean
  onSelect: (sessionId: number) => void
  onNew: () => void
  onRename: (id: number, title: string) => Promise<void>
  onDelete: (id: number) => Promise<void>
  fillContainer?: boolean
}

export default function ChatSessionList({
  sessions, currentSessionId, loading,
  onSelect, onNew, onRename, onDelete, fillContainer,
}: ChatSessionListProps) {
  const [editingId, setEditingId] = useState<number | null>(null)
  const [editValue, setEditValue] = useState('')
  const [deletingId, setDeletingId] = useState<number | null>(null)
  const [hoveredId, setHoveredId] = useState<number | null>(null)
  const inputRef = useRef<HTMLInputElement>(null)

  useEffect(() => {
    if (editingId !== null) {
      inputRef.current?.focus()
      inputRef.current?.select()
    }
  }, [editingId])

  const handleDoubleClick = (session: ChatSessionVO) => {
    setEditingId(session.id)
    setEditValue(session.title || '')
  }

  const handleRenameConfirm = async () => {
    const id = editingId
    if (id === null) return
    const title = editValue.trim()
    if (title) {
      try {
        await onRename(id, title)
      } catch {
        message.error('重命名失败')
      }
    }
    setEditingId(null)
  }

  const handleDelete = (session: ChatSessionVO) => {
    Modal.confirm({
      title: '删除会话',
      content: `确定删除会话"${session.title || '未命名'}"吗？此操作不可恢复。`,
      okText: '删除',
      okType: 'danger',
      cancelText: '取消',
      onOk: async () => {
        setDeletingId(session.id)
        try {
          await onDelete(session.id)
        } catch {
          message.error('删除失败')
        } finally {
          setDeletingId(null)
        }
      },
    })
  }

  const formatTime = (iso: string) => {
    const d = new Date(iso)
    const now = new Date()
    const pad = (n: number) => String(n).padStart(2, '0')
    if (d.toDateString() === now.toDateString()) {
      return `${pad(d.getHours())}:${pad(d.getMinutes())}`
    }
    return `${pad(d.getMonth() + 1)}/${pad(d.getDate())}`
  }

  return (
    <div style={{
      width: fillContainer ? undefined : 220,
      flex: fillContainer ? 1 : undefined,
      flexShrink: 0,
      display: 'flex',
      flexDirection: 'column',
      background: fillContainer ? '#f5f5f7' : '#fafafa',
      borderRight: fillContainer ? 'none' : '1px solid #e5e5ea',
      overflow: 'hidden',
    }}>
      {/* 新建会话按钮 */}
      <div style={{ padding: '10px 12px', borderBottom: '1px solid #e5e5ea' }}>
        <button
          type="button"
          onClick={onNew}
          style={{
            width: '100%',
            height: 34,
            borderRadius: 8,
            border: '1px dashed #d9d9d9',
            background: '#fff',
            cursor: 'pointer',
            color: '#666',
            fontSize: 13,
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            gap: 6,
            transition: 'all 0.2s',
          }}
          onMouseEnter={e => { e.currentTarget.style.borderColor = '#1677ff'; e.currentTarget.style.color = '#1677ff' }}
          onMouseLeave={e => { e.currentTarget.style.borderColor = '#d9d9d9'; e.currentTarget.style.color = '#666' }}
        >
          <MessageSquarePlus size={14} />
          新对话
        </button>
      </div>

      {/* 会话列表 */}
      <div style={{ flex: 1, overflow: 'auto', padding: '6px 8px' }}>
        {loading ? (
          <div style={{ textAlign: 'center', paddingTop: 24, color: '#aeaeb2', fontSize: 12 }}>
            加载中...
          </div>
        ) : sessions.length === 0 ? (
          <div style={{ textAlign: 'center', paddingTop: 24, color: '#aeaeb2', fontSize: 12, lineHeight: 1.6 }}>
            <MessageSquareText size={24} style={{ marginBottom: 8, opacity: 0.4 }} />
            暂无会话
          </div>
        ) : (
          sessions.map(session => {
            const isActive = session.id === currentSessionId
            const isHovered = hoveredId === session.id
            const bgColor = isActive ? '#e6f4ff' : isHovered ? '#f0f0f0' : 'transparent'

            return (
              <div
                key={session.id}
                onClick={() => onSelect(session.id)}
                onMouseEnter={() => setHoveredId(session.id)}
                onMouseLeave={() => setHoveredId(null)}
                style={{
                  padding: '8px 10px',
                  borderRadius: 8,
                  marginBottom: 3,
                  cursor: 'pointer',
                  background: bgColor,
                  transition: 'background 0.15s',
                  position: 'relative',
                }}
              >
                {editingId === session.id ? (
                  <input
                    ref={inputRef}
                    value={editValue}
                    onChange={e => setEditValue(e.target.value)}
                    onBlur={handleRenameConfirm}
                    onKeyDown={e => {
                      if (e.key === 'Enter') handleRenameConfirm()
                      if (e.key === 'Escape') setEditingId(null)
                    }}
                    onClick={e => e.stopPropagation()}
                    style={{
                      width: '100%',
                      border: '1px solid #1677ff',
                      outline: 'none',
                      borderRadius: 4,
                      padding: '2px 6px',
                      fontSize: 12,
                      lineHeight: '20px',
                    }}
                  />
                ) : (
                  <div style={{ display: 'flex', alignItems: 'flex-start', gap: 4 }}>
                    <div style={{ flex: 1, minWidth: 0 }}>
                      <div style={{
                        fontSize: 12,
                        fontWeight: isActive ? 600 : 400,
                        color: '#1d1d1f',
                        overflow: 'hidden',
                        textOverflow: 'ellipsis',
                        whiteSpace: 'nowrap',
                      }}>
                        {session.title || '新会话'}
                      </div>
                      <div style={{
                        fontSize: 11,
                        color: '#aeaeb2',
                        marginTop: 2,
                        overflow: 'hidden',
                        textOverflow: 'ellipsis',
                        whiteSpace: 'nowrap',
                      }}>
                        {session.previewText || `${session.messageCount} 条消息`}
                      </div>
                      <div style={{ fontSize: 10, color: '#d9d9d9', marginTop: 1 }}>
                        {formatTime(session.updatedAt || session.createdAt)}
                      </div>
                    </div>

                    {/* 操作按钮（hover 显示） */}
                    <div style={{ display: 'flex', gap: 1, opacity: isHovered ? 1 : 0, transition: 'opacity 0.15s' }}>
                      <button
                        type="button"
                        onClick={e => { e.stopPropagation(); handleDoubleClick(session) }}
                        style={{
                          border: 'none',
                          background: 'none',
                          cursor: 'pointer',
                          padding: 2,
                          borderRadius: 4,
                          color: '#999',
                          display: 'flex',
                          alignItems: 'center',
                        }}
                        title="重命名"
                      >
                        <Pencil size={11} />
                      </button>
                      <button
                        type="button"
                        onClick={e => { e.stopPropagation(); handleDelete(session) }}
                        disabled={deletingId === session.id}
                        style={{
                          border: 'none',
                          background: 'none',
                          cursor: 'pointer',
                          padding: 2,
                          borderRadius: 4,
                          color: '#ff4d4f',
                          display: 'flex',
                          alignItems: 'center',
                        }}
                        title="删除会话"
                      >
                        <Trash2 size={11} />
                      </button>
                    </div>
                  </div>
                )}
              </div>
            )
          })
        )}
      </div>
    </div>
  )
}
