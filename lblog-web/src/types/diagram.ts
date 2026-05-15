/** 图表列表项（不含完整 XML） */
export interface DiagramItem {
    id: number
    title: string
    description: string | null
    tags: string | null
    thumbnail: string | null
    fileSize: number
    createdAt: string
    updatedAt: string
}

/** 图表详情（含完整 XML） */
export interface DiagramDetail extends DiagramItem {
    userId: number
    xmlData: string
}

/** 创建/保存图表请求 */
export interface CreateDiagramRequest {
    title: string
    description?: string
    tags?: string
    xmlData: string
    thumbnail?: string
    fileSize?: number
}

/** 更新元数据请求 */
export interface UpdateDiagramMetaRequest {
    title: string
    description?: string
    tags?: string
}
