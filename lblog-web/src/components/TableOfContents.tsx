import { useState, useEffect, useRef } from 'react';
import { Card, Tooltip } from 'antd';

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
  const [top, setTop] = useState(120);
  const observerRef = useRef<IntersectionObserver | null>(null);

  useEffect(() => {
    const el = document.getElementById('post-content');
    if (el) {
      setTop(el.getBoundingClientRect().top + 32);
    }
  }, [items]);

  useEffect(() => {
    // 清理旧 observer
    if (observerRef.current) observerRef.current.disconnect();

    // 监听所有带 id 的标题元素
    const headings = items
      .map(item => document.getElementById(item.id))
      .filter(Boolean) as HTMLElement[];

    if (headings.length === 0) return;

    observerRef.current = new IntersectionObserver(
      entries => {
        // 找出当前可见的标题中层级最高的
        const visible = entries.filter(e => e.isIntersecting);
        if (visible.length > 0) {
          // 取最靠近顶部的可见标题
          visible.sort((a, b) => a.boundingClientRect.top - b.boundingClientRect.top);
          setActiveId(visible[0].target.id);
        }
      },
      { rootMargin: '-80px 0px -60% 0px' }
    );

    headings.forEach(h => observerRef.current?.observe(h));

    return () => observerRef.current?.disconnect();
  }, [items]);

  if (items.length === 0) return null;

  return (
    <div style={{
      position: 'fixed',
      top,
      right: 'calc(50% + 620px)',
      width: 190,
      maxHeight: 'calc(100vh - 300px)',
      overflowY: 'auto',
      display: 'none',
    }}
      className="toc-desktop"
    >
      <Card
        size="small"
        title="目录"
        styles={{ header: { padding: '8px 12px', fontWeight: 600, fontSize: 14 }, body: { padding: 0 } }}
      >
        <ul style={{ listStyle: 'none', margin: 0, padding: '4px 0' }}>
          {items.map(item => (
            <li key={item.id} style={{
              paddingLeft: item.level >= 3 ? 28 : item.level === 2 ? 16 : 12,
              paddingRight: 12,
              paddingTop: 3,
              paddingBottom: 3,
              fontSize: 13,
              lineHeight: 1.6,
              borderLeft: activeId === item.id ? '2px solid #1e80ff' : '2px solid transparent',
              background: activeId === item.id ? '#f0f5ff' : 'transparent',
              transition: 'all 0.15s',
            }}>
              <Tooltip title={item.text} mouseEnterDelay={0.5}>
                <a
                  href={`#${item.id}`}
                  onClick={e => {
                    e.preventDefault();
                    const el = document.getElementById(item.id);
                    if (el) {
                      const top = el.getBoundingClientRect().top + window.scrollY - 80;
                      window.scrollTo({ top, behavior: 'smooth' });
                    }
                  }}
                  style={{
                    color: activeId === item.id ? '#1e80ff' : '#666',
                    fontWeight: activeId === item.id ? 500 : 400,
                    textDecoration: 'none',
                    display: 'block',
                    overflow: 'hidden',
                    textOverflow: 'ellipsis',
                    whiteSpace: 'nowrap',
                  }}
                >
                  {item.text}
                </a>
              </Tooltip>
            </li>
          ))}
        </ul>
      </Card>
    </div>
  );
};

export default TableOfContents;
