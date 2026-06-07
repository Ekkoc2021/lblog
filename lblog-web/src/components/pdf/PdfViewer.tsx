import { useEffect, useRef, forwardRef, useImperativeHandle, useState, useCallback } from 'react';
import { Button, Space } from 'antd';
import { UploadOutlined, FolderOpenOutlined } from '@ant-design/icons';
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
  onUploadRequest?: () => void;
}

const PdfViewer = forwardRef<PdfViewerHandle, Props>(
  ({ file, onPageChange, onSaveComplete, onUploadRequest }, ref) => {
    const [localBlobUrl, setLocalBlobUrl] = useState<string | null>(null);
    const iframeRef = useRef<HTMLIFrameElement>(null);
    const fileInputRef = useRef<HTMLInputElement>(null);

    const needsFile = file.sourceType === 'LOCAL' || (!file.fileSize && !file.filePath);

    const url = localBlobUrl || (needsFile ? null : getPdfDownloadUrl(file.id));

    const handleLocalOpen = useCallback(() => {
      fileInputRef.current?.click();
    }, []);

    const handleFileSelected = useCallback((e: React.ChangeEvent<HTMLInputElement>) => {
      const selectedFile = e.target.files?.[0];
      if (selectedFile) {
        const blobUrl = URL.createObjectURL(selectedFile);
        setLocalBlobUrl(blobUrl);
      }
      e.target.value = '';
    }, []);

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
      const handler = (e: MessageEvent) => {
        if (!e.data?.type) return;

        // Restore annotations when bridge is ready
        if (e.data.type === 'bridge-hooked') {
          getPdfAnnotation(file.id, 0).then(res => {
            if (res.data) {
              try {
                const data = JSON.parse(res.data);
                if (Array.isArray(data) && data.length > 0) {
                  iframeRef.current?.contentWindow?.postMessage(
                    { type: 'restore-annotations', data }, '*'
                  );
                }
              } catch {}
            }
          }).catch(() => {});
        }

        // Track page changes and save progress
        if (e.data.type === 'pdf-page-change') {
          savePdfProgress(file.id, e.data.page, 0).catch(() => {});
          onPageChange?.(e.data.page);
        }

        // Receive serialized annotations from bridge and persist
        if (e.data.type === 'pdf-annotations') {
          savePdfAnnotation(file.id, 0, e.data.data)
            .then(() => onSaveComplete?.())
            .catch(() => {});
        }
      };
      window.addEventListener('message', handler);
      return () => window.removeEventListener('message', handler);
    }, [file.id]);

    // Cleanup blob URL on unmount
    useEffect(() => {
      return () => {
        if (localBlobUrl) URL.revokeObjectURL(localBlobUrl);
      };
    }, [localBlobUrl]);

    // When file changes, reset blob URL
    useEffect(() => {
      setLocalBlobUrl(null);
    }, [file.id]);

    if (needsFile && !localBlobUrl) {
      return (
        <>
          <input ref={fileInputRef} type="file" accept=".pdf" style={{ display: 'none' }}
            onChange={handleFileSelected} />
          <div style={{
            display: 'flex', flexDirection: 'column', alignItems: 'center',
            justifyContent: 'center', height: '100%', gap: 16
          }}>
            <div style={{ color: 'var(--color-text-secondary)', fontSize: 14, marginBottom: 8 }}>
              该书籍尚无文件
            </div>
            <Space>
              <Button type="primary" icon={<UploadOutlined />} onClick={onUploadRequest}>
                上传文件
              </Button>
              <Button icon={<FolderOpenOutlined />} onClick={handleLocalOpen}>
                打开本地文件
              </Button>
            </Space>
          </div>
        </>
      );
    }

    if (!url) return null;

    return (
      <iframe ref={iframeRef}
        src={`${import.meta.env.BASE_URL}pdfjs/web/viewer.html?v=12&file=${encodeURIComponent(url)}&disableAutoFetch=true`}
        style={{ width: '100%', height: '100%', border: 'none' }}
        title="PDF Viewer" />
    );
  });

export default PdfViewer;
