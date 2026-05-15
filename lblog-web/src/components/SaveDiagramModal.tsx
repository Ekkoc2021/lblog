import { useState, useEffect, useCallback } from 'react'
import { Modal, Input, Form, Select, message } from 'antd'

interface SaveDiagramModalProps {
    open: boolean
    onClose: () => void
    onSave: (title: string, description?: string, tags?: string) => Promise<void>
    initialTitle?: string
    mode: 'save' | 'saveAs'
}

export default function SaveDiagramModal({
    open,
    onClose,
    onSave,
    initialTitle = '无标题图表',
    mode,
}: SaveDiagramModalProps) {
    const [title, setTitle] = useState(initialTitle)
    const [description, setDescription] = useState('')
    const [tags, setTags] = useState<string[]>([])
    const [submitting, setSubmitting] = useState(false)

    useEffect(() => {
        if (open) {
            setTitle(initialTitle)
            setDescription('')
            setTags([])
        }
    }, [open, initialTitle])

    const handleSave = useCallback(async () => {
        const t = title.trim()
        if (!t) {
            message.warning('请输入图表名称')
            return
        }
        setSubmitting(true)
        try {
            const tagsStr = tags.length > 0 ? JSON.stringify(tags) : undefined
            await onSave(t, description.trim() || undefined, tagsStr)
            onClose()
        } catch (e: any) {
            message.error(e.message || '保存失败')
        } finally {
            setSubmitting(false)
        }
    }, [title, description, tags, onSave, onClose])

    return (
        <Modal
            title={mode === 'saveAs' ? '另存为' : '保存图表'}
            open={open}
            onCancel={onClose}
            onOk={handleSave}
            confirmLoading={submitting}
            okText="保存"
            cancelText="取消"
            destroyOnClose
            width={480}
        >
            <Form layout="vertical" style={{ marginTop: 16 }}>
                <Form.Item label="图表名称" required>
                    <Input
                        value={title}
                        onChange={e => setTitle(e.target.value)}
                        placeholder="输入图表名称"
                        maxLength={200}
                        autoFocus
                    />
                </Form.Item>
                <Form.Item label="描述（可选）">
                    <Input.TextArea
                        value={description}
                        onChange={e => setDescription(e.target.value)}
                        placeholder="简要描述图表内容"
                        maxLength={500}
                        rows={2}
                    />
                </Form.Item>
                <Form.Item label="标签（可选）">
                    <Select
                        mode="tags"
                        value={tags}
                        onChange={setTags}
                        placeholder="输入标签后回车"
                        style={{ width: '100%' }}
                        tokenSeparators={[',', '，']}
                    />
                </Form.Item>
            </Form>
        </Modal>
    )
}
