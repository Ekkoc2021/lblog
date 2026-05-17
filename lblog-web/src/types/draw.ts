export interface ChatMessageDTO {
    role: 'user' | 'assistant'
    content: string
}

export interface DrawChatRequest {
    messages: ChatMessageDTO[]
    xml?: string
    previousXml?: string
    sessionId?: string
    minimalStyle?: boolean
    customSystemMessage?: string
}

/** 当前编辑会话状态 */
export interface DiagramSessionState {
    diagramId: number | null
    title: string
    isDirty: boolean
    lastSavedAt: string | null
}

export interface SseEvent {
    type: 'text-delta' | 'tool-call' | 'done' | 'error' | 'heartbeat' | 'reasoning'
    name?: string
    content?: any
    arguments?: { xml: string }
    delta?: string
}
