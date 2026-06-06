import { useState } from 'react';
import { Modal, Upload, Select, message, Progress, Typography } from 'antd';
import { InboxOutlined } from '@ant-design/icons';
import { uploadPdf, getPdfFolders } from '../../services/api';
import type { PdfFolder } from '../../types';
import { useEffect } from 'react';

const { Dragger } = Upload;
const { Text } = Typography;

interface Props {
  open: boolean;
  onClose: () => void;
  onSuccess: () => void;
}

const PdfUploadModal: React.FC<Props> = ({ open, onClose, onSuccess }) => {
  const [folders, setFolders] = useState<PdfFolder[]>([]);
  const [selectedFolder, setSelectedFolder] = useState<number | null>(null);
  const [uploading, setUploading] = useState(false);
  const [progress, setProgress] = useState(0);

  useEffect(() => {
    if (open) {
      getPdfFolders().then(res => setFolders(res.data)).catch(() => {});
    }
  }, [open]);

  const handleUpload = async (file: File) => {
    if (file.type !== 'application/pdf') {
      message.error('仅支持 PDF 格式');
      return false;
    }
    if (!selectedFolder) {
      message.warning('请先选择目标文件夹');
      return false;
    }
    setUploading(true);
    setProgress(0);
    try {
      await uploadPdf(file, selectedFolder);
      setProgress(100);
      message.success('上传成功');
      onSuccess();
    } catch (e: any) {
      message.error(e.message || '上传失败');
    } finally {
      setUploading(false);
      setProgress(0);
    }
    return false; // prevent default Upload behavior
  };

  return (
    <Modal title="上传 PDF" open={open} onCancel={onClose} footer={null} width={480}>
      <Dragger accept=".pdf" maxCount={1} showUploadList={false}
        beforeUpload={handleUpload} disabled={uploading}
      >
        <p className="ant-upload-drag-icon"><InboxOutlined /></p>
        <p className="ant-upload-text">点击或拖拽 PDF 文件到此处</p>
        <p className="ant-upload-hint">支持最大 500MB 的 PDF 文件</p>
      </Dragger>
      {uploading && <Progress percent={progress} status="active" style={{ marginTop: 16 }} />}
      <div style={{ marginTop: 16 }}>
        <Text type="secondary">目标文件夹：</Text>
        <Select style={{ width: '100%', marginTop: 8 }} placeholder="请选择文件夹"
          value={selectedFolder} onChange={setSelectedFolder}
          options={folders.map(f => ({ value: f.id, label: f.name }))}
        />
        {folders.length === 0 && (
          <div style={{ color: '#faad14', fontSize: 12, marginTop: 4 }}>
            请先在书架中创建文件夹
          </div>
        )}
      </div>
    </Modal>
  );
};

export default PdfUploadModal;
