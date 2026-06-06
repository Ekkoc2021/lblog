import { useEffect, useRef, forwardRef, useImperativeHandle } from 'react';
import type { PdfFile } from '../../types';
import {
  getPdfDownloadUrl, getPdfProgress, savePdfProgress,
  savePdfAnnotation, getPdfAnnotation,
} from '../../services/api';

export interface PdfViewerHandle {
  save: () => void;
  jumpToPage: (page: number) => void;
}

interface Props {
  file: PdfFile;
  onPageChange?: (page: number) => void;
  onSaveComplete?: () => void;
}

const PdfViewer = forwardRef<PdfViewerHandle, Props>(({ file, onPageChange, onSaveComplete }, ref) => {
  const url = getPdfDownloadUrl(file.id);
  const iframeRef = useRef<HTMLIFrameElement>(null);

  useImperativeHandle(ref, () => ({
    save: () => {
      iframeRef.current?.contentWindow?.postMessage(
        { type: 'save-annotations' }, '*'
      );
    },
    jumpToPage: (page: number) => {
      iframeRef.current?.contentWindow?.postMessage(
        { type: 'jump-to-page', page }, '*'
      );
    },
  }));

  useEffect(() => {
    getPdfProgress(file.id).then(res => {
      if (res.data?.pageNum > 0) {
        sessionStorage.setItem(`pdf-progress-${file.id}`, String(res.data.pageNum));
      }
    }).catch(() => {});
  }, [file.id]);

  useEffect(() => {
    const iframe = iframeRef.current;
    const handler = (e: MessageEvent) => {
      if (!e.data?.type) return;

      if (e.data.type === 'bridge-hooked') {
        getPdfAnnotation(file.id, 0).then(res => {
          if (res.data) {
            try {
              const data = JSON.parse(res.data);
              if (Array.isArray(data) && data.length > 0) {
                iframe?.contentWindow?.postMessage(
                  { type: 'restore-annotations', data }, '*'
                );
              }
            } catch { /* parse error */ }
          }
        }).catch(() => {});
      }

      if (e.data.type === 'pdf-page-change') {
        savePdfProgress(file.id, e.data.page, 0).catch(() => {});
        onPageChange?.(e.data.page);
      }

      if (e.data.type === 'pdf-annotations') {
        savePdfAnnotation(file.id, 0, e.data.data)
          .then(() => onSaveComplete?.())
          .catch(() => {});
      }
    };
    window.addEventListener('message', handler);
    return () => window.removeEventListener('message', handler);
  }, [file.id]);

  return (
    <iframe ref={iframeRef}
      src={`/pdfjs/web/viewer.html?file=${encodeURIComponent(url)}&disableAutoFetch=true`}
      style={{ width: '100%', height: '100%', border: 'none' }}
      title="PDF Viewer" />
  );
});

export default PdfViewer;
