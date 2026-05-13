import { MessageSquarePlus } from 'lucide-react'

interface DrawFloatingButtonProps {
  onClick: () => void
  active?: boolean
}

const DrawFloatingButton: React.FC<DrawFloatingButtonProps> = ({ onClick, active }) => {
  if (active) return null

  return (
    <button
      type="button"
      onClick={onClick}
      style={{
        position: 'fixed',
        right: 24,
        bottom: 24,
        width: 56,
        height: 56,
        borderRadius: 16,
        background: 'linear-gradient(135deg, #1677ff, #0958d9)',
        border: 'none',
        color: '#fff',
        cursor: 'pointer',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        boxShadow: '0 4px 20px rgba(22,119,255,0.35)',
        zIndex: 1000,
        transition: 'transform 0.2s, box-shadow 0.2s',
      }}
      title="AI 绘图助手"
    >
      <MessageSquarePlus size={24} />
    </button>
  )
}

export default DrawFloatingButton
