// 公开证据引用标签（交接规格 2026-08-17 §6）。
// 引用显示公开编号和标题，例如「E-01 · SQL 审计工具交付证据集」；
// 禁止把内部 Evidence ID 暴露给访客。目录缺失或未知 ID 时回退通用文案。

export interface EvidenceCatalogEntry {
  id: string
  code: string
  title: string
}

export type EvidenceLabeler = (evidenceId: string) => string

const FALLBACK_LABEL = '已审核公开证据'

export function buildEvidenceLabeler(
  catalog: ReadonlyArray<EvidenceCatalogEntry>,
): EvidenceLabeler {
  const labels = new Map<string, string>()
  for (const entry of catalog) {
    if (!entry || typeof entry.id !== 'string' || labels.has(entry.id)) continue
    const code = typeof entry.code === 'string' ? entry.code.trim() : ''
    const title = typeof entry.title === 'string' ? entry.title.trim() : ''
    if (!code || !title) continue
    labels.set(entry.id, `${code} · ${title}`)
  }
  return (evidenceId: string): string => labels.get(evidenceId) ?? FALLBACK_LABEL
}
