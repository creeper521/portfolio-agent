import type { AnswerResponse } from './answerTypes'

export function partialSuccessResponse(): AnswerResponse {
  return {
    turnId: 'turn-semantic-partial',
    contentVersion: 'public-2026-08-10',
    resolution: 'ANSWERED',
    title: 'Compatibility title that must not define the semantic view',
    summary: 'Compatibility summary that must not define the semantic view',
    blocks: [{
      sourceScope: 'PORTFOLIO',
      sectionType: 'BACKGROUND',
      title: 'Legacy block',
      content: 'Legacy fallback must not define the semantic rendering.',
      claimIds: [],
      evidenceIds: [],
    }],
    agentTurn: {
      contractVersion: 'stp-v1',
      disposition: 'READY',
      plan: {
        taskCount: 3,
        tasks: [{
          displayIndex: '01',
          goalLabel: 'Review the SQL project',
          sourceDomain: 'PORTFOLIO',
          dependencySummary: 'Starts independently',
          taskId: 'task-01',
          dependencyType: 'REQUIRES_SUCCESS',
          modelConfidence: 0.99,
        }],
      },
      outcome: {
        planOutcome: 'PARTIAL',
        taskSummary: {
          displayMode: 'EXPANDED',
          totalCount: 3,
          answeredCount: 1,
          notSupportedCount: 1,
          emptyCount: 0,
          blockedCount: 1,
          failedCount: 0,
          cancelledCount: 0,
          degradedCount: 0,
          items: [{
            displayIndex: '01',
            goalLabel: 'Review the SQL project',
            status: 'COMPLETED',
            sourceDomain: 'PORTFOLIO',
            taskId: 'task-01',
          }],
        },
      },
      completedTasks: [{
        displayIndex: '01',
        goalLabel: 'Review the SQL project',
        sourceDomain: 'PORTFOLIO',
        taskId: 'task-01',
        resultPayload: {
          kind: 'SECTION_RESULT',
          blocks: [{
            sourceScope: 'PORTFOLIO',
            sectionType: 'SOLUTION',
            title: 'SQL project review',
            content: 'Only completed-task content appears here.',
            claimIds: ['claim-safe'],
            evidenceIds: ['evidence-safe'],
          }],
        },
      }],
    },
  }
}

export function confirmationRequiredResponse(): AnswerResponse {
  return {
    turnId: 'turn-semantic-confirmation',
    contentVersion: 'public-2026-08-10',
    resolution: 'AWAITING_CONFIRMATION',
    title: '',
    summary: '',
    blocks: [],
    agentTurn: {
      contractVersion: 'stp-v1',
      disposition: 'CONFIRMATION_REQUIRED',
      plan: {
        taskCount: 4,
        tasks: [{
          displayIndex: '01',
          goalLabel: 'Review the SQL project',
          sourceDomain: 'PORTFOLIO',
        }],
      },
      planConfirmation: {
        confirmationId: 'confirmation-01',
        confirmationPlan: 'opaque-envelope',
        planFingerprint: 'sha256:opaque-fingerprint',
        integrityToken: 'opaque-integrity-token',
        expiresAt: '2026-08-10T12:10:00Z',
        triggerCodes: ['TASK_COUNT_REQUIRES_CONFIRMATION'],
      },
    },
  }
}

export function localPartialReadyResponse(): AnswerResponse {
  const base = partialSuccessResponse()
  return {
    ...base,
    turnId: 'turn-semantic-local-clarification',
    agentTurn: {
      ...base.agentTurn!,
      disposition: 'PARTIAL_READY',
      clarification: {
        scope: 'LOCAL',
        prompt: '你希望项目 A 与哪个项目比较？',
        fields: [{
          fieldKey: 'comparisonSubject',
          inputMode: 'SINGLE_CHOICE',
          options: [
            { value: 'project-b', label: '项目 B' },
            { value: 'project-c', label: '项目 C' },
          ],
          required: true,
          affectedGoalLabels: ['比较两个项目'],
        }],
        blockedTaskCount: 1,
        continuingTaskCount: 1,
      },
    } as typeof base.agentTurn,
  }
}

export function criticalClarificationResponse(): AnswerResponse {
  const local = localPartialReadyResponse()
  return {
    ...local,
    turnId: 'turn-semantic-critical-clarification',
    resolution: 'NEEDS_CLARIFICATION',
    agentTurn: {
      contractVersion: 'stp-v1',
      disposition: 'CLARIFICATION_REQUIRED',
      plan: local.agentTurn && 'plan' in local.agentTurn ? local.agentTurn.plan : undefined,
      clarification: {
        scope: 'CRITICAL',
        prompt: '请选择关键比较主体',
        fields: [{
          fieldKey: 'comparisonSubject',
          inputMode: 'SINGLE_CHOICE',
          options: [{ value: 'project-b', label: '项目 B' }],
          required: true,
          affectedGoalLabels: ['比较两个项目', '形成综合建议'],
        }],
        blockedTaskCount: 2,
        continuingTaskCount: 0,
      },
    },
  }
}

export function invalidatedPlanResponse(): AnswerResponse {
  const base = partialSuccessResponse()
  return {
    ...base,
    turnId: 'turn-semantic-invalidated',
    resolution: 'REJECTED',
    title: '',
    summary: '',
    blocks: [],
    agentTurn: {
      contractVersion: 'stp-v1',
      disposition: 'REJECTED',
      planChange: {
        summary: '公开内容版本已变化，需要重新生成计划。',
        changeLabels: ['内容版本变化'],
        invalidatedPlanReference: {
          planId: 'plan-opaque',
          planFingerprint: 'sha256:opaque',
        },
      },
    },
  }
}

export function legacyOnlyResponse(): AnswerResponse {
  return {
    turnId: 'turn-legacy',
    contentVersion: 'public-2026-08-10',
    resolution: 'ANSWERED',
    title: 'Legacy answer',
    summary: 'Legacy summary',
    sections: [{
      type: 'BACKGROUND',
      title: 'Background',
      content: 'Legacy content',
      evidenceIds: [],
    }],
  }
}
