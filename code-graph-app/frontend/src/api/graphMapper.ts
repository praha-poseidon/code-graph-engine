import type { GraphData, GraphNode } from '../types/graph'
import type { GraphNodeDto, GraphRelationshipDto } from './graphDto'

const firstLabel = (node: GraphNodeDto) => node.type || node.labels?.[0] || 'CodeElement'

export const shortText = (value: string, max = 34) => {
  if (!value) return ''
  const separators = /[.#/$:]/
  const lastPart = value.split(separators).filter(Boolean).pop() || value
  if (lastPart.length <= max) return lastPart
  return `${lastPart.slice(0, Math.max(8, max - 1))}...`
}

export const nodeIdentity = (node: GraphNodeDto) => node.id || node.elementId || node.qualifiedName || node.name || ''

export const nodeDisplayName = (node: GraphNodeDto | GraphNode) => {
  if ('name' in node && node.name) return node.name
  if ('path' in node && node.path) return node.path
  if (node.qualifiedName) {
    const parts = node.qualifiedName.split('.')
    return parts[parts.length - 1] || node.qualifiedName
  }
  if ('label' in node && node.label) return shortText(node.label)
  if ('id' in node && node.id) return shortText(node.id)
  return '(unknown)'
}

export const mapGraphNode = (node: GraphNodeDto): GraphNode | null => {
  const id = nodeIdentity(node)
  if (!id) return null

  return {
    id,
    type: firstLabel(node),
    label: nodeDisplayName(node),
    fullName: node.name || node.qualifiedName || node.path || id,
    qualifiedName: node.qualifiedName,
    filePath: node.projectFilePath,
    gitRepoUrl: node.gitRepoUrl,
    path: node.path,
    httpMethod: node.httpMethod,
    depth: node.depth,
  }
}

export const mapGraphData = (
  nodes: GraphNodeDto[],
  relationships: GraphRelationshipDto[] = [],
): GraphData => {
  const nodeMap = new Map<string, GraphNode>()

  nodes.forEach((node) => {
    const normalized = mapGraphNode(node)
    if (normalized) nodeMap.set(normalized.id, normalized)
  })

  const idByElementId = new Map<string, string>()
  nodes.forEach((node) => {
    const id = nodeIdentity(node)
    if (id && node.elementId) idByElementId.set(node.elementId, id)
  })

  relationships.forEach((rel) => {
    const fromNodeId = idByElementId.get(rel.fromNodeId) || rel.fromNodeId
    const toNodeId = idByElementId.get(rel.toNodeId) || rel.toNodeId
    if (!nodeMap.has(fromNodeId)) {
      nodeMap.set(fromNodeId, {
        id: fromNodeId,
        type: 'CodeElement',
        label: shortText(fromNodeId),
        fullName: fromNodeId,
      })
    }
    if (!nodeMap.has(toNodeId)) {
      nodeMap.set(toNodeId, {
        id: toNodeId,
        type: 'CodeElement',
        label: shortText(rel.toNodeName || rel.toQualifiedName || toNodeId),
        fullName: rel.toNodeName || rel.toQualifiedName || toNodeId,
        qualifiedName: rel.toQualifiedName,
      })
    }
  })

  const edges = relationships.map((rel, index) => {
    const fromNodeId = idByElementId.get(rel.fromNodeId) || rel.fromNodeId
    const toNodeId = idByElementId.get(rel.toNodeId) || rel.toNodeId
    return {
      id: `${fromNodeId}->${toNodeId}:${rel.relationshipType}:${index}`,
      source: fromNodeId,
      target: toNodeId,
      type: rel.relationshipType,
      lineNumber: rel.lineNumber,
    }
  })

  return {
    nodes: Array.from(nodeMap.values()),
    edges,
  }
}
