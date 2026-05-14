import { useState, useRef, useCallback, useEffect } from 'react'
import { Wrench, PencilRuler } from 'lucide-react'

interface ToolItem {
  id: string
  label: string
  icon: React.ReactNode
  action: () => void
}

interface DrawFloatingButtonProps {
  onOpenDraw: () => void
  onPositionChange?: (pos: { left: number; top: number }) => void
}

const DrawFloatingButton: React.FC<DrawFloatingButtonProps> = ({ onOpenDraw, onPositionChange }) => {
  const [hover, setHover] = useState(false)
  const [pos, setPos] = useState({ left: 0, top: 0 })
  const dragging = useRef(false)
  const dragStart = useRef({ x: 0, y: 0, left: 0, top: 0 })
  const hideTimer = useRef<number | null>(null)

  const tools: ToolItem[] = [
    { id: 'draw', label: '绘图工具', icon: <PencilRuler size={14} />, action: () => { setHover(false); onOpenDraw() } },
  ]

  useEffect(() => {
    const p = { left: window.innerWidth - 72, top: window.innerHeight - 80 }
    setPos(p)
    onPositionChange?.(p)
  }, [])

  useEffect(() => {
    onPositionChange?.(pos)
  }, [pos])

  const handleMouseDown = useCallback((e: React.MouseEvent) => {
    e.preventDefault()
    dragging.current = true
    dragStart.current = { x: e.clientX, y: e.clientY, left: pos.left, top: pos.top }

    const onMove = (ev: MouseEvent) => {
      if (!dragging.current) return
      const mw = window.innerWidth - 48
      const mh = window.innerHeight - 48
      setPos({
        left: Math.max(0, Math.min(mw, dragStart.current.left + ev.clientX - dragStart.current.x)),
        top: Math.max(0, Math.min(mh, dragStart.current.top + ev.clientY - dragStart.current.y)),
      })
    }
    const onUp = () => { dragging.current = false; window.removeEventListener('mousemove', onMove); window.removeEventListener('mouseup', onUp) }

    window.addEventListener('mousemove', onMove)
    window.addEventListener('mouseup', onUp)
  }, [pos])

  const show = () => {
    if (dragging.current) return
    if (hideTimer.current !== null) clearTimeout(hideTimer.current)
    setHover(true)
  }
  const hide = () => {
    if (dragging.current) return
    hideTimer.current = window.setTimeout(() => setHover(false), 300)
  }

  return (
    <div style={{
      position: 'fixed',
      left: pos.left,
      top: pos.top,
      zIndex: 1100,
    }}>
      <div
        onMouseDown={handleMouseDown}
        onMouseEnter={show}
        onMouseLeave={hide}
        style={{
          cursor: 'grab',
          userSelect: 'none',
          borderRadius: 10,
          overflow: 'hidden',
          background: 'linear-gradient(135deg, #1677ff 0%, #4096ff 100%)',
          boxShadow: hover
            ? '0 8px 30px rgba(22,119,255,0.25), 0 2px 8px rgba(0,0,0,0.06)'
            : '0 4px 14px rgba(22,119,255,0.3)',
          width: hover ? 104 : 36,
          transition: 'width 0.25s cubic-bezier(0.4, 0, 0.2, 1), box-shadow 0.3s',
        }}
      >
        {/* 头部 */}
        <div style={{
          display: 'flex', alignItems: 'center',
          height: 36, paddingLeft: 9, boxSizing: 'border-box',
          color: '#fff',
        }}>
          <Wrench size={14} style={{ flexShrink: 0 }} />
        </div>

        {/* 工具列表 */}
        <div style={{
          background: '#fff',
          borderRadius: '0 0 10px 10px',
          overflow: 'hidden',
          maxHeight: hover ? 80 : 0,
          opacity: hover ? 1 : 0,
          transition: hover
            ? 'max-height 0.2s ease 0.2s, opacity 0.15s ease 0.2s'
            : 'max-height 0.18s ease, opacity 0.1s ease 0.06s',
        }}>
          <div style={{ height: 1, background: 'linear-gradient(90deg, transparent, #e5e5ea, transparent)' }} />
          <div style={{ padding: '4px 6px 6px' }}>
            {tools.map(tool => (
              <button
                key={tool.id}
                type="button"
                onClick={tool.action}
                style={{
                  display: 'flex', alignItems: 'center', gap: 6,
                  width: '100%', padding: '6px 8px',
                  border: 'none', background: 'none', borderRadius: 6,
                  cursor: 'pointer', fontSize: 12, color: '#333', whiteSpace: 'nowrap',
                  transform: hover ? 'translateY(0)' : 'translateY(-6px)',
                  opacity: hover ? 1 : 0,
                  transition: hover
                    ? 'transform 0.2s ease 0.25s, opacity 0.15s ease 0.25s'
                    : 'transform 0.12s ease, opacity 0.1s ease',
                }}
                onMouseEnter={(e) => {
                  e.currentTarget.style.background = '#f0f5ff'
                  e.currentTarget.style.color = '#1677ff'
                }}
                onMouseLeave={(e) => {
                  e.currentTarget.style.background = 'none'
                  e.currentTarget.style.color = '#333'
                }}
              >
                <span style={{
                  display: 'flex', alignItems: 'center', justifyContent: 'center',
                  width: 22, height: 22, borderRadius: 6,
                  background: '#f0f5ff', color: '#1677ff',
                }}>
                  {tool.icon}
                </span>
                <span>{tool.label}</span>
              </button>
            ))}
          </div>
        </div>
      </div>
    </div>
  )
}

export default DrawFloatingButton
