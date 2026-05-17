import { request } from './api'
import type { ApiResponse } from '../types'
import type { ChatSessionVO, ChatMessageVO } from '../types/chat'

/** 获取会话列表 */
export function fetchSessions(
  agentType: string,
  page = 1,
  size = 20
): Promise<ApiResponse<ChatSessionVO[]>> {
  const params = new URLSearchParams({ agentType, page: String(page), size: String(size) })
  return request<ChatSessionVO[]>(`/api/v1/ai/chat/sessions?${params}`)
}

/** 创建新会话 */
export function createSession(
  agentType: string,
  modelName?: string
): Promise<ApiResponse<ChatSessionVO>> {
  return request<ChatSessionVO>('/api/v1/ai/chat/sessions', {
    method: 'POST',
    body: JSON.stringify({ agentType, modelName }),
  })
}

/** 更新会话标题 */
export function updateSessionTitle(
  id: number,
  title: string
): Promise<ApiResponse<null>> {
  return request<null>(`/api/v1/ai/chat/sessions/${id}/title`, {
    method: 'PUT',
    body: JSON.stringify({ title }),
  })
}

/** 删除会话 */
export function deleteSession(id: number): Promise<ApiResponse<null>> {
  return request<null>(`/api/v1/ai/chat/sessions/${id}`, {
    method: 'DELETE',
  })
}

/** 加载会话消息 */
export function fetchMessages(
  sessionId: number
): Promise<ApiResponse<ChatMessageVO[]>> {
  return request<ChatMessageVO[]>(`/api/v1/ai/chat/sessions/${sessionId}/messages`)
}
