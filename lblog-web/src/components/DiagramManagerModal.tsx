import { useState, useEffect, useCallback } from 'react'
import { Modal, Input, List, Button, Popconfirm, message, Typography, Empty, Space, Tag } from 'antd'
import { FolderOpenOutlined, SearchOutlined, DeleteOutlined, EditOutlined } from '@ant-design/icons'
import type { DiagramItem } from '../types/diagram'
import { getDiagramList, deleteDiagram, updateDiagramMeta } from '../services/diagramStorage'

interface DiagramManagerModalProps {
    open: boolean
    onClose: () => void
    onOpenDiagram: (id: number) => Promise<void>
}

const PAGE_SIZE = 12

export default function DiagramManagerModal({
    open,
    onClose,
    onOpenDiagram,
}: DiagramManagerModalProps) {
    const [list, setList] = useState<DiagramItem[]>([])
    const [total, setTotal] = useState(0)
    const [page, setPage] = useState(1)
    const [keyword, setKeyword] = useState('')
    const [loading, setLoading] = useState(false)
    const [openingId, setOpeningId] = useState<number | null>(null)

    // 重命名状态
    const [renamingId, setRenamingId] = useState<number | null>(null)
    const [renameTitle, setRenameTitle] = useState('')
    const [renameDesc, setRenameDesc] = useState('')
    const [renameTags, setRenameTags] = useState('')

    const fetchList = useCallback(async (p: number, kw: string) => {
        setLoading(true)
        try {
            const res = await getDiagramList({ page: p, pageSize: PAGE_SIZE, keyword: kw || undefined })
            setList(res.data.list)
            setTotal(res.data.total)
        } catch (e: any) {
            message.error(e.message || '加载列表失败')
        } finally {
            setLoading(false)
        }
    }, [])

    useEffect(() => {
        if (open) {
            setPage(1)
            setKeyword('')
            fetchList(1, '')
        }
    }, [open, fetchList])

    const handleSearch = (value: string) => {
        setKeyword(value)
        setPage(1)
        fetchList(1, value)
    }

    const handlePageChange = (p: number) => {
        setPage(p)
        fetchList(p, keyword)
    }

    const handleOpen = async (id: number) => {
        setOpeningId(id)
        try {
            await onOpenDiagram(id)
            onClose()
        } catch (e: any) {
            message.error(e.message || '加载图表失败')
        } finally {
            setOpeningId(null)
        }
    }

    const handleDelete = async (id: number, title: string) => {
        try {
            await deleteDiagram(id)
            message.success(`已删除「${title}」`)
            fetchList(page, keyword)
        } catch (e: any) {
            message.error(e.message || '删除失败')
        }
    }

    const startRename = (item: DiagramItem) => {
        setRenamingId(item.id)
        setRenameTitle(item.title)
        setRenameDesc(item.description ?? '')
        setRenameTags(item.tags ?? '')
    }

    const confirmRename = async () => {
        if (!renamingId) return
        const t = renameTitle.trim()
        if (!t) { message.warning('名称不能为空'); return }
        try {
            await updateDiagramMeta(renamingId, {
                title: t,
                description: renameDesc.trim() || undefined,
                tags: renameTags || undefined,
            })
            message.success('已更新')
            setRenamingId(null)
            fetchList(page, keyword)
        } catch (e: any) {
            message.error(e.message || '更新失败')
        }
    }

    return (
        <>
            <Modal
                title={
                    <span>
                        <FolderOpenOutlined style={{ marginRight: 8 }} />
                        图表库
                    </span>
                }
                open={open}
                onCancel={onClose}
                footer={null}
                width={720}
                destroyOnClose
            >
                <div style={{ marginBottom: 16 }}>
                    <Input.Search
                        placeholder="搜索图表名称、描述、标签..."
                        allowClear
                        prefix={<SearchOutlined />}
                        onSearch={handleSearch}
                        style={{ maxWidth: 400 }}
                    />
                    <Typography.Text type="secondary" style={{ marginLeft: 12, fontSize: 13 }}>
                        共 {total} 个图表
                    </Typography.Text>
                </div>

                <List
                    loading={loading}
                    dataSource={list}
                    locale={{ emptyText: <Empty description="还没有保存的图表" /> }}
                    renderItem={(item) => (
                        <List.Item
                            actions={[
                                <Button
                                    key="open"
                                    type="link"
                                    size="small"
                                    loading={openingId === item.id}
                                    onClick={() => handleOpen(item.id)}
                                >
                                    打开
                                </Button>,
                                <Button
                                    key="rename"
                                    type="link"
                                    size="small"
                                    icon={<EditOutlined />}
                                    onClick={() => startRename(item)}
                                >
                                    重命名
                                </Button>,
                                <Popconfirm
                                    key="delete"
                                    title="确定删除此图表？"
                                    description="删除后不可恢复"
                                    onConfirm={() => handleDelete(item.id, item.title)}
                                    okText="删除"
                                    cancelText="取消"
                                    okButtonProps={{ danger: true }}
                                >
                                    <Button type="link" size="small" danger icon={<DeleteOutlined />}>
                                        删除
                                    </Button>
                                </Popconfirm>,
                            ]}
                        >
                            <List.Item.Meta
                                title={
                                    <Typography.Text
                                        strong
                                        style={{ cursor: 'pointer' }}
                                        onClick={() => handleOpen(item.id)}
                                    >
                                        {item.title}
                                    </Typography.Text>
                                }
                                description={
                                    <div style={{ fontSize: 12, color: '#999', lineHeight: '22px' }}>
                                        {item.description && <div>{item.description}</div>}
                                        <Space size={12}>
                                            <span>{(item.fileSize / 1024).toFixed(1)} KB</span>
                                            <span>更新于 {new Date(item.updatedAt).toLocaleString('zh-CN')}</span>
                                        </Space>
                                        {item.tags && (
                                            <div style={{ marginTop: 4 }}>
                                                {JSON.parse(item.tags).map((t: string) => (
                                                    <Tag key={t} style={{ fontSize: 11 }}>{t}</Tag>
                                                ))}
                                            </div>
                                        )}
                                    </div>
                                }
                            />
                        </List.Item>
                    )}
                    pagination={
                        total > PAGE_SIZE ? {
                            current: page,
                            pageSize: PAGE_SIZE,
                            total,
                            onChange: handlePageChange,
                            showSizeChanger: false,
                            size: 'small',
                        } : false
                    }
                    style={{ minHeight: 300 }}
                />
            </Modal>

            {/* 重命名对话框 */}
            <Modal
                title="重命名图表"
                open={renamingId !== null}
                onCancel={() => setRenamingId(null)}
                onOk={confirmRename}
                okText="保存"
                cancelText="取消"
                destroyOnClose
                width={480}
            >
                <div style={{ marginTop: 16 }}>
                    <div style={{ marginBottom: 12 }}>
                        <div style={{ marginBottom: 4, fontSize: 13, color: '#666' }}>图表名称</div>
                        <Input
                            value={renameTitle}
                            onChange={e => setRenameTitle(e.target.value)}
                            placeholder="输入图表名称"
                            maxLength={200}
                            autoFocus
                        />
                    </div>
                    <div style={{ marginBottom: 12 }}>
                        <div style={{ marginBottom: 4, fontSize: 13, color: '#666' }}>描述</div>
                        <Input.TextArea
                            value={renameDesc}
                            onChange={e => setRenameDesc(e.target.value)}
                            placeholder="图表描述"
                            maxLength={500}
                            rows={2}
                        />
                    </div>
                    <div>
                        <div style={{ marginBottom: 4, fontSize: 13, color: '#666' }}>标签</div>
                        <Input
                            value={renameTags}
                            onChange={e => setRenameTags(e.target.value)}
                            placeholder='JSON 数组，如 ["架构","微服务"]'
                        />
                    </div>
                </div>
            </Modal>
        </>
    )
}
