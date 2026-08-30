import { ChevronLeft } from 'lucide-react'
import ProjectsPage from '../settings/ProjectsPage'
import type { WorkbenchController } from './state/useWorkbenchState'

export default function WorkbenchSettingsScreen({ controller }: { controller: WorkbenchController }) {
  return (
    <div className="settings-theme flex h-full flex-col overflow-hidden bg-[#07080f] text-[#f7f2ff]">
      <header className="flex h-16 shrink-0 items-center gap-3 border-b border-white/[0.08] px-5">
        <button onClick={() => controller.setMode('graph')} title="返回工作台" className="grid h-9 w-9 place-items-center rounded-lg text-[#9d97b6] transition hover:bg-white/[0.06] hover:text-white">
          <ChevronLeft className="h-5 w-5" />
        </button>
        <div>
          <p className="text-sm font-semibold text-white">仓库配置</p>
          <p className="text-xs text-[#77718f]">管理解析来源和异步任务</p>
        </div>
      </header>
      <div className="flex-1 overflow-y-auto">
        <ProjectsPage />
      </div>
    </div>
  )
}
