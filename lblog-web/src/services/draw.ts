import type { DrawChatRequest, SseEvent } from '../types/draw'

function getToken(): string | null {
    return sessionStorage.getItem('lblog_access_token')
}

export function drawChatStream(
    request: DrawChatRequest,
    onEvent: (event: SseEvent) => void,
    onError: (error: Error) => void,
    onComplete: () => void,
): AbortController {
    const controller = new AbortController()
    const token = getToken()

    const headers: Record<string, string> = {
        'Content-Type': 'application/json; charset=utf-8',
    }
    if (token) {
        headers['Authorization'] = `Bearer ${token}`
    }

    let finished = false
    let lastEventTime = Date.now()
    const HEARTBEAT_TIMEOUT = 30000
    let heartbeatTimer: ReturnType<typeof setInterval> | null = null

    const finish = () => {
        if (finished) return
        finished = true
        if (heartbeatTimer) clearInterval(heartbeatTimer)
        onComplete()
    }

    fetch('/api/v1/draw/chat', {
        method: 'POST',
        headers,
        body: JSON.stringify(request),
        signal: controller.signal,
    }).then(async (response) => {
        if (!response.ok) {
            let msg = `HTTP ${response.status}: ${response.statusText}`
            try {
                const body = await response.json()
                if (body?.message) msg = body.message
            } catch { /* ignore */ }
            throw new Error(msg)
        }

        const reader = response.body?.getReader()
        if (!reader) {
            throw new Error('Response body not readable')
        }

        // 定时检查是否长时间无事件（后端异常断开但未关连接）
        heartbeatTimer = setInterval(() => {
            if (finished) return
            if (Date.now() - lastEventTime > HEARTBEAT_TIMEOUT) {
                finish()
            }
        }, 5000)

        const decoder = new TextDecoder()
        let buffer = ''

        while (true) {
            const { done: streamDone, value } = await reader.read()
            if (streamDone) break

            lastEventTime = Date.now()
            buffer += decoder.decode(value, { stream: true })
            const lines = buffer.split('\n')
            buffer = lines.pop() || ''

            for (const line of lines) {
                const trimmed = line.trim()
                if (!trimmed || trimmed.startsWith(':')) continue
                if (trimmed.startsWith('data:')) {
                    try {
                        const raw = trimmed.startsWith('data: ') ? trimmed.slice(6) : trimmed.slice(5)
                        const data = JSON.parse(raw)
                        if (data.type === 'error') {
                            onError(new Error(data.content || 'Unknown error'))
                        } else if (data.type === 'done') {
                            onEvent(data as SseEvent)
                            finish()
                            return
                        } else if (data.type === 'heartbeat') {
                            continue
                        } else {
                            onEvent(data as SseEvent)
                        }
                    } catch { /* ignore parse errors */ }
                }
            }
        }

        // 流正常结束但没有 done 事件
        finish()
    }).catch((err) => {
        if (err.name === 'AbortError') {
            finish()
            return
        }
        onError(err instanceof Error ? err : new Error(String(err)))
        finish()
    })

    return controller
}
