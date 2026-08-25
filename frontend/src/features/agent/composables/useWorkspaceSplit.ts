import { ref } from 'vue'

// Agent 工作区三栏宽度划分的计算与持久化（composable 状态层）。
// localStorage 只保存布局偏好：这是界面几何数据，不属于会话内容，
// 不在「浏览器不得持久化问题/答案/历史」的禁令范围内。

export const WORKSPACE_SPLIT_KEY = 'portfolio.workspace.split.v1'
/** 布局常量：sessions/evidence 为对应侧栏的 [最小, 最大] 宽度区间（px），chatMin 为聊天栏最小宽度。 */
export const WORKSPACE_LIMITS = {
  sessions: [250, 280] as const,
  evidence: [330, 380] as const,
  chatMin: 640,
}

/** 会话栏与证据栏的像素宽度；聊天栏占据剩余可用宽度。 */
export interface WorkspaceSplit {
  sessions: number
  evidence: number
}

function clamp(value: number, [min, max]: readonly [number, number]) {
  return Math.min(max, Math.max(min, Math.round(value)))
}

/** 默认分栏宽度（取自各区间的常用值）。 */
export function workspaceDefaults(): WorkspaceSplit {
  return {
    sessions: 260,
    evidence: 350,
  }
}

/** 把任意输入分栏夹取到允许区间并取整；持久化恢复与用户输入都先经它规范化。 */
export function clampWorkspaceSplit(next: WorkspaceSplit): WorkspaceSplit {
  return {
    sessions: clamp(next.sessions, WORKSPACE_LIMITS.sessions),
    evidence: clamp(next.evidence, WORKSPACE_LIMITS.evidence),
  }
}

/**
 * 在可用视口宽度内安放偏好分栏：总量放得下时原样返回；放不下时先保证
 * 聊天栏最小宽度，再把剩余可分配空间按「两侧偏好超出各自下限的份额」比例
 * 分配——偏好越宽的一侧保留越多，而不是简单等比压缩。
 */
export function fitWorkspaceSplit(
  preferred: WorkspaceSplit,
  availableWidth: number,
): WorkspaceSplit {
  const clamped = clampWorkspaceSplit(preferred)
  if (!Number.isFinite(availableWidth)) return clamped

  const minimumSideWidth =
    WORKSPACE_LIMITS.sessions[0] + WORKSPACE_LIMITS.evidence[0]
  const availableSideWidth = Math.max(
    minimumSideWidth,
    Math.floor(availableWidth) - WORKSPACE_LIMITS.chatMin,
  )
  if (clamped.sessions + clamped.evidence <= availableSideWidth) return clamped

  const sessionsSlack = clamped.sessions - WORKSPACE_LIMITS.sessions[0]
  const evidenceSlack = clamped.evidence - WORKSPACE_LIMITS.evidence[0]
  const preferredSlack = sessionsSlack + evidenceSlack
  if (preferredSlack <= 0) return clamped

  const availableSlack = availableSideWidth - minimumSideWidth
  const sessions =
    WORKSPACE_LIMITS.sessions[0] +
    Math.floor((availableSlack * sessionsSlack) / preferredSlack)

  return {
    sessions,
    evidence: availableSideWidth - sessions,
  }
}

/** 读取 localStorage 中已保存的分栏并夹取规范化；缺失、损坏或字段非法一律返回 null。 */
function readWorkspaceSplit(): WorkspaceSplit | null {
  try {
    const parsed = JSON.parse(localStorage.getItem(WORKSPACE_SPLIT_KEY) ?? 'null')
    if (
      parsed &&
      typeof parsed.sessions === 'number' &&
      typeof parsed.evidence === 'number'
    ) {
      return clampWorkspaceSplit(parsed)
    }
  } catch {
    return null
  }
  return null
}

/**
 * 工作区分栏宽度的响应式状态（composable）。
 * set 默认不落盘（拖动过程高频调用），persistChange=true 才写入 localStorage。
 */
export function useWorkspaceSplit() {
  const state = ref(readWorkspaceSplit() ?? workspaceDefaults())

  /** 把当前分栏写入 localStorage；只写布局数值，不含任何会话内容。 */
  function persist() {
    localStorage.setItem(WORKSPACE_SPLIT_KEY, JSON.stringify(state.value))
  }

  /** 更新一侧宽度（先夹取规范化）；persistChange 控制本次是否落盘。 */
  function set(
    key: keyof WorkspaceSplit,
    value: number,
    persistChange = false,
  ) {
    state.value = clampWorkspaceSplit({ ...state.value, [key]: value })
    if (persistChange) persist()
  }

  /** 按增量调整一侧宽度并立即落盘（分隔条拖动结束/按钮调整）。 */
  function adjust(key: keyof WorkspaceSplit, delta: number) {
    set(key, state.value[key] + delta, true)
  }

  /** 恢复默认分栏并落盘。 */
  function reset() {
    state.value = workspaceDefaults()
    persist()
  }

  return { state, set, adjust, reset, persist }
}
