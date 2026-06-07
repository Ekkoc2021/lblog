import { useState } from 'react';
import { Modal, Input, Select, Button, Space, Upload, message } from 'antd';
import { InboxOutlined } from '@ant-design/icons';
import { createPdfMetadata, getPdfFolders, uploadPdfToExisting } from '../../services/api';
import type { PdfFolder, PdfFile } from '../../types';
import { useEffect } from 'react';

const { Dragger } = Upload;

interface Props {
  open: boolean;
  onClose: () => void;
  onCreated: (file: PdfFile, action: 'upload' | 'local') => void;
}

const NewBookModal: React.FC<Props> = ({ open, onClose, onCreated }) => {
  const [bookName, setBookName] = useState('');
  const [selectedFolder, setSelectedFolder] = useState<number | null>(null);
  const [folders, setFolders] = useState<PdfFolder[]>([]);
  const [step, setStep] = useState<'create' | 'choose' | 'upload'>('create');
  const [createdFile, setCreatedFile] = useState<PdfFile | null>(null);
  const [uploading, setUploading] = useState(false);

  useEffect(() => {
    if (open) {
      getPdfFolders().then(res => setFolders(res.data)).catch(() => {});
      setStep('create');
      setBookName('');
      setSelectedFolder(null);
      setCreatedFile(null);
    }
  }, [open]);

  const handleCreate = async () => {
    if (!bookName.trim()) { message.warning('请输入书名'); return; }
    try {
      const res = await createPdfMetadata(bookName.trim(), selectedFolder);
      setCreatedFile(res.data);
      setStep('choose');
    } catch (e: any) {
      message.error(e.message || '创建失败');
    }
  };

  const handleUpload = async (file: File) => {
    if (!createdFile) return false;
    if (file.type !== 'application/pdf') {
      message.error('仅支持 PDF 格式');
      return false;
    }
    setUploading(true);
    try {
      await uploadPdfToExisting(createdFile.id, file);
      message.success('上传成功');
      onCreated(createdFile, 'upload');
    } catch (e: any) {
      message.error(e.message || '上传失败');
    } finally {
      setUploading(false);
    }
    return false;
  };

  return (
    <Modal
      title={step === 'create' ? '新建书籍' : step === 'choose' ? '选择来源' : '上传 PDF'}
      open={open}
      onCancel={onClose}
      footer={null}
      width={480}
    >
      {step === 'create' && (
        <>
          <div style={{ marginBottom: 16 }}>
            <div style={{ marginBottom: 8, fontWeight: 500 }}>书名</div>
            <Input
              placeholder="输入书名"
              value={bookName}
              onChange={e => setBookName(e.target.value)}
              onPressEnter={handleCreate}
            />
          </div>
          <div style={{ marginBottom: 16 }}>
            <div style={{ marginBottom: 8, fontWeight: 500 }}>文件夹</div>
            <Select
              style={{ width: '100%' }}
              placeholder="选择文件夹（可选）"
              value={selectedFolder}
              onChange={setSelectedFolder}
              allowClear
              options={folders.map(f => ({ value: f.id, label: f.name }))}
            />
          </div>
          <div style={{ textAlign: 'right' }}>
            <Button onClick={onClose} style={{ marginRight: 8 }}>取消</Button>
            <Button type="primary" onClick={handleCreate}>创建</Button>
          </div>
        </>
      )}

      {step === 'choose' && (
        <div style={{ textAlign: 'center', padding: '24px 0' }}>
          <p style={{ marginBottom: 24, fontSize: 14, color: 'var(--color-text-secondary)' }}>
            书籍 "{createdFile?.originalName}" 已创建，请选择文件来源
          </p>
          <Space direction="vertical" size={12} style={{ width: '100%' }}>
            <Button type="primary" block onClick={() => setStep('upload')}>
              上传 PDF 文件到服务器
            </Button>
            <Button block onClick={() => { onCreated(createdFile!, 'local'); }}>
              从本地打开（不上传）
            </Button>
          </Space>
        </div>
      )}

      {step === 'upload' && (
        <>
          <Dragger accept=".pdf" maxCount={1} showUploadList={false}
            beforeUpload={handleUpload} disabled={uploading}>
            <p className="ant-upload-drag-icon"><InboxOutlined /></p>
            <p className="ant-upload-text">点击或拖拽 PDF 文件到此处</p>
          </Dragger>
          <div style={{ textAlign: 'right', marginTop: 16 }}>
            <Button onClick={() => setStep('choose')}>返回</Button>
          </div>
        </>
      )}
    </Modal>
  );
};

export default NewBookModal;
