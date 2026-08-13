export interface PresetContractRequest {
  questionPresetId?: string
  contractVersion?: string
}

export interface PresetContractResponse {
  resolution?: string
  noticeCode?: string
  questionPresetId?: string
  contractVersion?: string
}

export const PRESET_CONTRACT_STALE = 'PRESET_CONTRACT_STALE'
export const PRESET_CONTRACT_UNAVAILABLE = 'PRESET_CONTRACT_UNAVAILABLE'

// P3：响应联合类型既含 AnswerResponse 也含 CompletionReceiptResponse。
// 这两个守卫只做结构化读取，不把响应收窄为任一具体分支（handoff §4）。
export function isPresetContractStale(response: unknown): boolean {
  return matchesPresetContractNotice(response, PRESET_CONTRACT_STALE)
}

export function isPresetContractUnavailable(response: unknown): boolean {
  return matchesPresetContractNotice(response, PRESET_CONTRACT_UNAVAILABLE)
}

function matchesPresetContractNotice(response: unknown, noticeCode: string): boolean {
  if (typeof response !== 'object' || response === null) return false
  const record = response as { resolution?: unknown; noticeCode?: unknown }
  return record.resolution === 'CAPABILITY_UNAVAILABLE' && record.noticeCode === noticeCode
}

interface PresetContractRetryHint {
  questionPresetId: string
  contractVersion: string
}

function readPresetContractRetryHint(response: unknown): PresetContractRetryHint | null {
  if (typeof response !== 'object' || response === null) return null
  const record = response as { questionPresetId?: unknown; contractVersion?: unknown }
  if (typeof record.questionPresetId !== 'string' || !record.questionPresetId.trim()) return null
  if (typeof record.contractVersion !== 'string' || !record.contractVersion.trim()) return null
  return {
    questionPresetId: record.questionPresetId,
    contractVersion: record.contractVersion,
  }
}

export async function askWithPresetContractRetry<
  TRequest extends PresetContractRequest,
  TResponse,
>(request: TRequest, send: (request: TRequest) => Promise<TResponse>): Promise<TResponse> {
  const first = await send(request)
  if (!request.questionPresetId?.trim() || !isPresetContractStale(first)) {
    return first
  }
  const hint = readPresetContractRetryHint(first)
  if (!hint) return first

  return send({
    ...request,
    questionPresetId: hint.questionPresetId,
    contractVersion: hint.contractVersion,
  })
}
