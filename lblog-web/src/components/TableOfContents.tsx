import { useState, useEffect } from 'react';
import { Card } from 'antd';

export interface TocItem {
  id: string;
  text: string;
  level: number;
}

interface TableOfContentsProps {
  items: TocItem[];
}

export function parseHeadings(markdown: string): TocItem[] {
  const regex = /^(#{1,6})\s+(.+)$/gm;
  const items: TocItem[] = [];
  const idCount = new Map<string, number>();
  let match;
  while ((match = regex.exec(markdown)) !== null) {
    const level = match[1].length;
    const text = match[2].trim();
    const baseId = text
      .toLowerCase()
      .replace(/[^a-z0-9一-鿿]+/g, '-')
      .replace(/(^-|-$)/g, '');
    const count = idCount.get(baseId) || 0;
    const id = count > 0 ? `${baseId}-${count}` : baseId;
    idCount.set(baseId, count + 1);
    items.push({ id, text, level });
  }
  return items;
}

const TableOfContents: React.FC<TableOfContentsProps> = ({ items }) => {
  const [activeId, setActiveId] = useState('');

  useEffect(() => {
    const handleScroll = () => {
      const headings = items
        .map(item => document.getElementById(item.id))
        .filter(Boolean) as HTMLElement[];
      let current = '';
      for (const el of headings) {
        if (el.getBoundingClientRect().top <= 100) {
          current = el.id;
        }
      }
      setActiveId(current);
    };

    handleScroll();
    window.addEventListener('scroll', handleScroll, { passive: true });
    return () => window.removeEventListener('scroll', handleScroll);
  }, [items]);

  if (items.length === 0) return null;

  return (
    <Card
      size="small"
      title="目录"
      styles={{
        header: { padding: '8px 12px', fontWeight: 600, fontSize: 14 },
        body: { padding: 0, maxHeight: 'calc(100vh - 160px)', overflowY: 'auto' },
      }}
    >
      <ul style={{ listStyle: 'none', margin: 0, padding: '4px 0' }}>
        {items.map(item => (
          <li
            key={item.id}
            onClick={() => {
              const el = document.getElementById(item.id);
              if (el) {
                const top = el.getBoundingClientRect().top + window.scrollY - 80;
                window.scrollTo({ top, behavior: 'smooth' });
              }
            }}
            style={{
              paddingLeft: item.level >= 3 ? 28 : item.level === 2 ? 16 : 12,
              paddingRight: 12,
              paddingTop: 3,
              paddingBottom: 3,
              fontSize: 13,
              lineHeight: 1.6,
              borderLeft: activeId === item.id ? '2px solid var(--color-primary)' : '2px solid transparent',
              background: activeId === item.id ? 'var(--color-primary-bg)' : 'transparent',
              transition: 'all 0.15s',
              cursor: 'pointer',
            }}
          >
            <span style={{
              color: activeId === item.id ? 'var(--color-primary)' : 'var(--color-text-secondary)',
              fontWeight: activeId === item.id ? 500 : 400,
              display: 'block',
              overflow: 'hidden',
              textOverflow: 'ellipsis',
              whiteSpace: 'nowrap',
            }}>
              {item.text}
            </span>
          </li>
        ))}
      </ul>
    </Card>
  );
};

export default TableOfContents;
