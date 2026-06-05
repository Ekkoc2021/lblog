import { Modal, Button, Space, Popconfirm, theme } from 'antd';
import { EditOutlined, DeleteOutlined } from '@ant-design/icons';
import type { JournalEntry } from '../../types';

interface Props {
  entry: JournalEntry;
  onClose: () => void;
  onEdit: () => void;
  onDelete: () => Promise<void>;
}

const JournalDetail: React.FC<Props> = ({ entry, onClose, onEdit, onDelete }) => {
  const { token } = theme.useToken();

  const hasMeta = !!(entry.moodEmoji || entry.mood || entry.weather);

  return (
    <Modal title={entry.title || `${entry.journalDate} 的日记`} open onCancel={onClose}
      footer={
        <Space>
          <Button icon={<EditOutlined />} onClick={onEdit}>编辑</Button>
          <Popconfirm title="确定删除这篇日记？" onConfirm={onDelete}>
            <Button danger icon={<DeleteOutlined />}>删除</Button>
          </Popconfirm>
        </Space>
      } destroyOnClose>
      <div style={{ marginTop: 8 }}>
        <div style={{ marginBottom: hasMeta ? 16 : 8, fontSize: 13, color: token.colorTextTertiary }}>
          {entry.journalDate}
          {entry.moodEmoji && <span style={{ marginLeft: 8 }}>{entry.moodEmoji}</span>}
          {entry.mood && <span style={{ marginLeft: 4 }}>{entry.mood}</span>}
          {entry.weather && <span style={{ marginLeft: 8 }}>{entry.weather}</span>}
        </div>
        <div style={{ fontFamily: '"Ma Shan Zheng", "STKaiti", "KaiTi", serif', fontSize: 18, lineHeight: 2, whiteSpace: 'pre-wrap', wordBreak: 'break-all', overflowWrap: 'break-word', maxHeight: '50vh', overflowY: 'auto', paddingRight: 4 }}>
          {entry.content || <span style={{ color: token.colorTextQuaternary }}>（无内容）</span>}
        </div>
      </div>
    </Modal>
  );
};

export default JournalDetail;
