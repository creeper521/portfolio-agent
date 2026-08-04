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

export function isPresetContractStale(response: PresetContractResponse): boolean {
  return response.resolution === 'CAPABILITY_UNAVAILABLE'
    && response.noticeCode === PRESET_CONTRACT_STALE
}

export function isPresetContractUnavailable(response: PresetContractResponse): boolean {
  return response.resolution === 'CAPABILITY_UNAVAILABLE'
    && response.noticeCode === PRESET_CONTRACT_UNAVAILABLE
}

export async function askWithPresetContractRetry<
  TRequest extends PresetContractRequest,
  TResponse extends PresetContractResponse,
>(request: TRequest, send: (request: TRequest) => Promise<TResponse>): Promise<TResponse> {
  const first = await send(request)
  if (!request.questionPresetId?.trim()
    || !isPresetContractStale(first)
    || !first.questionPresetId?.trim()
    || !first.contractVersion?.trim()) {
    return first
  }

  return send({
    ...request,
    questionPresetId: first.questionPresetId,
    contractVersion: first.contractVersion,
  })
}
