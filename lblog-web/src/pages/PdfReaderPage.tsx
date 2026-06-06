import { useState, useCallback, useRef } from 'react';
import { Layout, message } from 'antd';
import { ArrowLeftOutlined } from '@ant-design/icons';
import { useNavigate } from 'react-router-dom';
import type { PdfFile } from '../types';
import PdfSidebar from '../components/pdf/PdfSidebar';
import PdfViewer, { type PdfViewerHandle } from '../components/pdf/PdfViewer';
import PdfUploadModal from '../components/pdf/PdfUploadModal';

const { Sider, Content } = Layout;

const PdfReaderPage: React.FC = () => {
  const navigate = useNavigate();
  const viewerRef = useRef<PdfViewerHandle>(null);
  const [sidebarCollapsed, setSidebarCollapsed] = useState(false);
  const [selectedFile, setSelectedFile] = useState<PdfFile | null>(null);
  const [uploadVisible, setUploadVisible] = useState(false);
  const [refreshKey, setRefreshKey] = useState(0);
  const [currentPage, setCurrentPage] = useState(1);

  const handleSelectFile = useCallback((file: PdfFile) => {
    setSelectedFile(file);
  }, []);

  const handleUploadSuccess = useCallback(() => {
    setUploadVisible(false);
    setRefreshKey(k => k + 1);
  }, []);

  const handleSave = useCallback(() => {
    viewerRef.current?.save();
  }, []);

  const handleSaveComplete = useCallback(() => {
    message.success('标注已保存');
  }, []);

  const handleJumpToPage = useCallback((page: number) => {
    viewerRef.current?.jumpToPage(page);
  }, []);

  return (
    <Layout style={{ height: '100vh', background: 'var(--color-bg)' }}>
      {/* Top Bar */}
      <div style={{
        height: 48, display: 'flex', alignItems: 'center', padding: '0 16px',
        borderBottom: '1px solid var(--color-border, #e8e8e8)',
        background: 'var(--color-bg-elevated)', gap: 12, flexShrink: 0
      }}>
        <ArrowLeftOutlined style={{ cursor: 'pointer', fontSize: 16, color: 'var(--color-text-secondary)' }}
          onClick={() => navigate('/')} />
        <span style={{ fontWeight: 600, color: 'var(--color-text)' }}>PDF 阅读</span>
        {selectedFile && (
          <span style={{ color: 'var(--color-text-secondary)', fontSize: 13 }}>
            {selectedFile.originalName}
          </span>
        )}
        <span style={{ flex: 1 }} />
      </div>

      <Layout style={{ flex: 1, overflow: 'hidden', background: 'var(--color-bg)', position: 'relative' }}>
        {sidebarCollapsed && (
          <div onClick={() => setSidebarCollapsed(false)}
            style={{
              position: 'absolute', left: 0, top: 0, bottom: 0, width: 5, zIndex: 99,
              cursor: 'pointer',
              background: 'var(--color-border, #d9d9d9)',
              borderRight: '1px solid var(--color-border, #d9d9d9)',
            }}
            title="展开侧边栏"
          />
        )}
        <Sider width={300} collapsedWidth={0} collapsed={sidebarCollapsed}
          style={{ background: 'var(--color-bg-elevated)', borderRight: '1px solid var(--color-border, #e8e8e8)', overflow: 'auto' }}>
          <PdfSidebar
            selectedFile={selectedFile}
            onSelectFile={handleSelectFile}
            onUploadClick={() => setUploadVisible(true)}
            onSaveAnnotations={handleSave}
            currentPage={currentPage}
            onJumpToPage={handleJumpToPage}
            onCollapse={() => setSidebarCollapsed(v => !v)}
            refreshKey={refreshKey}
          />
        </Sider>

        <Content style={{ position: 'relative', overflow: 'hidden' }}>
          {selectedFile ? (
            <PdfViewer
              ref={viewerRef}
              file={selectedFile}
              onPageChange={setCurrentPage}
              onSaveComplete={handleSaveComplete}
            />
          ) : (
            <div style={{
              display: 'flex', alignItems: 'center', justifyContent: 'center', height: '100%',
              color: 'var(--color-text-tertiary)', fontSize: 16
            }}>
              从左侧书架选择 PDF 开始阅读
            </div>
          )}
        </Content>
      </Layout>

      <PdfUploadModal
        open={uploadVisible}
        onClose={() => setUploadVisible(false)}
        onSuccess={handleUploadSuccess}
      />
    </Layout>
  );
};

export default PdfReaderPage;
