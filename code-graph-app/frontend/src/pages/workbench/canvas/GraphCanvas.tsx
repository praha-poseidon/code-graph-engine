import { useCallback, useEffect, useMemo, useRef, useState, type ReactNode } from 'react'
import Sigma from 'sigma'
import Graph from 'graphology'
import forceAtlas2 from 'graphology-layout-forceatlas2'
import noverlap from 'graphology-layout-noverlap'
import type { GraphData, GraphNode } from '../../../types/graph'
import { graphDataToSigma, type SigmaEdgeAttributes, type SigmaNodeAttributes } from './sigmaAdapter'

interface GraphCanvasProps {
  data: GraphData
  selectedNodeId: string | null
  traceRootNodeId?: string | null
  onNodeSelect: (node: GraphNode | null) => void
  renderToolbar?: (controls: SigmaCanvasControls) => ReactNode
}

export interface SigmaCanvasControls {
  zoomIn: () => void
  zoomOut: () => void
  resetZoom: () => void
  rerunLayout: () => void
}

const dimColor = (hex: string, amount: number) => {
  const value = hex.replace('#', '')
  const r = parseInt(value.slice(0, 2), 16)
  const g = parseInt(value.slice(2, 4), 16)
  const b = parseInt(value.slice(4, 6), 16)
  const base = { r: 18, g: 18, b: 28 }
  const mix = (channel: number, baseChannel: number) =>
    Math.round(baseChannel + (channel - baseChannel) * amount).toString(16).padStart(2, '0')
  return `#${mix(r, base.r)}${mix(g, base.g)}${mix(b, base.b)}`
}

const applyCurvedEdgeApproximation = (graph: Graph<SigmaNodeAttributes, SigmaEdgeAttributes>) => {
  graph.edges().forEach((edge, index) => {
    if (!graph.hasEdge(edge)) return

    const [source, target] = graph.extremities(edge)
    const sourceX = graph.getNodeAttribute(source, 'x')
    const sourceY = graph.getNodeAttribute(source, 'y')
    const targetX = graph.getNodeAttribute(target, 'x')
    const targetY = graph.getNodeAttribute(target, 'y')
    const dx = targetX - sourceX
    const dy = targetY - sourceY
    const length = Math.hypot(dx, dy) || 1
    const offsetDirection = index % 2 === 0 ? 1 : -1
    const offset = Math.min(42, Math.max(16, length * 0.16)) * offsetDirection
    const controlId = `__curve__${edge}`
    const attributes = { ...graph.getEdgeAttributes(edge) }
    const label = attributes.label

    graph.dropEdge(edge)
    graph.addNode(controlId, {
      x: (sourceX + targetX) / 2 + (-dy / length) * offset,
      y: (sourceY + targetY) / 2 + (dx / length) * offset,
      size: 0,
      color: '#000000',
      label: '',
      nodeType: 'ControlPoint',
      isControlPoint: true,
      zIndex: -1,
    })
    // Put relation label only on the first half so it is not drawn twice.
    graph.addEdge(source, controlId, { ...attributes, label })
    graph.addEdge(controlId, target, { ...attributes, label: '' })
  })
}

export default function GraphCanvas({ data, selectedNodeId, traceRootNodeId, onNodeSelect, renderToolbar }: GraphCanvasProps) {
  const containerRef = useRef<HTMLDivElement>(null)
  const sigmaRef = useRef<Sigma | null>(null)
  const graphRef = useRef<Graph<SigmaNodeAttributes, SigmaEdgeAttributes> | null>(null)
  const selectedRef = useRef<string | null>(null)
  const [hoveredNode, setHoveredNode] = useState<string | null>(null)

  const nodeMap = useMemo(() => {
    return new Map(data.nodes.map((node) => [node.id, node]))
  }, [data.nodes])

  useEffect(() => {
    selectedRef.current = selectedNodeId
    sigmaRef.current?.refresh()
  }, [selectedNodeId])

  useEffect(() => {
    if (!containerRef.current) return

    const graph = new Graph<SigmaNodeAttributes, SigmaEdgeAttributes>()
    graphRef.current = graph

    const sigma = new Sigma(graph, containerRef.current, {
      renderLabels: true,
      renderEdgeLabels: true,
      labelFont: 'PingFang SC, JetBrains Mono, monospace',
      labelSize: 11,
      labelWeight: '500',
      labelColor: { color: '#e4e4ed' },
      labelRenderedSizeThreshold: 7,
      labelDensity: 0.12,
      labelGridCellSize: 74,
      edgeLabelFont: 'JetBrains Mono, PingFang SC, monospace',
      edgeLabelSize: 9,
      edgeLabelWeight: '600',
      edgeLabelColor: { color: '#c4b5fd' },
      defaultNodeColor: '#6b7280',
      defaultEdgeColor: '#2a2a3a',
      hideEdgesOnMove: true,
      zIndex: true,
      defaultDrawNodeHover: (context, data, settings) => {
        const label = data.label
        if (!label) return
        const size = settings.labelSize || 11
        context.font = `600 ${size}px PingFang SC, sans-serif`
        const width = context.measureText(label).width + 18
        const height = size + 12
        const x = data.x
        const y = data.y - (data.size || 8) - 13

        context.fillStyle = '#12121c'
        context.beginPath()
        context.roundRect(x - width / 2, y - height / 2, width, height, 5)
        context.fill()
        context.strokeStyle = data.color || '#8b5cf6'
        context.lineWidth = 2
        context.stroke()
        context.fillStyle = '#f5f5f7'
        context.textAlign = 'center'
        context.textBaseline = 'middle'
        context.fillText(label, x, y)

        context.beginPath()
        context.arc(data.x, data.y, (data.size || 8) + 4, 0, Math.PI * 2)
        context.strokeStyle = data.color || '#8b5cf6'
        context.globalAlpha = 0.5
        context.stroke()
        context.globalAlpha = 1
      },
      nodeReducer: (node, data) => {
        const result = { ...data }
        if (data.isControlPoint) {
          result.size = 0
          result.label = ''
          result.zIndex = -1
          return result
        }
        const selected = selectedRef.current
        if (!selected) return result

        const graph = graphRef.current
        const isSelected = node === selected
        const isNeighbor = graph?.hasEdge(node, selected) || graph?.hasEdge(selected, node)

        if (isSelected) {
          result.size = (data.size || 8) * 1.9
          result.zIndex = 3
        } else if (isNeighbor) {
          result.size = (data.size || 8) * 1.3
          result.zIndex = 2
        } else {
          result.color = dimColor(data.color || '#6b7280', 0.22)
          result.size = (data.size || 8) * 0.65
          result.zIndex = 0
        }
        return result
      },
      edgeReducer: (edge, data) => {
        const result = { ...data }
        // Always keep relation type label when present.
        if (data.relationType && !result.label) {
          result.label = data.relationType
        }
        const selected = selectedRef.current
        if (!selected) return result

        const graph = graphRef.current
        const [source, target] = graph?.extremities(edge) || []
        const connected = source === selected || target === selected
        if (connected) {
          result.size = Math.max(2.5, (data.size || 1) * 3)
          result.zIndex = 2
        } else {
          result.color = dimColor(data.color || '#4a4a5a', 0.12)
          result.size = 0.3
          result.zIndex = 0
          // Hide non-selected edge labels to reduce clutter.
          result.label = ''
        }
        return result
      },
    })

    sigma.on('clickNode', ({ node }) => {
      onNodeSelect(nodeMap.get(node) || null)
    })
    sigma.on('clickStage', () => onNodeSelect(null))
    sigma.on('enterNode', ({ node }) => {
      setHoveredNode(nodeMap.get(node)?.label || null)
      if (containerRef.current) containerRef.current.style.cursor = 'pointer'
    })
    sigma.on('leaveNode', () => {
      setHoveredNode(null)
      if (containerRef.current) containerRef.current.style.cursor = 'grab'
    })

    sigmaRef.current = sigma

    return () => {
      sigma.kill()
      sigmaRef.current = null
      graphRef.current = null
    }
  }, [nodeMap, onNodeSelect])

  /**
   * Call-order (layered) layout around a focus node:
   * - left  (rank < 0): nodes that call / point into the focus (upstream)
   * - center (rank 0): focus node
   * - right (rank > 0): nodes the focus calls / points to (downstream)
   */
  const runDirectedLayout = useCallback((graph: Graph<SigmaNodeAttributes, SigmaEdgeAttributes>, rootId: string) => {
    if (graph.order === 0) return

    const ranks = new Map<string, number>()
    const queue: Array<{ id: string; rank: number }> = []
    const root = graph.hasNode(rootId) ? rootId : graph.nodes().find((id) => !graph.getNodeAttribute(id, 'isControlPoint')) || graph.nodes()[0]
    if (!root) return

    ranks.set(root, 0)
    queue.push({ id: root, rank: 0 })

    while (queue.length) {
      const current = queue.shift()
      if (!current) continue
      const { id, rank } = current

      // Downstream: A -> B  => B is to the right of A
      graph.outNeighbors(id).forEach((neighbor) => {
        if (graph.getNodeAttribute(neighbor, 'isControlPoint')) return
        if (ranks.has(neighbor)) return
        const nextRank = rank + 1
        ranks.set(neighbor, nextRank)
        queue.push({ id: neighbor, rank: nextRank })
      })

      // Upstream: C -> A  => C is to the left of A
      graph.inNeighbors(id).forEach((neighbor) => {
        if (graph.getNodeAttribute(neighbor, 'isControlPoint')) return
        if (ranks.has(neighbor)) return
        const nextRank = rank - 1
        ranks.set(neighbor, nextRank)
        queue.push({ id: neighbor, rank: nextRank })
      })
    }

    const rankedValues = Array.from(ranks.values())
    const maxAbs = rankedValues.length ? Math.max(...rankedValues.map((v) => Math.abs(v)), 1) : 1
    const disconnectedRank = maxAbs + 1
    graph.nodes().forEach((node) => {
      if (graph.getNodeAttribute(node, 'isControlPoint')) return
      if (!ranks.has(node)) ranks.set(node, disconnectedRank)
    })

    const layers = new Map<number, string[]>()
    ranks.forEach((rank, node) => {
      if (graph.getNodeAttribute(node, 'isControlPoint')) return
      const layer = layers.get(rank) || []
      layer.push(node)
      layers.set(rank, layer)
    })

    const nodeGap = graph.order > 120 ? 40 : 56
    const rankGap = graph.order > 120 ? 190 : 230
    const minRank = Math.min(...Array.from(layers.keys()))
    const maxRank = Math.max(...Array.from(layers.keys()))
    const centerOffset = ((maxRank + minRank) * rankGap) / 2

    Array.from(layers.entries())
      .sort(([a], [b]) => a - b)
      .forEach(([rank, nodes]) => {
        nodes.sort((a, b) =>
          String(graph.getNodeAttribute(a, 'label')).localeCompare(String(graph.getNodeAttribute(b, 'label'))),
        )
        const columnHeight = Math.max(0, nodes.length - 1) * nodeGap
        nodes.forEach((node, index) => {
          graph.setNodeAttribute(node, 'x', rank * rankGap - centerOffset)
          graph.setNodeAttribute(node, 'y', index * nodeGap - columnHeight / 2)
        })
      })

    // Keep root visually centered on the vertical axis.
    if (graph.hasNode(root)) {
      graph.setNodeAttribute(root, 'y', 0)
    }

    applyCurvedEdgeApproximation(graph)
  }, [])

  const runLayout = useCallback((graph: Graph<SigmaNodeAttributes, SigmaEdgeAttributes>) => {
    if (graph.order === 0) return
    const inferred = forceAtlas2.inferSettings(graph)
    forceAtlas2.assign(graph, {
      iterations: graph.order > 1000 ? 90 : 140,
      settings: {
        ...inferred,
        gravity: graph.order > 500 ? 0.45 : 0.8,
        scalingRatio: graph.order > 500 ? 42 : 22,
        slowDown: graph.order > 500 ? 3 : 1.5,
        barnesHutOptimize: graph.order > 200,
        outboundAttractionDistribution: true,
        adjustSizes: true,
      },
    })
    noverlap.assign(graph, {
      maxIterations: 24,
      settings: {
        ratio: 1.15,
        margin: 8,
        expansion: 1.05,
      },
    })
  }, [])

  const setSigmaGraph = useCallback(() => {
    const sigma = sigmaRef.current
    if (!sigma) return
    const graph = graphDataToSigma(data)
    graphRef.current = graph
    if (traceRootNodeId) {
      runDirectedLayout(graph, traceRootNodeId)
    } else {
      runLayout(graph)
    }
    sigma.setGraph(graph)
    sigma.getCamera().animatedReset({ duration: 350 })
  }, [data, runDirectedLayout, runLayout, traceRootNodeId])

  useEffect(() => {
    setSigmaGraph()
  }, [setSigmaGraph])

  const zoomIn = () => sigmaRef.current?.getCamera().animatedZoom({ duration: 180 })
  const zoomOut = () => sigmaRef.current?.getCamera().animatedUnzoom({ duration: 180 })
  const resetZoom = () => sigmaRef.current?.getCamera().animatedReset({ duration: 260 })
  const rerunLayout = () => setSigmaGraph()

  return (
    <div className="relative h-full w-full">
      <div ref={containerRef} className="h-full w-full cursor-grab active:cursor-grabbing" />

      {hoveredNode && !selectedNodeId && (
        <div className="pointer-events-none absolute left-1/2 top-4 z-20 -translate-x-1/2 rounded-lg border border-white/10 bg-[#12121c]/95 px-3 py-1.5 text-sm font-mono text-white shadow-xl">
          {hoveredNode}
        </div>
      )}

      {traceRootNodeId && (
        <div className="pointer-events-none absolute bottom-4 left-4 z-20 rounded-lg border border-white/10 bg-[#12121c]/92 px-3 py-2 text-[11px] text-[#b7b0cf] shadow-xl">
          <div className="font-semibold text-violet-200">调用顺序布局</div>
          <div>左：调用我的 / 上游 · 中：焦点 · 右：我调用的 / 下游</div>
        </div>
      )}

      {renderToolbar?.({ zoomIn, zoomOut, resetZoom, rerunLayout })}
    </div>
  )
}
