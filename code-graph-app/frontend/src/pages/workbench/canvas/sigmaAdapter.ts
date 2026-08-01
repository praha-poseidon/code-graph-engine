import Graph from 'graphology'
import type { GraphData, GraphNode } from '../../../types/graph'

export interface SigmaNodeAttributes {
  x: number
  y: number
  size: number
  color: string
  label: string
  nodeType: string
  filePath?: string
  qualifiedName?: string
  depth?: number
  isControlPoint?: boolean
  zIndex?: number
}

export interface SigmaEdgeAttributes {
  size: number
  color: string
  relationType: string
  label?: string
  type?: string
  zIndex?: number
}

const NODE_COLORS: Record<string, string> = {
  CodeEndpoint: '#3b82f6',
  ENDPOINT: '#3b82f6',
  HTTP: '#3b82f6',
  UI: '#06b6d4',
  CodeFunction: '#10b981',
  FUNCTION: '#10b981',
  CodeUnit: '#f59e0b',
  UNIT: '#f59e0b',
  CodePackage: '#8b5cf6',
  PACKAGE: '#8b5cf6',
  CodeElement: '#64748b',
}

const NODE_SIZES: Record<string, string | number> = {
  CodeEndpoint: 10,
  ENDPOINT: 10,
  HTTP: 10,
  UI: 10,
  CodeFunction: 6,
  FUNCTION: 6,
  CodeUnit: 9,
  UNIT: 9,
  CodePackage: 13,
  PACKAGE: 13,
  CodeElement: 5,
}

const EDGE_STYLES: Record<string, { color: string; size: number }> = {
  CALLS: { color: '#7c3aed', size: 1.4 },
  RENDERS: { color: '#f97316', size: 1.5 },
  PACKAGE_TO_UNIT: { color: '#2d5a3d', size: 0.8 },
  PACKAGE_TO_PACKAGE: { color: '#4c1d95', size: 0.9 },
  UNIT_TO_FUNCTION: { color: '#0e7490', size: 1 },
  ENDPOINT_TO_FUNCTION: { color: '#1d4ed8', size: 1.3 },
  FUNCTION_TO_ENDPOINT: { color: '#be185d', size: 1.2 },
  MATCHES: { color: '#c2410c', size: 1 },
}

/** Short edge labels so call-order graphs stay readable. */
export const formatEdgeLabel = (relationType?: string) => {
  if (!relationType) return ''
  const map: Record<string, string> = {
    CALLS: 'CALLS',
    RENDERS: 'RENDERS',
    FUNCTION_TO_ENDPOINT: 'FN→HTTP',
    ENDPOINT_TO_FUNCTION: 'EP→FN',
    UNIT_TO_FUNCTION: 'FILE→FN',
    PACKAGE_TO_UNIT: 'PKG→FILE',
    PACKAGE_TO_PACKAGE: 'PKG→PKG',
    MATCHES: 'MATCH',
    EXTENDS: 'EXTENDS',
    IMPLEMENTS: 'IMPL',
    OVERRIDES: 'OVERRIDE',
  }
  return map[relationType] || relationType
}

export const shortNodeType = (type?: string) => {
  if (!type) return 'NODE'
  const value = type.replace(/^Code/, '').toUpperCase()
  return value || 'NODE'
}

export const formatNodeLabel = (node: GraphNode) => {
  const type = shortNodeType(node.type)
  const name = node.label || node.fullName || shortTextFromId(node.id)
  return `[${type}] ${name}`
}

const shortTextFromId = (id: string) => {
  if (!id) return 'unknown'
  const parts = id.split(/[#:/]/).filter(Boolean)
  return parts[parts.length - 1] || id
}

const getNodeColor = (node: GraphNode) => NODE_COLORS[node.type] || '#9ca3af'
const getNodeSize = (node: GraphNode) => Number(NODE_SIZES[node.type] || 6)

/**
 * Build a directed graphology graph for call-order layouts.
 * Edge direction: source --relation--> target (caller -> callee for CALLS).
 */
export const graphDataToSigma = (data: GraphData) => {
  const graph = new Graph<SigmaNodeAttributes, SigmaEdgeAttributes>({ type: 'directed', multi: false, allowSelfLoops: true })
  const count = Math.max(data.nodes.length, 1)
  const spread = Math.sqrt(count) * 80
  const goldenAngle = Math.PI * (3 - Math.sqrt(5))

  data.nodes.forEach((node, index) => {
    const angle = index * goldenAngle
    const radius = spread * Math.sqrt((index + 1) / count)
    const jitter = spread * 0.05

    graph.addNode(node.id, {
      x: radius * Math.cos(angle) + (Math.random() - 0.5) * jitter,
      y: radius * Math.sin(angle) + (Math.random() - 0.5) * jitter,
      size: getNodeSize(node),
      color: getNodeColor(node),
      label: formatNodeLabel(node),
      nodeType: node.type,
      filePath: node.filePath,
      qualifiedName: node.qualifiedName,
      depth: node.depth,
    })
  })

  data.edges.forEach((edge) => {
    if (!graph.hasNode(edge.source) || !graph.hasNode(edge.target)) return
    if (graph.hasEdge(edge.source, edge.target)) return
    const style = EDGE_STYLES[edge.type] || { color: '#4a4a5a', size: 0.8 }
    graph.addEdge(edge.source, edge.target, {
      size: style.size,
      color: style.color,
      relationType: edge.type,
      label: formatEdgeLabel(edge.type),
      type: 'arrow',
      zIndex: 0,
    })
  })

  return graph
}
