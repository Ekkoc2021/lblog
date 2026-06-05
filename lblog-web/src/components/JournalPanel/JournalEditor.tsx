import { useState, useEffect } from 'react';
import { Modal, Form, Input, Select, theme } from 'antd';
import { MOOD_OPTIONS, WEATHER_OPTIONS } from '../../types';
import type { JournalEntry, CreateJournalRequest } from '../../types';

interface Props {
  open: boolean;
  date: string;
  entry: JournalEntry | null;
  onClose: () => void;
  onSave: (data: CreateJournalRequest) => Promise<void>;
}

const JournalEditor: React.FC<Props> = ({ open, date, entry, onClose, onSave }) => {
  const [form] = Form.useForm();
  const [submitting, setSubmitting] = useState(false);
  const { token } = theme.useToken();
  const isEdit = !!entry;

  useEffect(() => {
    if (open) {
      form.setFieldsValue({
        title: entry?.title || '',
        content: entry?.content || '',
        mood: entry?.mood || undefined,
        weather: entry?.weather || undefined,
      });
    }
  }, [open, entry, form]);

  const handleSave = async () => {
    try {
      const values = await form.validateFields();
      setSubmitting(true);
      const moodOption = MOOD_OPTIONS.find(m => m.label === values.mood);
      await onSave({
        journalDate: date,
        title: values.title || '',
        content: values.content || '',
        mood: values.mood || '',
        moodEmoji: moodOption?.emoji || '',
        weather: values.weather || '',
      });
      form.resetFields();
    } catch { /* validation error; API errors handled by parent */ }
    finally { setSubmitting(false); }
  };

  const moodSelectOptions = MOOD_OPTIONS.map(m => ({
    value: m.label,
    label: `${m.emoji} ${m.label}`,
  }));

  const weatherSelectOptions = WEATHER_OPTIONS.map(w => ({
    value: w.label,
    label: `${w.emoji} ${w.label}`,
  }));

  return (
    <Modal
      title={`${isEdit ? '编辑' : '写'}日记 — ${date}`}
      open={open}
      onOk={handleSave}
      onCancel={onClose}
      confirmLoading={submitting}
      okText="保存"
      cancelText="取消"
      destroyOnClose
      width={640}
      styles={{ body: { padding: '16px 24px' } }}
    >
      <Form form={form} layout="vertical">
        <Form.Item name="title" label="标题" style={{ marginBottom: 8 }}>
          <Input placeholder="给这一天起个标题（可选）" maxLength={200} variant="borderless" style={{ fontSize: 20, fontWeight: 600, padding: '4px 0' }} />
        </Form.Item>

        <div style={{ display: 'flex', gap: 12, marginBottom: 16 }}>
          <Form.Item name="mood" label="心情" style={{ marginBottom: 0, flex: 1 }}>
            <Select placeholder="选择心情" options={moodSelectOptions} allowClear size="small" />
          </Form.Item>
          <Form.Item name="weather" label="天气" style={{ marginBottom: 0, flex: 1 }}>
            <Select placeholder="选择天气" options={weatherSelectOptions} allowClear size="small" />
          </Form.Item>
        </div>

        <Form.Item name="content" label="内容" style={{ marginBottom: 0 }}>
          <Input.TextArea
            placeholder="今天发生了什么..."
            rows={14}
            style={{
              fontFamily: '"Ma Shan Zheng", "STKaiti", "KaiTi", serif',
              fontSize: 18,
              lineHeight: '30px',
              resize: 'none',
              wordBreak: 'break-all',
              overflowWrap: 'break-word',
              backgroundImage: `repeating-linear-gradient(transparent, transparent 29px, ${token.colorBorderSecondary} 29px, ${token.colorBorderSecondary} 30px)`,
              backgroundPosition: '0 15px',
              backgroundAttachment: 'local',
              borderRadius: 8,
              border: 'none',
              padding: '15px 20px 15px 28px',
              boxShadow: `inset 1px 0 0 ${token.colorBorderSecondary}66, inset 0 1px 3px ${token.colorBorderSecondary}`,
            }}
          />
        </Form.Item>
      </Form>
    </Modal>
  );
};

export default JournalEditor;
