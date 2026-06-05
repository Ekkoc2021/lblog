import { Button, List, theme } from 'antd';
import type { JournalEntry } from '../../types';

interface Props {
  entries: JournalEntry[];
  loading: boolean;
  hasMore: boolean;
  onLoadMore: () => void;
  onEntryClick: (entry: JournalEntry) => void;
  onAddClick: () => void;
}

const TimelineView: React.FC<Props> = ({ entries, loading, hasMore, onLoadMore, onEntryClick, onAddClick }) => {
  const { token } = theme.useToken();

  return (
    <div>
      <div style={{ display: 'flex', justifyContent: 'flex-end', marginBottom: 8 }}>
        <Button type="primary" size="small" onClick={onAddClick}>写日记</Button>
      </div>
      <List loading={loading} dataSource={entries} locale={{ emptyText: '还没有日记，去日历视图写一篇吧' }}
        renderItem={item => (
          <List.Item style={{ cursor: 'pointer', padding: '12px 0' }} onClick={() => onEntryClick(item)}>
            <List.Item.Meta
              avatar={<span style={{ fontSize: 28, lineHeight: '32px' }}>{item.moodEmoji || '📝'}</span>}
              title={
                <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                  <span style={{ fontSize: 12, color: token.colorTextTertiary }}>{item.journalDate}</span>
                  <span style={{ fontSize: 12, color: token.colorTextTertiary }}>
                    {item.weather && `${item.weather} `}{item.mood && item.mood}
                  </span>
                </div>
              }
              description={
                <div style={{ fontSize: 13, color: token.colorTextSecondary, lineHeight: 1.5 }}>
                  {(() => { const text = item.content || ''; const truncated = Array.from(text).slice(0, 100).join(''); return truncated + (text.length > 100 ? '...' : ''); })() || '（无内容）'}
                </div>
              }
            />
          </List.Item>
        )}
      />
      {hasMore && (
        <div style={{ textAlign: 'center', marginTop: 8 }}>
          <Button loading={loading} onClick={onLoadMore}>加载更多</Button>
        </div>
      )}
    </div>
  );
};

export default TimelineView;
