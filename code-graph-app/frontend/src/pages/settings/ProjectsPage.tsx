import { useCallback, useEffect, useMemo, useState } from 'react'
import {
  AlertCircle, CheckCircle2, Clock, FolderGit2, KeyRound, Loader2,
  ListTodo, Pencil, Play, Plus, Trash2, X, XCircle,
} from 'lucide-react'
import { apiGet, apiPost, request } from '../../lib/http'
import { cn } from '../../lib/utils'

interface Project {
  id: number
  name: string
  gitRepoUrl: string
  gitBranch: string
  languages: string[]
  authType: 'NONE' | 'SSH' | 'ACCESS_TOKEN'
  hasAccessToken: boolean
  hasSshPrivateKey: boolean
  endpointRuleSources: string[]
  status: 'idle' | 'analyzing' | 'done' | 'failed'
  progressCurrent: number
  progressTotal: number
  statusMessage?: string
  lastAnalyzedAt?: string
  latestTaskId?: string
}

const LANGUAGES = [
  ['java', 'Java'], ['go', 'Go'], ['javascript', 'JavaScript'], ['typescript', 'TypeScript'],
  ['python', 'Python'], ['php', 'PHP'], ['kotlin', 'Kotlin'], ['swift', 'Swift'],
] as const

const statusConfig = {
  idle: { label: '待分析', icon: Clock, color: 'text-ink-500 bg-ink-100' },
  analyzing: { label: '分析中', icon: Loader2, color: 'text-brand-600 bg-brand-50' },
  done: { label: '已完成', icon: CheckCircle2, color: 'text-emerald-600 bg-emerald-50' },
  failed: { label: '失败', icon: XCircle, color: 'text-red-600 bg-red-50' },
}

type FormState = {
  gitRepoUrl: string
  gitBranch: string
  languages: string[]
  authType: 'NONE' | 'SSH' | 'ACCESS_TOKEN'
  accessToken: string
  sshPrivateKey: string
  sshPassphrase: string
  endpointRules: string
}

const emptyForm: FormState = {
  gitRepoUrl: '',
  gitBranch: '',
  languages: ['java'],
  authType: 'NONE',
  accessToken: '',
  sshPrivateKey: '',
  sshPassphrase: '',
  endpointRules: '',
}

const RULE_SEPARATOR = '\n\n--- codegraph-rule ---\n\n'

export default function ProjectsPage({ onOpenTasks }: { onOpenTasks?: (repositoryId: number) => void }) {
  const [projects, setProjects] = useState<Project[]>([])
  const [loading, setLoading] = useState(true)
  const [showForm, setShowForm] = useState(false)
  const [editing, setEditing] = useState<Project | null>(null)
  const [form, setForm] = useState<FormState>(emptyForm)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const load = useCallback(async (initial = false) => {
    if (initial) setLoading(true)
    try {
      const response = await apiGet<{ code: number; message: string; data: Project[] }>('/api/config/projects')
      if (response.code !== 200) throw new Error(response.message)
      setProjects(response.data ?? [])
    } catch (cause) {
      if (initial) setError(cause instanceof Error ? cause.message : '仓库配置加载失败')
    } finally {
      if (initial) setLoading(false)
    }
  }, [])

  useEffect(() => {
    const initialTimer = window.setTimeout(() => void load(true), 0)
    const refreshTimer = window.setInterval(() => void load(false), 2500)
    return () => {
      window.clearTimeout(initialTimer)
      window.clearInterval(refreshTimer)
    }
  }, [load])

  const openCreate = () => {
    setEditing(null)
    setForm(emptyForm)
    setError(null)
    setShowForm(true)
  }

  const openEdit = (project: Project) => {
    setEditing(project)
    setForm({
      gitRepoUrl: project.gitRepoUrl,
      gitBranch: project.gitBranch,
      languages: project.languages,
      authType: project.authType,
      accessToken: '',
      sshPrivateKey: '',
      sshPassphrase: '',
      endpointRules: project.endpointRuleSources.join(RULE_SEPARATOR),
    })
    setError(null)
    setShowForm(true)
  }

  const toggleLanguage = (language: string) => {
    setForm(current => ({
      ...current,
      languages: current.languages.includes(language)
        ? current.languages.filter(value => value !== language)
        : [...current.languages, language],
    }))
  }

  const handleSave = async () => {
    if (!form.gitRepoUrl.trim()) return setError('仓库地址必填')
    if (form.languages.length === 0) return setError('至少选择一种语言')
    const body = {
      gitRepoUrl: form.gitRepoUrl,
      gitBranch: form.gitBranch,
      languages: form.languages,
      authType: form.authType,
      accessToken: form.accessToken,
      sshPrivateKey: form.sshPrivateKey,
      sshPassphrase: form.sshPassphrase,
      endpointRuleSources: form.endpointRules.trim()
        ? form.endpointRules.split(RULE_SEPARATOR).map(rule => rule.trim()).filter(Boolean)
        : [],
    }
    setSaving(true)
    setError(null)
    try {
      const response = editing
        ? await request<{ code: number; message: string }>(`/api/config/projects/${editing.id}`, { method: 'PUT', body })
        : await apiPost<{ code: number; message: string }>('/api/config/projects', body)
      if (response.code !== 200) throw new Error(response.message)
      setShowForm(false)
      await load(false)
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : '保存失败')
    } finally {
      setSaving(false)
    }
  }

  const handleDelete = async (project: Project) => {
    if (!confirm(`确认删除仓库「${project.name}」及其任务记录？`)) return
    await request(`/api/config/projects/${project.id}`, { method: 'DELETE' })
    await load(false)
  }

  const handleAnalyze = async (project: Project) => {
    const response = await apiPost<{ code: number; message: string }>(`/api/config/projects/${project.id}/analyze`)
    if (response.code !== 200) setError(response.message)
    await load(false)
  }

  const summary = useMemo(() => ({
    total: projects.length,
    running: projects.filter(project => project.status === 'analyzing').length,
    failed: projects.filter(project => project.status === 'failed').length,
  }), [projects])

  return (
    <div className="w-full space-y-6 px-6 py-7 animate-fadeIn">
      <div className="flex flex-wrap items-center justify-between gap-4">
        <div>
          <h2 className="text-lg font-semibold text-ink-900">仓库</h2>
          <p className="mt-1 text-sm text-ink-500">仓库、访问凭证、语言和端点规则在这里一次配置完成。</p>
        </div>
        <button onClick={openCreate} className="flex items-center gap-2 rounded-lg bg-brand-600 px-4 py-2.5 text-sm font-medium text-white transition hover:bg-brand-700">
          <Plus className="h-4 w-4" /> 添加仓库
        </button>
      </div>

      <div className="grid grid-cols-3 gap-3">
        <Metric label="仓库" value={summary.total} />
        <Metric label="正在分析" value={summary.running} accent="text-violet-300" />
        <Metric label="失败" value={summary.failed} accent={summary.failed ? 'text-rose-300' : undefined} />
      </div>

      {error && !showForm && (
        <div className="flex items-center gap-2 rounded-lg border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-600">
          <AlertCircle className="h-4 w-4" /> {error}
        </div>
      )}

      {loading ? (
        <div className="flex justify-center py-20"><Loader2 className="h-6 w-6 animate-spin text-ink-400" /></div>
      ) : projects.length === 0 ? (
        <button onClick={openCreate} className="flex w-full flex-col items-center rounded-2xl border border-dashed border-ink-200 py-20 text-center transition hover:border-violet-400/40 hover:bg-white/[0.02]">
          <span className="mb-3 grid h-12 w-12 place-items-center rounded-2xl bg-ink-100"><FolderGit2 className="h-6 w-6 text-ink-400" /></span>
          <span className="text-sm font-medium text-ink-700">添加第一个代码仓库</span>
          <span className="mt-1 text-xs text-ink-400">项目名称会根据仓库地址自动识别</span>
        </button>
      ) : (
        <div className="space-y-3">
          {projects.map(project => <RepositoryCard key={project.id} project={project} onAnalyze={handleAnalyze} onEdit={openEdit} onDelete={handleDelete} onOpenTasks={onOpenTasks} />)}
        </div>
      )}

      {showForm && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/55 p-4 backdrop-blur-sm">
          <div className="w-full max-w-2xl overflow-hidden rounded-2xl border border-ink-200 bg-white shadow-2xl">
            <div className="flex items-center justify-between border-b border-ink-100 px-6 py-4">
              <div>
                <h3 className="font-semibold text-ink-900">{editing ? '编辑仓库' : '添加仓库'}</h3>
                <p className="mt-0.5 text-xs text-ink-400">项目名称将从仓库地址自动生成</p>
              </div>
              <button onClick={() => setShowForm(false)} className="text-ink-400 hover:text-ink-600"><X className="h-5 w-5" /></button>
            </div>
            <div className="max-h-[72vh] space-y-5 overflow-y-auto px-6 py-5">
              {error && <div className="flex items-center gap-2 rounded-lg border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-600"><AlertCircle className="h-4 w-4" />{error}</div>}

              <Field label="仓库地址 *">
                <input value={form.gitRepoUrl} onChange={event => setForm(current => ({ ...current, gitRepoUrl: event.target.value }))} placeholder="git@github.com:org/repository.git" className={cn(input(), 'font-mono text-xs')} />
              </Field>
              <Field label="分支">
                <input value={form.gitBranch} onChange={event => setForm(current => ({ ...current, gitBranch: event.target.value }))} placeholder="留空时使用仓库默认分支" className={input()} />
              </Field>

              <Field label="源码语言 *">
                <div className="flex flex-wrap gap-2">
                  {LANGUAGES.map(([value, label]) => (
                    <button key={value} type="button" onClick={() => toggleLanguage(value)} className={cn(
                      'rounded-lg border px-3 py-2 text-xs font-medium transition',
                      form.languages.includes(value) ? 'border-violet-400/60 bg-violet-500/15 text-violet-200' : 'border-ink-200 text-ink-500 hover:border-violet-400/30 hover:text-ink-800',
                    )}>{label}</button>
                  ))}
                </div>
              </Field>

              <Field label="仓库认证">
                <div className="grid grid-cols-3 gap-2">
                  {([['NONE', '公开仓库'], ['SSH', 'SSH 私钥'], ['ACCESS_TOKEN', 'Access Token']] as const).map(([value, label]) => (
                    <button key={value} type="button" onClick={() => setForm(current => ({ ...current, authType: value }))} className={cn(
                      'flex items-center justify-center gap-2 rounded-lg border px-3 py-2.5 text-xs transition',
                      form.authType === value ? 'border-violet-400/60 bg-violet-500/15 text-violet-200' : 'border-ink-200 text-ink-500',
                    )}><KeyRound className="h-3.5 w-3.5" />{label}</button>
                  ))}
                </div>
              </Field>

              {form.authType === 'ACCESS_TOKEN' && (
                <Field label="Access Token">
                  <input type="password" value={form.accessToken} onChange={event => setForm(current => ({ ...current, accessToken: event.target.value }))} placeholder={editing?.hasAccessToken ? '已保存，留空表示不修改' : '输入仓库访问令牌'} className={input()} />
                </Field>
              )}
              {form.authType === 'SSH' && (
                <div className="space-y-3 rounded-xl border border-ink-200 p-4">
                  <Field label="SSH 私钥">
                    <textarea value={form.sshPrivateKey} onChange={event => setForm(current => ({ ...current, sshPrivateKey: event.target.value }))} rows={6} placeholder={editing?.hasSshPrivateKey ? '已保存，留空表示不修改' : '-----BEGIN OPENSSH PRIVATE KEY-----'} className={cn(input(), 'resize-none font-mono text-xs')} />
                  </Field>
                  <Field label="私钥密码（可选）">
                    <input type="password" value={form.sshPassphrase} onChange={event => setForm(current => ({ ...current, sshPassphrase: event.target.value }))} placeholder="没有密码可留空" className={input()} />
                  </Field>
                </div>
              )}

              <Field label="端点规则（可选）">
                <textarea value={form.endpointRules} onChange={event => setForm(current => ({ ...current, endpointRules: event.target.value }))} rows={8} placeholder="直接粘贴 SER/EPR 规则；多条规则使用 --- codegraph-rule --- 分隔" className={cn(input(), 'resize-y font-mono text-xs')} />
              </Field>
            </div>
            <div className="flex justify-end gap-2 border-t border-ink-100 px-6 py-4">
              <button onClick={() => setShowForm(false)} className="rounded-lg px-4 py-2 text-sm text-ink-600 hover:bg-ink-50">取消</button>
              <button onClick={handleSave} disabled={saving} className="flex items-center gap-2 rounded-lg bg-brand-600 px-4 py-2 text-sm font-medium text-white hover:bg-brand-700 disabled:opacity-50">
                {saving && <Loader2 className="h-4 w-4 animate-spin" />}{saving ? '保存中…' : '保存仓库'}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}

function RepositoryCard({ project, onAnalyze, onEdit, onDelete, onOpenTasks }: {
  project: Project
  onAnalyze: (project: Project) => void
  onEdit: (project: Project) => void
  onDelete: (project: Project) => void
  onOpenTasks?: (repositoryId: number) => void
}) {
  const config = statusConfig[project.status]
  const StatusIcon = config.icon
  const progress = project.progressTotal > 0 ? Math.round(project.progressCurrent / project.progressTotal * 100) : 0
  return (
    <article className="rounded-xl border border-ink-200 bg-white px-5 py-4 shadow-card">
      <div className="flex items-start gap-4">
        <span className="grid h-10 w-10 shrink-0 place-items-center rounded-xl bg-brand-50"><FolderGit2 className="h-5 w-5 text-brand-600" /></span>
        <div className="min-w-0 flex-1">
          <div className="flex flex-wrap items-center gap-2">
            <span className="text-sm font-semibold text-ink-900">{project.name}</span>
            <span className={cn('flex items-center gap-1 rounded px-2 py-0.5 text-[11px] font-medium', config.color)}>
              <StatusIcon className={cn('h-3 w-3', project.status === 'analyzing' && 'animate-spin')} />{config.label}
            </span>
            {project.languages.map(language => <span key={language} className="rounded bg-white/[0.05] px-2 py-0.5 text-[10px] uppercase text-ink-400">{language}</span>)}
          </div>
          <p className="mt-1 truncate font-mono text-xs text-ink-400">{project.gitRepoUrl}</p>
          <div className="mt-2 flex flex-wrap gap-x-4 gap-y-1 text-[11px] text-ink-400">
            <span>分支：{project.gitBranch || '默认分支'}</span>
            <span>认证：{project.authType === 'NONE' ? '公开仓库' : project.authType}</span>
            <span>端点规则：{project.endpointRuleSources.length}</span>
            {project.lastAnalyzedAt && <span>完成时间：{new Date(project.lastAnalyzedAt).toLocaleString()}</span>}
          </div>
          {project.status === 'analyzing' && (
            <div className="mt-3 max-w-xl">
              <div className="mb-1 flex justify-between text-[11px] text-ink-400"><span className="truncate">{project.statusMessage || '等待执行'}</span><span>{project.progressTotal ? `${project.progressCurrent}/${project.progressTotal}` : '准备中'}</span></div>
              <div className="h-1.5 overflow-hidden rounded-full bg-white/[0.06]"><div className="h-full rounded-full bg-violet-500 transition-all" style={{ width: `${Math.max(progress, project.progressTotal ? 2 : 10)}%` }} /></div>
            </div>
          )}
          {project.status === 'failed' && project.statusMessage && <p className="mt-2 text-xs text-red-500">{project.statusMessage}</p>}
        </div>
        <div className="flex shrink-0 items-center gap-1">
          {onOpenTasks && <button onClick={() => onOpenTasks(project.id)} title="查看任务" className="grid h-8 w-8 place-items-center rounded-lg text-violet-300 hover:bg-violet-500/10"><ListTodo className="h-4 w-4" /></button>}
          <button onClick={() => onAnalyze(project)} disabled={project.status === 'analyzing'} title="开始分析" className="grid h-8 w-8 place-items-center rounded-lg text-emerald-600 hover:bg-emerald-50 disabled:opacity-40"><Play className="h-4 w-4" /></button>
          <button onClick={() => onEdit(project)} title="编辑" className="grid h-8 w-8 place-items-center rounded-lg text-ink-500 hover:bg-ink-50"><Pencil className="h-4 w-4" /></button>
          <button onClick={() => onDelete(project)} title="删除" className="grid h-8 w-8 place-items-center rounded-lg text-red-500 hover:bg-red-50"><Trash2 className="h-4 w-4" /></button>
        </div>
      </div>
    </article>
  )
}

function Metric({ label, value, accent }: { label: string; value: number; accent?: string }) {
  return <div className="rounded-xl border border-ink-200 bg-white px-4 py-3"><p className="text-xs text-ink-400">{label}</p><p className={cn('mt-1 text-xl font-semibold text-ink-900', accent)}>{value}</p></div>
}

function Field({ label, children }: { label: string; children: React.ReactNode }) {
  return <div className="space-y-1.5"><label className="text-xs font-medium text-ink-600">{label}</label>{children}</div>
}

function input() {
  return 'w-full rounded-lg border border-ink-200 bg-white px-3 py-2.5 text-sm text-ink-900 outline-none transition focus:border-brand-400'
}
