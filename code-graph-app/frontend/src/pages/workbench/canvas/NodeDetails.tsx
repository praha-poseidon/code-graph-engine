import { GitBranch, Play, X } from 'lucide-react'
import type { WorkbenchController } from '../state/useWorkbenchState'

function DetailRow({ label, value }: { label: string; value?: string | number | null }) {
  if (value === undefined || value === null || value === '') return null

  return (
    <div className="space-y-1">
      <div className="text-[10px] font-semibold uppercase tracking-wide text-[#77718f]">{label}</div>
      <div className="break-words rounded-lg bg-white/[0.045] px-3 py-2 font-mono text-[11px] leading-5 text-[#dcd5ef]">
        {value}
      </div>
    </div>
  )
}

export default function NodeDetails({ controller }: { controller: WorkbenchController }) {
  const node = controller.selectedNode
  if (!node) return null

  return (
    <aside className="absolute right-24 top-5 z-40 flex max-h-[calc(100%-96px)] w-[320px] flex-col overflow-hidden rounded-xl border border-white/12 bg-[#10111d]/96 shadow-2xl backdrop-blur">
      <div className="flex min-h-12 items-center justify-between gap-3 border-b border-white/10 px-3.5">
        <div className="min-w-0">
          <div className="flex items-center gap-2">
            <span className="h-2 w-2 shrink-0 rounded-full bg-violet-300" />
            <div className="truncate text-sm font-bold text-white">{node.label}</div>
            <span className="shrink-0 rounded-md bg-white/[0.08] px-2 py-1 text-[10px] font-semibold text-[#9d97b6]">
              {node.type}
            </span>
          </div>
        </div>
        <button
          onClick={() => controller.setSelectedNode(null)}
          className="grid h-8 w-8 shrink-0 place-items-center rounded-lg text-[#77718f] transition hover:bg-white/[0.08] hover:text-white"
        >
          <X className="h-4 w-4" />
        </button>
      </div>

      <div className="min-h-0 flex-1 space-y-3 overflow-y-auto p-3.5">
        <DetailRow label="ID" value={node.id} />
        <DetailRow label="Qualified" value={node.qualifiedName || node.fullName} />
        <DetailRow label="File" value={node.filePath || node.path} />
        <DetailRow label="Repo" value={node.gitRepoUrl} />
        <DetailRow label="Endpoint" value={node.httpMethod} />

        <section className="space-y-3 rounded-xl border border-white/10 bg-white/[0.025] p-3">
          <div className="flex items-center gap-2 text-[11px] font-semibold uppercase tracking-wide text-[#9d97b6]">
            <GitBranch className="h-3.5 w-3.5 text-violet-300" />
            Trace Node
          </div>
          {controller.traceRootNode && (
            <div className="truncate rounded-lg bg-violet-500/10 px-3 py-2 text-xs text-violet-100/80">
              Root: {controller.traceRootNode.label}
            </div>
          )}
          <div className="grid grid-cols-[1fr_112px] gap-2">
            <select
              value={controller.traceDirection}
              onChange={event => controller.setTraceDirection(event.target.value as 'FORWARD' | 'BACKWARD' | 'BOTH')}
              className="h-9 rounded-lg border border-white/10 bg-[#080912] px-3 text-xs text-[#dcd5ef] outline-none transition focus:border-violet-400/50"
            >
              <option value="BOTH">Both ways</option>
              <option value="FORWARD">Forward</option>
              <option value="BACKWARD">Backward</option>
            </select>
            <select
              value={controller.traceDepth}
              onChange={event => controller.setTraceDepth(Number(event.target.value))}
              className="h-9 rounded-lg border border-white/10 bg-[#080912] px-3 text-xs text-[#dcd5ef] outline-none transition focus:border-violet-400/50"
            >
              {[1, 2, 3, 4].map(depth => (
                <option key={depth} value={depth}>Depth {depth}</option>
              ))}
            </select>
          </div>
          <button
            type="button"
            onClick={() => controller.startTraceFromNode(node)}
            disabled={controller.loadingGraph}
            className="flex h-9 w-full items-center justify-center gap-2 rounded-lg bg-violet-600 text-xs font-bold text-white transition hover:bg-violet-500 disabled:opacity-50"
          >
            <Play className="h-3.5 w-3.5" />
            Trace from this node
          </button>
          <div className="text-[11px] leading-4 text-[#77718f]">
            After a trace root is chosen, direction, depth, and filters refresh automatically.
          </div>
        </section>
      </div>
    </aside>
  )
}
