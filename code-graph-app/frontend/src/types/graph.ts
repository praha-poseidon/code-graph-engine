export interface GraphNode {
  id: string
  type: string
  label: string
  fullName?: string
  qualifiedName?: string
  filePath?: string
  gitRepoUrl?: string
  path?: string
  httpMethod?: string
  depth?: number
}

export interface GraphEdge {
  id: string
  source: string
  target: string
  type: string
  lineNumber?: number
}

export interface GraphData {
  nodes: GraphNode[]
  edges: GraphEdge[]
}
