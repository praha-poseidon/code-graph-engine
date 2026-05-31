import { ChevronLeft } from 'lucide-react'
import ProjectsPage from '../settings/ProjectsPage'
import SshKeysPage from '../settings/SshKeysPage'
import EndpointRulesPage from '../settings/EndpointRulesPage'
import { SETTINGS_TABS } from './workbench-constants'
import type { WorkbenchController } from './state/useWorkbenchState'

export default function WorkbenchSettingsScreen({ controller }: { controller: WorkbenchController }) {
  return (
    <div className="flex h-full overflow-hidden bg-[#07080f] text-[#f7f2ff]">
      <aside className="settings-theme flex w-56 shrink-0 flex-col overflow-hidden border-r border-white/[0.08] bg-[#101019]">
        <div className="border-b border-white/[0.08] px-5 py-5">
          <div className="flex items-center gap-3">
            <button
              onClick={() => controller.setMode('graph')}
              title="返回工作台"
              className="grid h-8 w-8 place-items-center rounded-lg text-[#9d97b6] transition hover:bg-white/[0.06] hover:text-white"
            >
              <ChevronLeft className="h-5 w-5" />
            </button>
            <span className="grid h-9 w-9 place-items-center rounded-xl bg-gradient-to-br from-[#8b5cf6] to-[#5b21b6]">
              <span className="text-sm text-white">S</span>
            </span>
            <p className="text-base font-semibold text-white">配置中心</p>
          </div>
        </div>
        <nav className="flex-1 space-y-2 p-4">
          {SETTINGS_TABS.map(({ id, label }) => (
            <button
              key={id}
              onClick={() => controller.setSettingsTab(id)}
              className={`w-full rounded-lg px-4 py-3 text-left text-sm transition-colors ${
                controller.settingsTab === id
                  ? 'bg-violet-600/20 text-violet-200'
                  : 'text-[#8f88a8] hover:bg-white/[0.05] hover:text-white'
              }`}
            >
              {label}
            </button>
          ))}
        </nav>
      </aside>

      <div className="settings-theme flex-1 overflow-y-auto">
        {controller.settingsTab === 'projects' && <ProjectsPage key="projects" />}
        {controller.settingsTab === 'ssh-keys' && <SshKeysPage key="ssh-keys" />}
        {controller.settingsTab === 'endpoint-rules' && <EndpointRulesPage key="endpoint-rules" />}
      </div>
    </div>
  )
}
