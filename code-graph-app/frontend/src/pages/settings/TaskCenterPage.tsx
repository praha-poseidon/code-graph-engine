import { useCallback, useEffect, useMemo, useState } from 'react'
import {
  AlertCircle, Ban, CheckCircle2, ChevronDown, ChevronUp, CircleX,
  Clock3, Loader2, RefreshCw, RotateCcw, TimerReset,
} from 'lucide-react'
import {
  cancelAnalysisTask, fetchRepositoryOptions, fetchTaskEvents, fetchTasks,
  type AnalysisTask, type AnalysisTaskEvent, type AnalysisTaskEventStatus,
  type AnalysisTaskStatus, type RepositoryOption,
} from '../../api/analysisApi'
import { cn } from '../../lib/utils'

const statusMeta: Record<AnalysisTaskStatus, { label: string; icon: typeof Clock3; color: string }> = {
  QUEUED: { label: '排队中', icon: Clock3, color: 'border-amber-400/20 bg-amber-400/10 text-amber-300' },
  RUNNING: { label: '运行中', icon: Loader2, color: 'border-violet-400/25 bg-violet-500/15 text-violet-200' },
  SUCCEEDED: { label: '已完成', icon: CheckCircle2, color: 'border-emerald-400/20 bg-emerald-400/10 text-emerald-300' },
  FAILED: { label: '失败', icon: CircleX, color: 'border-rose-400/20 bg-rose-400/10 text-rose-300' },
  CANCELED: { label: '已取消', icon: Ban, color: 'border-white/10 bg-white/[0.04] text-[#8f88a8]' },
}

const statusFilters: Array<['ALL' | AnalysisTaskStatus, string]> = [
  ['ALL', '全部'], ['QUEUED', '排队中'], ['RUNNING', '运行中'],
  ['SUCCEEDED', '已完成'], ['FAILED', '失败'], ['CANCELED', '已取消'],
]

const eventStatusMeta: Record<AnalysisTaskEventStatus, { label: string; dot: string; text: string }> = {
  RUNNING: { label: '进行中', dot: 'bg-violet-400 animate-pulse', text: 'text-violet-200' },
  SUCCEEDED: { label: '完成', dot: 'bg-emerald-400', text: 'text-emerald-300' },
  FAILED: { label: '失败', dot: 'bg-rose-400', text: 'text-rose-300' },
  SKIPPED: { label: '跳过', dot: 'bg-[#69637c]', text: 'text-[#8f88a8]' },
  CANCELED: { label: '取消', dot: 'bg-amber-400', text: 'text-amber-300' },
  RETRYING: { label: '重试', dot: 'bg-amber-400', text: 'text-amber-300' },
}

const stageLabels: Record<string, string> = {
  QUEUED: '进入队列', CLONE: '克隆仓库', BUILD: '构建项目', DISCOVER: '扫描源码',
  SESSION_START: '创建 Session', PARSE: '解析源码',
  SESSION_CLOSE: '关闭 Session', CLEANUP: '清理现场', COMPLETE: '任务结束',
}

export default function TaskCenterPage({ repositoryId, onRepositoryChange }: {
  repositoryId: number | null
  onRepositoryChange: (repositoryId: number | null) => void
}) {
  const [tasks, setTasks] = useState<AnalysisTask[]>([])
  const [repositories, setRepositories] = useState<RepositoryOption[]>([])
  const [status, setStatus] = useState<'ALL' | AnalysisTaskStatus>('ALL')
  const [loading, setLoading] = useState(true)
  const [refreshing, setRefreshing] = useState(false)
  const [canceling, setCanceling] = useState<string | null>(null)
  const [expanded, setExpanded] = useState<string | null>(null)
  const [events, setEvents] = useState<AnalysisTaskEvent[]>([])
  const [eventsLoading, setEventsLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const load = useCallback(async (initial = false) => {
    if (initial) setLoading(true)
    else setRefreshing(true)
    try {
      const [nextTasks, nextRepositories] = await Promise.all([
        fetchTasks(repositoryId),
        fetchRepositoryOptions(),
      ])
      setTasks(nextTasks)
      setRepositories(nextRepositories)
      setError(null)
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : '任务加载失败')
    } finally {
      setLoading(false)
      setRefreshing(false)
    }
  }, [repositoryId])

  useEffect(() => {
    const initialTimer = window.setTimeout(() => void load(true), 0)
    const refreshTimer = window.setInterval(() => void load(false), 2500)
    return () => {
      window.clearTimeout(initialTimer)
      window.clearInterval(refreshTimer)
    }
  }, [load])

  useEffect(() => {
    if (!expanded) return
    let active = true
    const loadEvents = async (initial = false) => {
      if (initial) setEventsLoading(true)
      try {
        const nextEvents = await fetchTaskEvents(expanded)
        if (active) setEvents(nextEvents)
      } catch (cause) {
        if (active) setError(cause instanceof Error ? cause.message : '任务明细加载失败')
      } finally {
        if (active && initial) setEventsLoading(false)
      }
    }
    void loadEvents(true)
    const timer = window.setInterval(() => void loadEvents(false), 2500)
    return () => {
      active = false
      window.clearInterval(timer)
    }
  }, [expanded])

  const visibleTasks = useMemo(
    () => status === 'ALL' ? tasks : tasks.filter(task => task.status === status),
    [status, tasks],
  )
  const repositoryNames = useMemo(
    () => new Map(repositories.map(repository => [repository.id, repository.name])),
    [repositories],
  )
  const summary = useMemo(() => ({
    queued: tasks.filter(task => task.status === 'QUEUED').length,
    running: tasks.filter(task => task.status === 'RUNNING').length,
    failed: tasks.filter(task => task.status === 'FAILED').length,
    succeeded: tasks.filter(task => task.status === 'SUCCEEDED').length,
  }), [tasks])

  const cancel = async (task: AnalysisTask) => {
    if (!confirm(`确认取消任务 ${shortId(task.id)}？`)) return
    setCanceling(task.id)
    try {
      await cancelAnalysisTask(task.id)
      await load(false)
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : '取消任务失败')
    } finally {
      setCanceling(null)
    }
  }

  return (
    <div className="w-full space-y-6 px-6 py-7 animate-fadeIn">
      <div className="flex flex-wrap items-center justify-between gap-4">
        <div>
          <h2 className="text-lg font-semibold text-ink-900">任务中心</h2>
          <p className="mt-1 text-sm text-ink-500">查看所有仓库的解析队列、执行进度和失败原因。</p>
        </div>
        <button onClick={() => void load(false)} disabled={refreshing} className="flex h-9 items-center gap-2 rounded-lg border border-ink-200 bg-white px-3 text-xs text-ink-500 transition hover:text-ink-800 disabled:opacity-50">
          <RefreshCw className={cn('h-3.5 w-3.5', refreshing && 'animate-spin')} />刷新
        </button>
      </div>

      <div className="grid grid-cols-2 gap-3 lg:grid-cols-4">
        <Metric label="排队" value={summary.queued} tone="text-amber-300" />
        <Metric label="运行中" value={summary.running} tone="text-violet-200" />
        <Metric label="已完成" value={summary.succeeded} tone="text-emerald-300" />
        <Metric label="失败" value={summary.failed} tone="text-rose-300" />
      </div>

      <div className="flex flex-wrap items-center gap-3 rounded-xl border border-ink-200 bg-white p-3">
        <select value={repositoryId ?? ''} onChange={event => onRepositoryChange(event.target.value ? Number(event.target.value) : null)} className="h-9 min-w-52 rounded-lg border border-ink-200 px-3 text-xs outline-none">
          <option value="">全部仓库</option>
          {repositories.map(repository => <option key={repository.id} value={repository.id}>{repository.name}</option>)}
        </select>
        <div className="flex flex-wrap gap-1.5">
          {statusFilters.map(([value, label]) => (
            <button key={value} onClick={() => setStatus(value)} className={cn(
              'rounded-lg px-3 py-2 text-xs transition',
              status === value ? 'bg-violet-500/20 text-violet-200' : 'text-ink-400 hover:bg-ink-50 hover:text-ink-800',
            )}>{label}</button>
          ))}
        </div>
      </div>

      {error && <ErrorBanner message={error} />}

      {loading ? (
        <div className="grid place-items-center py-24"><Loader2 className="h-6 w-6 animate-spin text-violet-300" /></div>
      ) : visibleTasks.length === 0 ? (
        <div className="rounded-2xl border border-dashed border-ink-200 py-20 text-center text-sm text-ink-400">当前筛选条件下没有任务</div>
      ) : (
        <div className="space-y-3">
          {visibleTasks.map(task => {
            const meta = statusMeta[task.status]
            const StatusIcon = meta.icon
            const progress = task.progressTotal > 0
              ? Math.round(task.progressCurrent / task.progressTotal * 100)
              : task.status === 'RUNNING' ? 6 : 0
            const open = expanded === task.id
            const cancelable = task.status === 'QUEUED' || task.status === 'RUNNING'
            return (
              <article key={task.id} className="overflow-hidden rounded-xl border border-ink-200 bg-white shadow-card">
                <div className="flex flex-wrap items-start gap-4 px-5 py-4">
                  <span className={cn('flex shrink-0 items-center gap-1.5 rounded-lg border px-2.5 py-1.5 text-[11px] font-medium', meta.color)}>
                    <StatusIcon className={cn('h-3.5 w-3.5', task.status === 'RUNNING' && 'animate-spin')} />{meta.label}
                  </span>
                  <div className="min-w-0 flex-1">
                    <div className="flex flex-wrap items-center gap-x-3 gap-y-1">
                      <span className="text-sm font-semibold text-ink-900">{repositoryNames.get(task.repositoryId) ?? `仓库 #${task.repositoryId}`}</span>
                      <span title={task.id} className="font-mono text-[11px] text-ink-400">{shortId(task.id)}</span>
                      {task.cancelRequested && task.status === 'RUNNING' && <span className="text-[11px] text-amber-300">正在取消</span>}
                    </div>
                    <p className="mt-1 truncate text-xs text-ink-500">{task.message || '等待状态更新'}</p>
                    {(task.status === 'RUNNING' || task.progressTotal > 0) && (
                      <div className="mt-3 max-w-2xl">
                        <div className="mb-1 flex justify-between text-[10px] text-ink-400">
                          <span>{task.progressTotal ? `${task.progressCurrent}/${task.progressTotal} 个文件` : '准备中'}</span>
                          <span>{task.progressTotal ? `${progress}%` : ''}</span>
                        </div>
                        <div className="h-1.5 overflow-hidden rounded-full bg-white/[0.06]"><div className="h-full rounded-full bg-violet-500 transition-all" style={{ width: `${progress}%` }} /></div>
                      </div>
                    )}
                  </div>
                  <div className="flex shrink-0 items-center gap-2">
                    {cancelable && (
                      <button onClick={() => void cancel(task)} disabled={canceling === task.id || task.cancelRequested} className="flex h-8 items-center gap-1.5 rounded-lg border border-rose-400/20 px-2.5 text-[11px] text-rose-300 transition hover:bg-rose-400/10 disabled:opacity-40">
                        {canceling === task.id ? <Loader2 className="h-3.5 w-3.5 animate-spin" /> : <Ban className="h-3.5 w-3.5" />}取消
                      </button>
                    )}
                    <button onClick={() => {
                      setEvents([])
                      setEventsLoading(!open)
                      setExpanded(open ? null : task.id)
                    }} title="任务详情" className="grid h-8 w-8 place-items-center rounded-lg text-ink-400 transition hover:bg-ink-50 hover:text-ink-800">
                      {open ? <ChevronUp className="h-4 w-4" /> : <ChevronDown className="h-4 w-4" />}
                    </button>
                  </div>
                </div>
                {open && (
                  <div className="grid gap-4 border-t border-ink-100 bg-white/[0.015] px-5 py-4 text-xs md:grid-cols-2 xl:grid-cols-4">
                    <div className="md:col-span-2 xl:col-span-4">
                      <p className="mb-3 text-[10px] uppercase tracking-wide text-ink-400">执行明细</p>
                      {eventsLoading ? (
                        <div className="flex items-center gap-2 py-3 text-ink-400"><Loader2 className="h-3.5 w-3.5 animate-spin" />正在加载</div>
                      ) : events.length === 0 ? (
                        <div className="rounded-lg border border-dashed border-ink-200 px-3 py-4 text-center text-ink-400">该任务还没有执行步骤</div>
                      ) : (
                        <div className="space-y-0">
                          {events.map((event, index) => <TaskEventRow key={event.id} event={event} last={index === events.length - 1} />)}
                        </div>
                      )}
                    </div>
                    <Detail label="创建时间" value={formatTime(task.createdAt)} />
                    <Detail label="开始时间" value={formatTime(task.startedAt)} />
                    <Detail label="完成时间" value={formatTime(task.finishedAt)} />
                    <Detail label="重试次数" value={`${task.attemptCount} / ${task.maxAttempts}`} icon={<RotateCcw className="h-3 w-3" />} />
                    <Detail label="执行 Worker" value={task.leaseOwner || '—'} />
                    <Detail label="Worker 心跳" value={formatTime(task.heartbeatAt)} icon={<TimerReset className="h-3 w-3" />} />
                    <Detail label="下次重试" value={formatTime(task.nextAttemptAt)} />
                    <Detail label="任务 ID" value={task.id} mono />
                    {task.errorDetails && (
                      <div className="md:col-span-2 xl:col-span-4">
                        <p className="mb-1.5 text-[10px] uppercase tracking-wide text-ink-400">错误详情</p>
                        <pre className="max-h-52 overflow-auto whitespace-pre-wrap rounded-lg border border-rose-400/15 bg-rose-400/[0.06] p-3 font-mono text-[11px] leading-5 text-rose-200">{task.errorDetails}</pre>
                      </div>
                    )}
                  </div>
                )}
              </article>
            )
          })}
        </div>
      )}
    </div>
  )
}

function TaskEventRow({ event, last }: { event: AnalysisTaskEvent; last: boolean }) {
  const meta = eventStatusMeta[event.status] ?? eventStatusMeta.RUNNING
  return (
    <div className="grid grid-cols-[18px_minmax(0,1fr)_auto] gap-x-3">
      <div className="flex flex-col items-center">
        <span className={cn('mt-1.5 h-2.5 w-2.5 shrink-0 rounded-full ring-4 ring-[#10111b]', meta.dot)} />
        {!last && <span className="min-h-7 w-px flex-1 bg-white/[0.09]" />}
      </div>
      <div className={cn('min-w-0 pb-4', last && 'pb-0')}>
        <div className="flex flex-wrap items-center gap-2">
          <span className="font-medium text-ink-800">{stageLabels[event.stage] ?? event.stage}</span>
          <span className={cn('text-[10px]', meta.text)}>{meta.label}</span>
        </div>
        <p className="mt-0.5 break-all text-[11px] leading-5 text-ink-500">{event.message || '—'}</p>
        {event.details && <pre className="mt-2 max-h-32 overflow-auto whitespace-pre-wrap rounded-md bg-rose-400/[0.06] p-2 font-mono text-[10px] leading-4 text-rose-200">{event.details}</pre>}
      </div>
      <div className="whitespace-nowrap pt-0.5 text-[10px] text-ink-400" title={formatTime(event.startedAt)}>
        {eventDuration(event)}
      </div>
    </div>
  )
}

function Metric({ label, value, tone }: { label: string; value: number; tone: string }) {
  return <div className="rounded-xl border border-ink-200 bg-white px-4 py-3"><p className="text-xs text-ink-400">{label}</p><p className={cn('mt-1 text-xl font-semibold', tone)}>{value}</p></div>
}

function ErrorBanner({ message }: { message: string }) {
  return <div className="flex items-center gap-2 rounded-lg border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-500"><AlertCircle className="h-4 w-4" />{message}</div>
}

function Detail({ label, value, icon, mono = false }: { label: string; value: string; icon?: React.ReactNode; mono?: boolean }) {
  return <div className="min-w-0"><p className="text-[10px] uppercase tracking-wide text-ink-400">{label}</p><p className={cn('mt-1 flex items-center gap-1.5 truncate text-ink-600', mono && 'font-mono text-[11px]')} title={value}>{icon}{value}</p></div>
}

function shortId(id: string) {
  return id.slice(0, 8)
}

function formatTime(value?: string | null) {
  if (!value) return '—'
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? value : date.toLocaleString()
}

function eventDuration(event: AnalysisTaskEvent) {
  if (!event.finishedAt) return '进行中'
  const start = new Date(event.startedAt).getTime()
  const finish = new Date(event.finishedAt).getTime()
  if (Number.isNaN(start) || Number.isNaN(finish)) return '—'
  const milliseconds = Math.max(0, finish - start)
  if (milliseconds < 1000) return `${milliseconds} ms`
  if (milliseconds < 60_000) return `${(milliseconds / 1000).toFixed(1)} 秒`
  return `${Math.floor(milliseconds / 60_000)} 分 ${Math.round(milliseconds % 60_000 / 1000)} 秒`
}
