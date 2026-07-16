import type {
  AudienceRole,
  PublicEvidence,
  PublicProject,
} from '../../public-content/model/publicContentTypes'

export interface PreviewAnswer {
  content: string
  evidenceIds: string[]
}

export function createPreviewAnswer(
  question: string,
  role: AudienceRole,
  project: PublicProject,
  evidence: PublicEvidence[],
): PreviewAnswer {
  const normalized = question.trim()
  const citedEvidence = evidence.filter((item) => project.evidenceIds.includes(item.id))

  if (!normalized) {
    return { content: '请先输入一个具体问题。', evidenceIds: [] }
  }

  let content: string
  if (/验证|测试|证明|证据/.test(normalized)) {
    content = `这个项目的验证覆盖时间排序、完整 SQL 展示、动态来源、导出、归档、复用和软删除；同时验证 WebSocket 断开后的轮询降级、刷新后的状态恢复，以及路径和输入安全边界。`
  } else if (/职责|角色|贡献|承担/.test(normalized) || role === 'HR') {
    content = `${project.handoff} 具体工作包括多目标路由、异步查询、状态恢复与取消、进度推送与降级、动态结果、导出归档和安全边界。`
  } else if (/复盘|成长|导师/.test(normalized) || role === 'MENTOR') {
    content = `这段经历最重要的复盘，是把“功能能跑”进一步变成“边界明确、状态可恢复、结果可追溯、材料可交接”。关键决策都围绕消除硬编码和建立可验证闭环展开。`
  } else if (/决策|方案|技术|如何做/.test(normalized) || role === 'INTERVIEWER') {
    content = `${project.solution} 其中最关键的决策是限制可选目标而不是允许任意主机与路径输入，并让动态来源结果成为唯一真实结果。`
  } else {
    content = `${project.summary} ${project.outcome}`
  }

  return {
    content,
    evidenceIds: citedEvidence.map((item) => item.id),
  }
}
