import { useState, useEffect } from 'react'
import {
  Plus, Pencil, Trash2, Play, FolderGit2,
  Loader2, CheckCircle2, XCircle, Clock, AlertCircle, X
} from 'lucide-react'
import { cn } from '../../lib/utils'
import { apiGet, apiPost, request } from '../../lib/http'

interface Project {
  id: number
  name: string
  gitRepoUrl: string
  gitBranch: string
  authType: 'SSH' | 'ACCESS_TOKEN'
  sshKeyId?: string
  jdkVersion: string
  buildTool: string
  buildToolVersion?: string
  autoTrigger: boolean
  status: 'idle' | 'analyzing' | 'done' | 'failed'
  lastAnalyzedAt?: string
}

interface SshKey { keyId: string; description?: string }
interface EndpointRule { ruleId: string; name: string; isBuiltin: boolean; enabled: boolean }

const statusConfig = {
  idle:      { label: '待分析', icon: Clock,         color: 'text-ink-500 bg-ink-100' },
  analyzing: { label: '分析中', icon: Loader2,       color: 'text-brand-600 bg-brand-50' },
  done:      { label: '已完成', icon: CheckCircle2,  color: 'text-emerald-600 bg-emerald-50' },
  failed:    { label: '失败',   icon: XCircle,       color: 'text-red-600 bg-red-50' },
}

const JDK_VERSIONS = ['8', '11', '17', '21']
const BUILD_TOOLS  = { maven: ['3.6', '3.8', '3.9'], gradle: ['7.6', '8.0', '8.5'] }

const emptyForm = {
  name: '', gitRepoUrl: '', gitBranch: 'master',
  authType: 'SSH' as 'SSH' | 'ACCESS_TOKEN',
  sshKeyId: '', accessToken: '',
  jdkVersion: '17', buildTool: 'maven', buildToolVersion: '3.9',
  autoTrigger: false,
}

export default function ProjectsPage() {
  const [projects,       setProjects]       = useState<Project[]>([])
  const [sshKeys,        setSshKeys]        = useState<SshKey[]>([])
  const [endpointRules,  setEndpointRules]  = useState<EndpointRule[]>([])
  const [selectedRuleIds, setSelectedRuleIds] = useState<string[]>([])
  const [loading,        setLoading]        = useState(true)
  const [showForm,       setShowForm]       = useState(false)
  const [editing,        setEditing]        = useState<Project | null>(null)
  const [form,           setForm]           = useState(emptyForm)
  const [saving,         setSaving]         = useState(false)
  const [error,          setError]          = useState<string | null>(null)

  const load = async () => {
    setLoading(true)
    const [p, k, r] = await Promise.allSettled([
      apiGet<{ data: Project[] }>('/api/config/projects'),
      apiGet<{ data: SshKey[] }>('/api/config/ssh-keys'),
      apiGet<{ data: EndpointRule[] }>('/api/config/endpoint-rules'),
    ])
    if (p.status === 'fulfilled') setProjects(p.value.data ?? [])
    if (k.status === 'fulfilled') setSshKeys(k.value.data ?? [])
    if (r.status === 'fulfilled') setEndpointRules(r.value.data ?? [])
    setLoading(false)
  }

  useEffect(() => { load() }, [])

  const openCreate = () => {
    setEditing(null)
    setForm(emptyForm)
    setSelectedRuleIds([])
    setError(null)
    setShowForm(true)
  }

  const openEdit = async (p: Project) => {
    setEditing(p)
    setForm({
      name: p.name, gitRepoUrl: p.gitRepoUrl, gitBranch: p.gitBranch,
      authType: p.authType, sshKeyId: p.sshKeyId ?? '', accessToken: '',
      jdkVersion: p.jdkVersion, buildTool: p.buildTool,
      buildToolVersion: p.buildToolVersion ?? '3.9', autoTrigger: p.autoTrigger,
    })
    setError(null)
    setShowForm(true)
    try {
      const res = await apiGet<{ data: string[] }>(`/api/config/endpoint-rules/project/${p.id}`)
      setSelectedRuleIds(res.data ?? [])
    } catch {
      setSelectedRuleIds([])
    }
  }

  const toggleRule = (ruleId: string) => {
    setSelectedRuleIds(prev =>
      prev.includes(ruleId) ? prev.filter(r => r !== ruleId) : [...prev, ruleId]
    )
  }

  const handleSave = async () => {
    if (!form.name || !form.gitRepoUrl) { setError('项目名称和仓库地址必填'); return }
    setSaving(true); setError(null)
    try {
      let projectId: number
      if (editing) {
        await request(`/api/config/projects/${editing.id}`, { method: 'PUT', body: form })
        projectId = editing.id
      } else {
        const res = await apiPost<{ data: { id: number } }>('/api/config/projects', form)
        projectId = res.data.id
      }
      await request(`/api/config/endpoint-rules/project/${projectId}`, {
        method: 'PUT', body: selectedRuleIds,
      })
      setShowForm(false)
      load()
    } catch (e) {
      setError(e instanceof Error ? e.message : '保存失败')
    } finally { setSaving(false) }
  }

  const handleDelete = async (id: number) => {
    if (!confirm('确认删除该仓库配置？')) return
    await request(`/api/config/projects/${id}`, { method: 'DELETE' })
    load()
  }

  const handleAnalyze = async (id: number) => {
    await apiPost(`/api/config/projects/${id}/analyze`)
    load()
  }

  const buildToolVersions = BUILD_TOOLS[form.buildTool as keyof typeof BUILD_TOOLS] ?? []

  return (
    <div className="max-w-5xl mx-auto px-6 py-6 space-y-5 animate-fadeIn">
      {/* Header */}
      <div className="flex items-center justify-between">
        <div>
          <h2 className="text-base font-semibold text-ink-900">仓库管理</h2>
          <p className="text-sm text-ink-500 mt-0.5">注册 Git 仓库并配置构建环境，支持自动触发重分析</p>
        </div>
        <button
          onClick={openCreate}
          className="flex items-center gap-1.5 px-3 py-2 rounded-lg bg-brand-600 text-white text-sm font-medium hover:bg-brand-700 transition-colors"
        >
          <Plus className="w-4 h-4" /> 添加仓库
        </button>
      </div>

      {/* List */}
      {loading ? (
        <div className="flex justify-center py-16">
          <Loader2 className="w-6 h-6 animate-spin text-ink-400" />
        </div>
      ) : projects.length === 0 ? (
        <div className="flex flex-col items-center py-20 text-center">
          <div className="w-12 h-12 rounded-2xl bg-ink-100 flex items-center justify-center mb-3">
            <FolderGit2 className="w-6 h-6 text-ink-400" />
          </div>
          <p className="text-sm text-ink-500">还没有仓库，点击右上角添加</p>
        </div>
      ) : (
        <div className="space-y-3">
          {projects.map((p) => {
            const st = statusConfig[p.status] ?? statusConfig.idle
            const StatusIcon = st.icon
            return (
              <div key={p.id} className="bg-white border border-ink-200 rounded-xl shadow-card px-5 py-4 flex items-center gap-4">
                <div className="w-9 h-9 rounded-lg bg-brand-50 flex items-center justify-center shrink-0">
                  <FolderGit2 className="w-5 h-5 text-brand-600" />
                </div>
                <div className="flex-1 min-w-0 space-y-1">
                  <div className="flex items-center gap-2 flex-wrap">
                    <span className="font-semibold text-sm text-ink-900">{p.name}</span>
                    <span className={cn('flex items-center gap-1 text-[11px] px-1.5 py-0.5 rounded font-medium', st.color)}>
                      <StatusIcon className={cn('w-3 h-3', p.status === 'analyzing' && 'animate-spin')} />
                      {st.label}
                    </span>
                    {p.autoTrigger && (
                      <span className="text-[11px] px-1.5 py-0.5 rounded bg-violet-50 text-violet-600 font-medium">自动触发</span>
                    )}
                  </div>
                  <p className="text-xs text-ink-400 font-mono truncate">{p.gitRepoUrl}</p>
                  <div className="flex items-center gap-3 text-[11px] text-ink-400">
                    <span>分支: {p.gitBranch}</span>
                    <span>JDK {p.jdkVersion}</span>
                    <span>{p.buildTool} {p.buildToolVersion}</span>
                    <span>{p.authType === 'SSH' ? `SSH · ${p.sshKeyId}` : 'Access Token'}</span>
                    {p.lastAnalyzedAt && <span>上次分析: {new Date(p.lastAnalyzedAt).toLocaleString()}</span>}
                  </div>
                </div>
                <div className="flex items-center gap-1.5 shrink-0">
                  <button
                    onClick={() => handleAnalyze(p.id)}
                    disabled={p.status === 'analyzing'}
                    title="触发分析"
                    className="w-8 h-8 rounded-lg flex items-center justify-center text-emerald-600 hover:bg-emerald-50 disabled:opacity-40 transition-colors"
                  >
                    <Play className="w-4 h-4" />
                  </button>
                  <button onClick={() => openEdit(p)} title="编辑" className="w-8 h-8 rounded-lg flex items-center justify-center text-ink-500 hover:bg-ink-50 transition-colors">
                    <Pencil className="w-4 h-4" />
                  </button>
                  <button onClick={() => handleDelete(p.id)} title="删除" className="w-8 h-8 rounded-lg flex items-center justify-center text-red-500 hover:bg-red-50 transition-colors">
                    <Trash2 className="w-4 h-4" />
                  </button>
                </div>
              </div>
            )
          })}
        </div>
      )}

      {/* Modal */}
      {showForm && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40">
          <div className="bg-white rounded-2xl shadow-2xl w-full max-w-lg mx-4 overflow-hidden">
            <div className="flex items-center justify-between px-6 py-4 border-b border-ink-100">
              <h3 className="font-semibold text-ink-900">{editing ? '编辑仓库' : '添加仓库'}</h3>
              <button onClick={() => setShowForm(false)} className="text-ink-400 hover:text-ink-600">
                <X className="w-5 h-5" />
              </button>
            </div>
            <div className="px-6 py-5 space-y-4 max-h-[70vh] overflow-y-auto">
              {error && (
                <div className="flex items-center gap-2 text-sm text-red-600 bg-red-50 border border-red-200 rounded-lg px-3 py-2">
                  <AlertCircle className="w-4 h-4 shrink-0" /> {error}
                </div>
              )}

              <Field label="项目名称 *">
                <input value={form.name} onChange={e => setForm(f => ({ ...f, name: e.target.value }))}
                  placeholder="my-service" className={input()} />
              </Field>

              <Field label="仓库地址 *">
                <input value={form.gitRepoUrl} onChange={e => setForm(f => ({ ...f, gitRepoUrl: e.target.value }))}
                  placeholder="git@github.com:org/repo.git" className={cn(input(), 'font-mono text-xs')} />
              </Field>

              <div className="grid grid-cols-2 gap-3">
                <Field label="默认分支">
                  <input value={form.gitBranch} onChange={e => setForm(f => ({ ...f, gitBranch: e.target.value }))}
                    placeholder="master" className={input()} />
                </Field>
                <Field label="认证方式">
                  <select value={form.authType} onChange={e => setForm(f => ({ ...f, authType: e.target.value as 'SSH' | 'ACCESS_TOKEN' }))} className={input()}>
                    <option value="SSH">SSH 密钥</option>
                    <option value="ACCESS_TOKEN">Access Token</option>
                  </select>
                </Field>
              </div>

              {form.authType === 'SSH' ? (
                <Field label="SSH 密钥">
                  <select value={form.sshKeyId} onChange={e => setForm(f => ({ ...f, sshKeyId: e.target.value }))} className={input()}>
                    <option value="">-- 选择密钥 --</option>
                    {sshKeys.map(k => <option key={k.keyId} value={k.keyId}>{k.keyId}{k.description ? ` · ${k.description}` : ''}</option>)}
                  </select>
                </Field>
              ) : (
                <Field label="Access Token">
                  <input type="password" value={form.accessToken} onChange={e => setForm(f => ({ ...f, accessToken: e.target.value }))}
                    placeholder="glpat-xxxx" className={input()} />
                </Field>
              )}

              <div className="grid grid-cols-3 gap-3">
                <Field label="JDK 版本">
                  <select value={form.jdkVersion} onChange={e => setForm(f => ({ ...f, jdkVersion: e.target.value }))} className={input()}>
                    {JDK_VERSIONS.map(v => <option key={v} value={v}>JDK {v}</option>)}
                  </select>
                </Field>
                <Field label="构建工具">
                  <select value={form.buildTool} onChange={e => setForm(f => ({ ...f, buildTool: e.target.value, buildToolVersion: BUILD_TOOLS[e.target.value as keyof typeof BUILD_TOOLS]?.at(-1) ?? '' }))} className={input()}>
                    <option value="maven">Maven</option>
                    <option value="gradle">Gradle</option>
                  </select>
                </Field>
                <Field label="工具版本">
                  <select value={form.buildToolVersion} onChange={e => setForm(f => ({ ...f, buildToolVersion: e.target.value }))} className={input()}>
                    {buildToolVersions.map(v => <option key={v} value={v}>{v}</option>)}
                  </select>
                </Field>
              </div>

              <Field label="端点解析规则">
                {endpointRules.length === 0 ? (
                  <p className="text-xs text-ink-400 px-1">暂无规则，请先在「端点规则」页面添加</p>
                ) : (
                  <div className="border border-ink-200 rounded-lg divide-y divide-ink-100 max-h-36 overflow-y-auto">
                    {endpointRules.map(rule => (
                      <label key={rule.ruleId} className="flex items-center gap-2.5 px-3 py-2 cursor-pointer hover:bg-ink-50 transition-colors">
                        <input
                          type="checkbox"
                          checked={selectedRuleIds.includes(rule.ruleId)}
                          onChange={() => toggleRule(rule.ruleId)}
                          className="w-3.5 h-3.5 accent-brand-600 shrink-0"
                        />
                        <span className="text-sm text-ink-800 flex-1 min-w-0 truncate">{rule.name}</span>
                        {rule.isBuiltin && (
                          <span className="text-[10px] px-1.5 py-0.5 rounded bg-brand-50 text-brand-600 font-medium shrink-0">内置</span>
                        )}
                        {!rule.enabled && (
                          <span className="text-[10px] px-1.5 py-0.5 rounded bg-ink-100 text-ink-400 font-medium shrink-0">已禁用</span>
                        )}
                      </label>
                    ))}
                  </div>
                )}
              </Field>

              <label className="flex items-center gap-2.5 cursor-pointer">
                <input type="checkbox" checked={form.autoTrigger} onChange={e => setForm(f => ({ ...f, autoTrigger: e.target.checked }))}
                  className="w-4 h-4 accent-brand-600" />
                <span className="text-sm text-ink-700">代码变更时自动触发重分析</span>
              </label>
            </div>
            <div className="flex justify-end gap-2 px-6 py-4 border-t border-ink-100">
              <button onClick={() => setShowForm(false)} className="px-4 py-2 rounded-lg text-sm text-ink-600 hover:bg-ink-50 transition-colors">取消</button>
              <button onClick={handleSave} disabled={saving}
                className="flex items-center gap-1.5 px-4 py-2 rounded-lg bg-brand-600 text-white text-sm font-medium hover:bg-brand-700 disabled:opacity-50 transition-colors">
                {saving && <Loader2 className="w-3.5 h-3.5 animate-spin" />}
                {saving ? '保存中…' : '保存'}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}

function Field({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div className="space-y-1.5">
      <label className="text-xs font-medium text-ink-600">{label}</label>
      {children}
    </div>
  )
}

function input() {
  return 'w-full px-3 py-2 text-sm border border-ink-200 rounded-lg bg-white text-ink-900 outline-none focus:border-brand-400 transition-colors'
}
