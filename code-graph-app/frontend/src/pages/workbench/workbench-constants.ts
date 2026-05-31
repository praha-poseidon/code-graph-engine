import type { ElementType } from 'react'
import { Box, Braces, Code2, Folder } from 'lucide-react'

export type WorkbenchMode = 'graph' | 'settings'
export type SettingsTab = 'projects' | 'ssh-keys' | 'endpoint-rules'

export interface GraphMetadata {
  gitRepoUrls: string[]
  nodeTypes: string[]
  relationshipTypes: string[]
}

export const SETTINGS_TABS: Array<{ id: SettingsTab; label: string }> = [
  { id: 'projects', label: '仓库管理' },
  { id: 'ssh-keys', label: 'SSH 密钥' },
  { id: 'endpoint-rules', label: '端点规则' },
]

export const NODE_FILTER_META: Record<string, { label: string; color: string; icon: ElementType }> = {
  CodePackage: { label: 'Package', color: '#8b5cf6', icon: Folder },
  CodeUnit: { label: 'Class', color: '#f59e0b', icon: Box },
  CodeFunction: { label: 'Function', color: '#10b981', icon: Braces },
  CodeEndpoint: { label: 'Endpoint', color: '#3b82f6', icon: Code2 },
}

export const EDGE_FILTER_META: Record<string, { label: string; color: string }> = {
  PACKAGE_TO_UNIT: { label: 'Package to unit', color: '#2d5a3d' },
  UNIT_TO_FUNCTION: { label: 'Unit to function', color: '#0e7490' },
  CALLS: { label: 'Calls', color: '#8b5cf6' },
  ENDPOINT_TO_FUNCTION: { label: 'Endpoint to function', color: '#2563eb' },
  FUNCTION_TO_ENDPOINT: { label: 'Function to endpoint', color: '#db2777' },
  MATCHES: { label: 'Matches', color: '#f97316' },
}

export const repoDisplayName = (url?: string) => {
  if (!url) return 'All repositories'
  const cleaned = url.replace(/\.git$/, '')
  const parts = cleaned.split(/[/:]/).filter(Boolean)
  return parts.slice(-2).join('/') || cleaned
}
