import { useMemo } from 'react';
import { Image } from 'antd';
import ReactMarkdown from 'react-markdown';
import remarkGfm from 'remark-gfm';
import remarkBreaks from 'remark-breaks';
import type { Components } from 'react-markdown';
import { DrawIoEmbed } from 'react-drawio';

interface MarkdownRendererProps {
  content: string;
  imageBaseUrl?: string;
}

function generateId(text: string): string {
  return text.toLowerCase().replace(/[^a-z0-9一-鿿]+/g, '-').replace(/(^-|-$)/g, '');
}

const idCount = new Map<string, number>();

function extractText(node: React.ReactNode): string {
  if (typeof node === 'string' || typeof node === 'number') return String(node);
  if (Array.isArray(node)) return node.map(extractText).join('');
  if (node && typeof node === 'object' && 'props' in node) {
    return extractText((node as any).props.children);
  }
  return '';
}

function withHeadingId(level: string) {
  return (props: any) => {
    const { children, node: _, ...rest } = props;
    const text = extractText(children);
    let id: string | undefined;
    if (text) {
      const baseId = generateId(text);
      const count = idCount.get(baseId) || 0;
      id = count > 0 ? `${baseId}-${count}` : baseId;
      idCount.set(baseId, count + 1);
    }
    const Tag = level as 'h1' | 'h2' | 'h3' | 'h4' | 'h5' | 'h6';
    return <Tag id={id} {...rest}>{children}</Tag>;
  };
}

function DrawioDiagram({ xml }: { xml: string }) {
  return (
    <div style={{ height: 400, border: '1px solid #e5e5ea', borderRadius: 8, overflow: 'hidden', margin: '16 0' }}>
      <DrawIoEmbed xml={xml} urlParameters={{ ui: 'min', spin: false, libraries: false, noSaveBtn: true, noExitBtn: true, modified: false } as any} />
    </div>
  );
}

function mkComponents(baseUrl: string): Components {
  const fixSrc = (src: string | undefined) =>
    src && src.startsWith('/') && baseUrl ? `${baseUrl.replace(/\/$/, '')}${src}` : src;
  return {
    h1: withHeadingId('h1'), h2: withHeadingId('h2'), h3: withHeadingId('h3'),
    h4: withHeadingId('h4'), h5: withHeadingId('h5'), h6: withHeadingId('h6'),
    img: ({ src, alt }) => (
      <div style={{ display: 'flex', justifyContent: 'center', margin: '12px 0' }}>
        <Image src={fixSrc(src)} alt={alt || ''} style={{ maxWidth: '100%', borderRadius: 12 }} />
      </div>
    ),
    a: ({ href, children, node: _, ...rest }) => {
      const external = href && (href.startsWith('http://') || href.startsWith('https://'));
      return (
        <a href={href} {...(external ? { target: '_blank', rel: 'nofollow noopener noreferrer' } : {})} {...rest}>
          {children}
        </a>
      );
    },
  };
}

const DRAWIO_RE = /```drawio\n?([\s\S]*?)```/g;

const MarkdownRenderer: React.FC<MarkdownRendererProps> = ({ content, imageBaseUrl = '' }) => {
  idCount.clear();

  // 提取 drawio 块
  const parts = useMemo(() => {
    const r: { t: 'md' | 'drawio'; c: string }[] = [];
    let last = 0, m: RegExpExecArray | null;
    DRAWIO_RE.lastIndex = 0;
    while ((m = DRAWIO_RE.exec(content))) {
      if (m.index > last) r.push({ t: 'md', c: content.slice(last, m.index) });
      r.push({ t: 'drawio', c: m[1].trim() });
      last = m.index + m[0].length;
    }
    if (last < content.length) r.push({ t: 'md', c: content.slice(last) });
    return r.some(p => p.t === 'drawio') ? r : null;
  }, [content]);

  // 没有 drawio 块 → 单次渲染（兼容原逻辑）
  if (!parts) {
    return (
      <div className="markdown-body">
        <ReactMarkdown remarkPlugins={[remarkGfm, remarkBreaks]} components={mkComponents(imageBaseUrl)}>
          {content}
        </ReactMarkdown>
      </div>
    );
  }

  return (
    <div className="markdown-body">
      {parts.map((p, i) =>
        p.t === 'drawio' ? (
          <DrawioDiagram key={`d${i}`} xml={p.c} />
        ) : (
          <ReactMarkdown key={`m${i}`} remarkPlugins={[remarkGfm, remarkBreaks]} components={mkComponents(imageBaseUrl)}>
            {p.c}
          </ReactMarkdown>
        )
      )}
    </div>
  );
};

export default MarkdownRenderer;
