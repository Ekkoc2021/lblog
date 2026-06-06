import { Button, Space, ColorPicker, Select } from 'antd';
import {
  DragOutlined, EditOutlined, HighlightOutlined, MessageOutlined,
  UnderlineOutlined, UndoOutlined, FontSizeOutlined
} from '@ant-design/icons';

export type ToolType = 'pan' | 'pen' | 'highlight' | 'note' | 'underline' | 'text';

interface Props {
  activeTool: ToolType;
  onToolChange: (tool: ToolType) => void;
  color: string;
  onColorChange: (color: string) => void;
  strokeWidth: number;
  onStrokeWidthChange: (w: number) => void;
  onUndo: () => void;
}

const PdfToolbar: React.FC<Props> = ({ activeTool, onToolChange, color, onColorChange,
  strokeWidth, onStrokeWidthChange, onUndo }) => {

  const tools: { key: ToolType; icon: React.ReactNode; label: string }[] = [
    { key: 'pan', icon: <DragOutlined />, label: '拖动' },
    { key: 'pen', icon: <EditOutlined />, label: '画笔' },
    { key: 'highlight', icon: <HighlightOutlined />, label: '高亮' },
    { key: 'note', icon: <MessageOutlined />, label: '便签' },
    { key: 'underline', icon: <UnderlineOutlined />, label: '下划线' },
    { key: 'text', icon: <FontSizeOutlined />, label: '文字' },
  ];

  return (
    <div style={{ display: 'flex', alignItems: 'center', padding: '6px 12px', gap: 8,
      borderBottom: '1px solid var(--color-border, #e8e8e8)', background: 'var(--color-bg-elevated)' }}>
      <Space size={4}>
        {tools.map(t => (
          <Button key={t.key} type={activeTool === t.key ? 'primary' : 'text'} size="small"
            icon={t.icon} onClick={() => onToolChange(t.key)} title={t.label} />
        ))}
      </Space>
      <div style={{ width: 1, height: 20, background: 'var(--color-border, #e8e8e8)' }} />
      <ColorPicker value={color} onChange={(c) => onColorChange(c.toHexString())} size="small" />
      <Select size="small" value={strokeWidth} onChange={onStrokeWidthChange} style={{ width: 70 }}
        options={[{ value: 1, label: '细' }, { value: 3, label: '中' }, { value: 5, label: '粗' }]} />
      <Button type="text" size="small" icon={<UndoOutlined />} onClick={onUndo} title="撤销" />
    </div>
  );
};

export default PdfToolbar;
