import type {
  CareerTrack,
  ProjectNature,
  PublicProject,
} from '../../public-content/model/publicContentTypes'

/**
 * 「项目主线」页的分组模型。
 *
 * /projects 只展示 Project（不再混入 Case），按求职方向分成两条主线架位，
 * 外加兜底与次级两组：
 *   - java      : careerTrack = JAVA_BACKEND 的主要项目（Java 后端主线）
 *   - agent     : careerTrack = AGENT 的主要项目（Agent 主线，学习型集成原型也如实呈现）
 *   - general   : careerTrack = UNCLASSIFIED（后端未标注方向时的保守回退，单一「项目」分区）
 *   - secondary : displayTier = SECONDARY（次级项目，排最后，不抢主线视线）
 *
 * 组顺序固定 java → agent → general → secondary：两条求职主线同时可见，
 * 不用默认 Tab 隐藏任一方向。组内保持输入顺序——顺序由内容策展固定，前端不做额外排序。
 */
export type ProjectMainlineGroupKey = 'java' | 'agent' | 'general' | 'secondary'

export interface ProjectMainlineGroup {
  key: ProjectMainlineGroupKey
  code: string
  label: string
  note: string
  projects: PublicProject[]
}

const MAINLINE_GROUP_ORDER: readonly ProjectMainlineGroupKey[] = [
  'java',
  'agent',
  'general',
  'secondary',
] as const

const MAINLINE_GROUP_INFO: Record<ProjectMainlineGroupKey, { label: string; note: string }> = {
  java: {
    label: 'Java 后端主线',
    note: '实习期间真实交付与协作的 Java 后端工程经历。',
  },
  agent: {
    label: 'Agent 主线',
    note: '学习期的 Agent 能力集成实践——如实标注为学习型集成原型。',
  },
  general: {
    label: '项目',
    note: '公开展示的项目。',
  },
  secondary: {
    label: '次级项目',
    note: '规模较小的补充项目。',
  },
}

/** CareerTrack 的中文展示文案。 */
export const CAREER_TRACK_LABEL: Record<CareerTrack, string> = {
  JAVA_BACKEND: 'Java 后端',
  AGENT: 'Agent',
  UNCLASSIFIED: '项目',
}

/** ProjectNature 的中文展示文案。 */
export const PROJECT_NATURE_LABEL: Record<ProjectNature, string> = {
  TOOL: '工具',
  WORKSTREAM: '工作主线',
  INTEGRATION_PROTOTYPE: '集成原型',
  UNCLASSIFIED: '项目',
}

function mainlineGroupKey(project: PublicProject): ProjectMainlineGroupKey {
  if (project.displayTier === 'SECONDARY') return 'secondary'
  if (project.careerTrack === 'JAVA_BACKEND') return 'java'
  if (project.careerTrack === 'AGENT') return 'agent'
  return 'general'
}

/**
 * 把 projects 分成 java / agent / general / secondary 四个主线架位。
 * 空组不返回，组内保持输入顺序；架位编号按输出顺序从 01 递增。
 */
export function buildProjectMainlines(projects: ReadonlyArray<PublicProject>): ProjectMainlineGroup[] {
  const buckets: Record<ProjectMainlineGroupKey, PublicProject[]> = {
    java: [],
    agent: [],
    general: [],
    secondary: [],
  }
  for (const project of projects) {
    buckets[mainlineGroupKey(project)].push(project)
  }
  return MAINLINE_GROUP_ORDER.filter((key) => buckets[key].length > 0).map((key, index) => ({
    key,
    code: String(index + 1).padStart(2, '0'),
    label: MAINLINE_GROUP_INFO[key].label,
    note: MAINLINE_GROUP_INFO[key].note,
    projects: buckets[key],
  }))
}
