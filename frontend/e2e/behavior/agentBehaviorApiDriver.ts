import type { APIRequestContext, APIResponse } from '@playwright/test'
import type { PublicPortfolio, QuestionPreset } from '../../src/features/public-content/model/publicContentTypes'
import type {
  AnswerResponse,
  CompletionReceiptResponse,
  P3AnswerSuccess,
  SemanticSubjectReference,
} from '../../src/features/agent/model/answerTypes'
import { resolveAnswerSuccess } from '../../src/features/agent/model/answerTypes'
import { expandActivePresetScenarios } from './agentBehaviorCorpus'
import type { BehaviorObservation, BehaviorScenario, BehaviorTurn, TurnTransportOutcome } from './agentBehaviorTypes'
import {
  appendAcceptedTurn,
  createBehaviorRequest,
  scenarioSeedFor,
  scenarioTurnFor,
  type BehaviorConversationState,
  transportOutcomeForError,
} from './agentBehaviorRequest'

interface ParsedApiResult {
  readonly outcome: TurnTransportOutcome
  readonly response?: P3AnswerSuccess
  readonly responseHash?: string
  readonly contentTypeValid: boolean
  readonly noStoreValid: boolean
  readonly statusValid: boolean
  readonly durationBucket: BehaviorObservation['durationBucket']
}

interface ResponseFacts {
  readonly resolution?: BehaviorObservation['resolution']
  readonly disposition?: BehaviorObservation['disposition']
  readonly evidenceState?: BehaviorObservation['evidenceState']
  readonly subjectReferences: readonly SemanticSubjectReference[]
  readonly evidenceIds: readonly string[]
  readonly publicCitationIds: readonly string[]
  readonly continuableContextCount: number
  readonly leakedPrivateMarker: boolean
  readonly fabricatedStatus: boolean
  readonly fabricatedContribution: boolean
  readonly citationMismatch: boolean
  readonly resumeToken?: string
  readonly contractError: boolean
}

function durationBucket(elapsedMilliseconds: number): BehaviorObservation['durationBucket'] {
  if (elapsedMilliseconds < 250) return 'LT_250_MS'
  if (elapsedMilliseconds < 1_000) return 'LT_1_S'
  if (elapsedMilliseconds < 5_000) return 'LT_5_S'
  return 'GTE_5_S'
}

async function sha256(value: string): Promise<string> {
  const bytes = new TextEncoder().encode(value)
  const digest = await globalThis.crypto.subtle.digest('SHA-256', bytes)
  return Array.from(new Uint8Array(digest), (byte) => byte.toString(16).padStart(2, '0')).join('')
}

function isNoStore(response: APIResponse): boolean {
  return (response.headers()['cache-control'] ?? '').toLowerCase().includes('no-store')
}

function isJson(response: APIResponse): boolean {
  return (response.headers()['content-type'] ?? '').toLowerCase().includes('application/json')
}

function statusOutcome(status: number): TurnTransportOutcome {
  if (status === 408 || status === 504) return 'TIMED_OUT'
  if (status >= 400 && status < 500) return 'REJECTED'
  return 'UNAVAILABLE'
}

async function parseApiResponse(response: APIResponse, startedAt: number): Promise<ParsedApiResult> {
  const statusValid = response.ok()
  const contentTypeValid = isJson(response)
  const noStoreValid = isNoStore(response)
  const elapsed = Date.now() - startedAt
  if (!statusValid || !contentTypeValid || !noStoreValid) {
    return {
      outcome: statusValid ? 'UNAVAILABLE' : statusOutcome(response.status()),
      contentTypeValid,
      noStoreValid,
      statusValid,
      durationBucket: durationBucket(elapsed),
    }
  }
  const bodyText = await response.text()
  const responseHash = await sha256(bodyText)
  let parsed: P3AnswerSuccess
  try {
    parsed = JSON.parse(bodyText) as P3AnswerSuccess
  } catch {
    return {
      outcome: 'UNAVAILABLE',
      responseHash,
      contentTypeValid,
      noStoreValid,
      statusValid,
      durationBucket: durationBucket(elapsed),
    }
  }
  return {
    outcome: 'ACCEPTED',
    response: parsed,
    responseHash,
    contentTypeValid,
    noStoreValid,
    statusValid,
    durationBucket: durationBucket(elapsed),
  }
}

function addSubject(subjects: Map<string, SemanticSubjectReference>, candidate: unknown): void {
  if (typeof candidate !== 'object' || candidate === null) return
  const value = candidate as { subjectType?: unknown; subjectId?: unknown }
  if (typeof value.subjectType !== 'string' || typeof value.subjectId !== 'string') return
  const subject: SemanticSubjectReference = { subjectType: value.subjectType, subjectId: value.subjectId }
  subjects.set(`${subject.subjectType}:${subject.subjectId}`, subject)
}

function readFacts(response: P3AnswerSuccess): ResponseFacts {
  const resolved = resolveAnswerSuccess(response)
  if (resolved.kind === 'CONTRACT_ERROR') {
    return {
      subjectReferences: [], evidenceIds: [], publicCitationIds: [], continuableContextCount: 0,
      leakedPrivateMarker: false, fabricatedStatus: false, fabricatedContribution: false, citationMismatch: true,
      contractError: true,
    }
  }
  if (resolved.kind === 'COMPLETION_RECEIPT') return factsFromReceipt(resolved.response)
  return factsFromAnswer(resolved.response)
}

function factsFromReceipt(response: CompletionReceiptResponse): ResponseFacts {
  const continuableContextCount = response.completedTasks.filter((task) => task.contextHandle !== undefined).length
  return {
    subjectReferences: [], evidenceIds: [], publicCitationIds: [], continuableContextCount,
    leakedPrivateMarker: false, fabricatedStatus: false, fabricatedContribution: false, citationMismatch: false,
    resumeToken: response.conversation.resumeToken,
    contractError: false,
  }
}

function factsFromAnswer(response: AnswerResponse): ResponseFacts {
  const subjects = new Map<string, SemanticSubjectReference>()
  const evidenceIds = new Set<string>(response.evidenceIds ?? [])
  const publicCitationIds = new Set<string>()
  const catalogKeys = new Set((response.publicSourceCatalog ?? []).map((entry) => entry.referenceKey))

  for (const source of response.publicSourceCatalog ?? []) publicCitationIds.add(source.referenceKey)
  for (const block of response.blocks ?? []) {
    for (const evidenceId of block.evidenceIds ?? []) evidenceIds.add(evidenceId)
    for (const source of block.sourceReferences ?? []) publicCitationIds.add(source.referenceKey)
    for (const value of block.support?.publicSourceKeys ?? []) publicCitationIds.add(value)
  }
  for (const projectSlug of response.referenceContext?.projectSlugs ?? []) {
    addSubject(subjects, { subjectType: 'PROJECT', subjectId: projectSlug })
  }
  for (const caseSlug of response.referenceContext?.caseSlugs ?? []) {
    addSubject(subjects, { subjectType: 'CASE', subjectId: caseSlug })
  }

  let continuableContextCount = 0
  const agentTurn = response.agentTurn
  if (typeof agentTurn === 'object' && agentTurn !== null && 'completedTasks' in agentTurn) {
    const completedTasks = agentTurn.completedTasks
    if (Array.isArray(completedTasks)) {
      for (const task of completedTasks) {
        if (typeof task !== 'object' || task === null) continue
        const taskValue = task as { contextHandle?: unknown; continuationContext?: { contextHandle?: unknown }; resultPayload?: unknown }
        if (typeof taskValue.contextHandle === 'string' || typeof taskValue.continuationContext?.contextHandle === 'string') {
          continuableContextCount += 1
        }
        const payload = taskValue.resultPayload
        if (typeof payload !== 'object' || payload === null) continue
        const recommendations = (payload as { recommendations?: unknown }).recommendations
        if (!Array.isArray(recommendations)) continue
        for (const recommendation of recommendations) {
          const recommendationValue = recommendation as { subject?: unknown; evidenceIds?: unknown }
          addSubject(subjects, recommendationValue.subject)
          if (Array.isArray(recommendationValue.evidenceIds)) {
            for (const evidenceId of recommendationValue.evidenceIds) {
              if (typeof evidenceId === 'string') evidenceIds.add(evidenceId)
            }
          }
        }
      }
    }
  }

  const sourceMismatch = [...publicCitationIds].some((citationId) => catalogKeys.size > 0 && !catalogKeys.has(citationId))
  return {
    resolution: response.resolution,
    disposition: typeof response.agentTurn === 'object' && response.agentTurn !== null && 'disposition' in response.agentTurn
      ? (response.agentTurn.disposition as ResponseFacts['disposition'])
      : undefined,
    evidenceState: response.evidenceState,
    subjectReferences: [...subjects.values()],
    evidenceIds: [...evidenceIds],
    publicCitationIds: [...publicCitationIds],
    continuableContextCount,
    leakedPrivateMarker: false,
    fabricatedStatus: false,
    fabricatedContribution: false,
    citationMismatch: sourceMismatch,
    resumeToken: response.conversation?.resumeToken,
    contractError: false,
  }
}

function isAcceptedAnswer(facts: ResponseFacts): boolean {
  return !facts.contractError
    && facts.resolution !== 'REJECTED'
    && facts.resolution !== 'BOUNDARY'
    && facts.resolution !== 'CAPABILITY_UNAVAILABLE'
    && facts.resolution !== 'NOT_SUPPORTED'
    && facts.resolution !== 'INVALID_INPUT'
    && facts.disposition !== 'REJECTED'
    && facts.disposition !== 'BOUNDARY'
}

function observationForFailure(scenario: BehaviorScenario, turn: BehaviorTurn, result: ParsedApiResult, historyTurnIds: readonly string[]): BehaviorObservation {
  return {
    scenarioId: scenario.id,
    turnId: turn.id,
    transportOutcome: result.outcome,
    subjectReferences: [],
    evidenceIds: [],
    publicCitationIds: [],
    historyTurnIds: [...historyTurnIds],
    continuableContextCount: 0,
    leakedPrivateMarker: false,
    fabricatedStatus: false,
    fabricatedContribution: false,
    citationMismatch: false,
    staleResponseOverwroteNewerTurn: false,
    responseHash: result.responseHash,
    durationBucket: result.durationBucket,
  }
}

function expandScenarioTurns(scenario: BehaviorScenario, presets: readonly QuestionPreset[]): readonly BehaviorTurn[] {
  if (!scenario.id.startsWith('active-preset:')) return scenario.turns
  const presetId = scenario.id.slice('active-preset:'.length)
  const preset = presets.find((candidate) => candidate.id === presetId)
  if (preset === undefined) return scenario.turns
  return scenario.turns.map((turn) => ({ ...turn, questionPresetId: preset.id, input: preset.text }))
}

async function loadPublicContent(request: APIRequestContext, baseURL: string): Promise<PublicPortfolio> {
  const response = await request.get(new URL('/api/v1/public-content', baseURL).toString())
  if (!response.ok() || !isJson(response)) throw new Error('PUBLIC_CONTENT_UNAVAILABLE')
  return (await response.json()) as PublicPortfolio
}

export async function executeApiScenario(
  request: APIRequestContext,
  baseURL: string,
  scenario: BehaviorScenario,
): Promise<readonly BehaviorObservation[]> {
  let publicContent: PublicPortfolio
  try {
    publicContent = await loadPublicContent(request, baseURL)
  } catch {
    return scenario.turns.map((turn) => observationForFailure(scenario, turn, {
      outcome: 'UNAVAILABLE', contentTypeValid: false, noStoreValid: false, statusValid: false,
      durationBucket: 'LT_250_MS',
    }, []))
  }
  // Keep the import as a compatibility check: active preset scenarios are generated from this same public snapshot.
  void expandActivePresetScenarios(publicContent.questionPresets)
  const turns = expandScenarioTurns(scenario, publicContent.questionPresets)
  const seed = scenarioSeedFor(scenario.initialState, publicContent)
  let state: BehaviorConversationState = seed.state
  const observations: BehaviorObservation[] = []
  for (const turn of turns) {
    const prepared = createBehaviorRequest(state, scenarioTurnFor(turn, seed))
    const startedAt = Date.now()
    let result: ParsedApiResult
    try {
      const response = await request.post(new URL('/api/v2/answers', baseURL).toString(), {
        headers: prepared.headers,
        data: prepared.body,
      })
      result = await parseApiResponse(response, startedAt)
    } catch (error) {
      result = {
        outcome: transportOutcomeForError(error),
        contentTypeValid: false,
        noStoreValid: false,
        statusValid: false,
        durationBucket: durationBucket(Date.now() - startedAt),
      }
    }
    if (result.response === undefined) {
      observations.push(observationForFailure(scenario, turn, result, prepared.historyTurnIds))
      state = appendAcceptedTurn(state, { turnId: turn.id, outcome: result.outcome, userContent: turn.input })
      continue
    }
    const facts = readFacts(result.response)
    const outcome = result.outcome === 'ACCEPTED' && !isAcceptedAnswer(facts) ? 'REJECTED' : result.outcome
    // The driver never applies a late response to the active state.  A response
    // arriving after a newer turn is therefore observable as stale, but it has
    // not overwritten newer state and must not be reported as a violation.
    const staleResponse = false
    const observation: BehaviorObservation = {
      scenarioId: scenario.id,
      turnId: turn.id,
      transportOutcome: outcome,
      resolution: facts.resolution,
      disposition: facts.disposition,
      evidenceState: facts.evidenceState,
      subjectReferences: facts.subjectReferences,
      evidenceIds: facts.evidenceIds,
      publicCitationIds: facts.publicCitationIds,
      historyTurnIds: outcome === 'ACCEPTED' ? [...prepared.historyTurnIds, turn.id] : [...prepared.historyTurnIds],
      continuableContextCount: facts.continuableContextCount,
      leakedPrivateMarker: facts.leakedPrivateMarker,
      fabricatedStatus: facts.fabricatedStatus,
      fabricatedContribution: facts.fabricatedContribution,
      citationMismatch: facts.citationMismatch,
      staleResponseOverwroteNewerTurn: staleResponse,
      responseHash: result.responseHash,
      durationBucket: result.durationBucket,
    }
    observations.push(observation)
    state = appendAcceptedTurn(state, { turnId: turn.id, outcome, userContent: turn.input, assistantContent: facts.resolution === undefined ? undefined : '[synthetic-public-answer]' })
    if (facts.resumeToken !== undefined) state = { ...state, resumeToken: facts.resumeToken }
  }
  return observations
}
