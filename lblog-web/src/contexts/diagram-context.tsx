import type React from 'react'
import { createContext, useContext, useRef, useState } from 'react'
import type { DrawIoEmbedRef } from 'react-drawio'

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
}

const DiagramContext = createContext<DiagramContextType | undefined>(undefined)

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

    const loadDiagram = (chart: string) => {
        setChartXML(chart)
        if (!tryLoad(chart)) {
            const retry = setInterval(() => {
                if (tryLoad(chart)) {
                    clearInterval(retry)
                }
            }, 200)
            setTimeout(() => clearInterval(retry), 30000)
        }
    }

    const handleDiagramExport = (data: any) => {
        const extractedXML = extractDiagramXML(data.data)
        setChartXML(extractedXML)
    }

    const handleDiagramAutoSave = (data: { xml?: string }) => {
        if (!data?.xml) return
        if (!isDrawioReady && isRealDiagram(chartXML)) {
            return
        }
        setChartXML(data.xml)
    }

    const clearDiagram = () => {
        const emptyDiagram = '<mxfile><diagram name="Page-1" id="page-1"><mxGraphModel><root><mxCell id="0"/><mxCell id="1" parent="0"/></root></mxGraphModel></diagram></mxfile>'
        loadDiagram(emptyDiagram)
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
