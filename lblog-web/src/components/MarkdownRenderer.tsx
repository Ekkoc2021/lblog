import ReactMarkdown from 'react-markdown';
import remarkGfm from 'remark-gfm';
import remarkBreaks from 'remark-breaks';
import rehypeHighlight from 'rehype-highlight';
import type { Components } from 'react-markdown';

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

function withImageBaseUrl(imageBaseUrl: string): Components {
  return {
    h1: withHeadingId('h1'),
    h2: withHeadingId('h2'),
    h3: withHeadingId('h3'),
    h4: withHeadingId('h4'),
    h5: withHeadingId('h5'),
    h6: withHeadingId('h6'),
    img: ({ src, alt, ...rest }) => {
      const finalSrc = src && imageBaseUrl && src.startsWith('/')
        ? `${imageBaseUrl.replace(/\/$/, '')}${src}`
        : src;
      return <img src={finalSrc} alt={alt || ''} {...rest} />;
    },
  };
}

const MarkdownRenderer: React.FC<MarkdownRendererProps> = ({ content, imageBaseUrl = '' }) => {
  idCount.clear();
  return (
    <div className="markdown-body">
      <ReactMarkdown
        remarkPlugins={[remarkGfm, remarkBreaks]}
        rehypePlugins={[rehypeHighlight]}
        components={withImageBaseUrl(imageBaseUrl)}
      >
        {content}
      </ReactMarkdown>
    </div>
  );
};

export default MarkdownRenderer;
