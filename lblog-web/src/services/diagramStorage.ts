import type { ApiResponse, PageResult } from '../types'
import type { DiagramItem, DiagramDetail, CreateDiagramRequest, UpdateDiagramMetaRequest } from '../types/diagram'
import { request } from './api'

/** 获取图表列表 */
export async function getDiagramList(params?: {
    page?: number
    pageSize?: number
    keyword?: string
}): Promise<ApiResponse<PageResult<DiagramItem>>> {
    const q = Object.entries(params ?? {})
        .filter(([, v]) => v !== undefined && v !== null && v !== '')
        .map(([k, v]) => `${encodeURIComponent(k)}=${encodeURIComponent(String(v))}`)
        .join('&')
    return request<PageResult<DiagramItem>>(`/api/v1/draw/diagrams${q ? `?${q}` : ''}`)
}

/** 获取图表详情（含 XML） */
export async function getDiagramDetail(id: number): Promise<ApiResponse<DiagramDetail>> {
    return request<DiagramDetail>(`/api/v1/draw/diagrams/${id}`)
}

/** 新建图表 */
export async function createDiagram(data: CreateDiagramRequest): Promise<ApiResponse<{ id: number }>> {
    return request<{ id: number }>('/api/v1/draw/diagrams', {
        method: 'POST',
        body: JSON.stringify(data),
    })
}

/** 覆盖保存（更新 XML + 元数据） */
export async function updateDiagram(id: number, data: CreateDiagramRequest): Promise<ApiResponse<null>> {
    return request<null>(`/api/v1/draw/diagrams/${id}`, {
        method: 'PUT',
        body: JSON.stringify(data),
    })
}

/** 更新元数据（重命名） */
export async function updateDiagramMeta(id: number, data: UpdateDiagramMetaRequest): Promise<ApiResponse<null>> {
    return request<null>(`/api/v1/draw/diagrams/${id}`, {
        method: 'PATCH',
        body: JSON.stringify(data),
    })
}

/** 删除图表 */
export async function deleteDiagram(id: number): Promise<ApiResponse<null>> {
    return request<null>(`/api/v1/draw/diagrams/${id}`, { method: 'DELETE' })
}
