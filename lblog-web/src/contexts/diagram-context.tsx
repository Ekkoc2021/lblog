import type React from 'react'
import { createContext, useContext, useRef, useState, useCallback, useEffect } from 'react'
import type { DrawIoEmbedRef } from 'react-drawio'
import { createDiagram, updateDiagram, getDiagramDetail } from '../services/diagramStorage'

interface DiagramContextType {
    chartXML: string
    loadDiagram: (chart: string) => void
    clearDiagram: () => void
    drawioRef: React.MutableRefObject<DrawIoEmbedRef | null>
    isDrawioReady: boolean
    onDrawioLoad: () => void
    resetDrawioReady: () => void
    handleDiagramExport: (data: any) => void
    handleDiagramAutoSave: (data: { xml?: string }) => void

    // 存储相关
    sessionId: number | null
    sessionTitle: string
    isDirty: boolean
    lastSavedAt: string | null
    saving: boolean

    setSessionTitle: (title: string) => void
    saveDiagram: () => Promise<number | null>
    saveAsDiagram: (title: string, description?: string, tags?: string) => Promise<number>
    openDiagram: (id: number) => Promise<void>
    revertDiagram: () => void
    handleExportResult: (data: any) => void
}

const DiagramContext = createContext<DiagramContextType | undefined>(undefined)

const EMPTY_DIAGRAM = '<mxfile><diagram name="Page-1" id="page-1"><mxGraphModel><root><mxCell id="0"/><mxCell id="1" parent="0"/></root></mxGraphModel></diagram></mxfile>'

function isRealDiagram(xml: string): boolean {
    if (!xml) return false
    return xml.includes('value=') || (xml.match(/<mxCell/g) || []).length > 2
}

function extractDiagramXML(data: string): string {
    if (!data.includes('<svg')) return data
    const match = data.match(/<div class="xml"[^>]*>([\s\S]*?)<\/div>/)
    if (match?.[1]) {
        return match[1].trim()
    }
    return data
}

export function DiagramProvider({ children }: { children: React.ReactNode }) {
    const [chartXML, setChartXML] = useState<string>('')
    const [isDrawioReady, setIsDrawioReady] = useState(false)
    const hasCalledOnLoadRef = useRef(false)
    const drawioRef = useRef<DrawIoEmbedRef | null>(null)

    // 存储状态
    const [sessionId, setSessionId] = useState<number | null>(null)
    const [sessionTitle, setSessionTitle] = useState('无标题图表')
    const [lastSavedAt, setLastSavedAt] = useState<string | null>(null)
    const [saving, setSaving] = useState(false)
    const savedXmlRef = useRef<string>('')

    // 用 ref 实时追踪最新 XML，避免 useCallback 闭包过期问题
    const chartXMLRef = useRef(chartXML)
    useEffect(() => { chartXMLRef.current = chartXML }, [chartXML])

    // 主动导出 XML 的 Promise 桥接
    const exportResolveRef = useRef<((xml: string) => void) | null>(null)

    const isDirty = chartXML !== savedXmlRef.current && isRealDiagram(chartXML)

    const tryLoad = (xml: string) => {
        if (drawioRef.current) {
            drawioRef.current.load({ xml })
            return true
        }
        return false
    }

    const onDrawioLoad = () => {
        if (hasCalledOnLoadRef.current) return
        hasCalledOnLoadRef.current = true
        setIsDrawioReady(true)
    }

    const resetDrawioReady = () => {
        hasCalledOnLoadRef.current = false
        setIsDrawioReady(false)
    }

    const loadDiagram = useCallback((chart: string) => {
        chartXMLRef.current = chart
        setChartXML(chart)
        if (!tryLoad(chart)) {
            const retry = setInterval(() => {
                if (tryLoad(chart)) {
                    clearInterval(retry)
                }
            }, 200)
            setTimeout(() => clearInterval(retry), 30000)
        }
    }, [])

    const handleDiagramExport = (data: any) => {
        const extractedXML = extractDiagramXML(data.data)
        chartXMLRef.current = extractedXML
        setChartXML(extractedXML)
    }

    const handleDiagramAutoSave = (data: { xml?: string }) => {
        if (!data?.xml) return
        if (!isDrawioReady && isRealDiagram(chartXML)) {
            return
        }
        chartXMLRef.current = data.xml
        setChartXML(data.xml)
    }

    const clearDiagram = () => {
        loadDiagram(EMPTY_DIAGRAM)
        setSessionId(null)
        setSessionTitle('无标题图表')
        setLastSavedAt(null)
        savedXmlRef.current = EMPTY_DIAGRAM
    }

    /** 打开已有图表 */
    const openDiagram = useCallback(async (id: number) => {
        const res = await getDiagramDetail(id)
        const detail = res.data
        if (!detail) throw new Error('图表不存在')

        loadDiagram(detail.xmlData)
        savedXmlRef.current = detail.xmlData
        setSessionId(detail.id)
        setSessionTitle(detail.title)
        setLastSavedAt(detail.updatedAt)
    }, [loadDiagram])

    /** onExport 回调：桥接 draw.io 导出结果到 getCurrentXml 的 Promise */
    const handleExportResult = useCallback((data: any) => {
        if (data?.xml && exportResolveRef.current) {
            exportResolveRef.current(data.xml)
            exportResolveRef.current = null
        } else if (data?.data && exportResolveRef.current) {
            exportResolveRef.current(extractDiagramXML(data.data))
            exportResolveRef.current = null
        }
    }, [])

    /** 从 draw.io 主动获取最新 XML（exportDiagram + Promise，2s 超时回退到 ref） */
    const getCurrentXml = useCallback(async (): Promise<string> => {
        const fallback = chartXMLRef.current || EMPTY_DIAGRAM
        if (!drawioRef.current || !isDrawioReady) {
            return fallback
        }

        return new Promise<string>((resolve) => {
            exportResolveRef.current = resolve
            drawioRef.current!.exportDiagram({ format: 'xmlsvg' })
            setTimeout(() => {
                if (exportResolveRef.current) {
                    exportResolveRef.current(fallback)
                    exportResolveRef.current = null
                }
            }, 2000)
        })
    }, [isDrawioReady])

    /** 保存当前图表（新建或覆盖） */
    const saveDiagram = useCallback(async (): Promise<number | null> => {
        setSaving(true)
        try {
            const xml = await getCurrentXml()
            const payload = {
                title: sessionTitle,
                xmlData: xml,
                fileSize: new TextEncoder().encode(xml).length,
            }

            if (sessionId !== null) {
                await updateDiagram(sessionId, payload)
                savedXmlRef.current = xml
                setChartXML(xml)
                setLastSavedAt(new Date().toISOString())
                return sessionId
            } else {
                const res = await createDiagram(payload)
                const newId = res.data.id
                setSessionId(newId)
                savedXmlRef.current = xml
                setChartXML(xml)
                setLastSavedAt(new Date().toISOString())
                return newId
            }
        } finally {
            setSaving(false)
        }
    }, [sessionTitle, sessionId])

    /** 另存为（总是新建） */
    const saveAsDiagram = useCallback(async (title: string, description?: string, tags?: string): Promise<number> => {
        setSaving(true)
        try {
            const xml = await getCurrentXml()
            const res = await createDiagram({
                title,
                description,
                tags,
                xmlData: xml,
                fileSize: new TextEncoder().encode(xml).length,
            })
            const newId = res.data.id
            savedXmlRef.current = xml
            setChartXML(xml)
            setSessionId(newId)
            setSessionTitle(title)
            setLastSavedAt(new Date().toISOString())
            return newId
        } finally {
            setSaving(false)
        }
    }, [getCurrentXml])

    /** 回退到上次保存版本 */
    const revertDiagram = () => {
        if (savedXmlRef.current) {
            loadDiagram(savedXmlRef.current)
        }
    }

    return (
        <DiagramContext.Provider
            value={{
                chartXML,
                loadDiagram,
                clearDiagram,
                drawioRef,
                isDrawioReady,
                onDrawioLoad,
                resetDrawioReady,
                handleDiagramExport,
                handleDiagramAutoSave,

                sessionId,
                sessionTitle,
                isDirty,
                lastSavedAt,
                saving,

                setSessionTitle,
                saveDiagram,
                saveAsDiagram,
                openDiagram,
                revertDiagram,
                handleExportResult,
            }}
        >
            {children}
        </DiagramContext.Provider>
    )
}

export function useDiagram() {
    const context = useContext(DiagramContext)
    if (context === undefined) {
        throw new Error('useDiagram must be used within a DiagramProvider')
    }
    return context
}
