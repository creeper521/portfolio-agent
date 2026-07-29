import { DiagnosticTransport } from './diagnosticTransport'
import {
  createFirstPartyStackFingerprint,
  createFrontendDiagnosticEvent,
  serializeFrontendEvent,
  type SafeFrontendEvent,
} from './frontendDiagnosticTypes'

export interface FrontendDiagnostics {
  debug(event: SafeFrontendEvent): void
  report(event: SafeFrontendEvent): void
}

const transport = new DiagnosticTransport()
transport.installPagehideListener()

export const frontendDiagnostics: FrontendDiagnostics = {
  debug(event): void {
    try {
      const sanitized = serializeFrontendEvent(event)
      if (sanitized && window.location.hostname === 'localhost') console.debug('frontend diagnostic', sanitized)
    } catch {
      // Diagnostics must never affect the application.
    }
  },
  report(event): void {
    try {
      const sanitized = serializeFrontendEvent(event)
      if (sanitized) transport.report(sanitized)
    } catch {
      // Diagnostics must never affect the application.
    }
  },
}

export function installRuntimeDiagnostics(): void {
  window.addEventListener('error', (event) => reportRuntimeFailure('ERROR_EVENT', event.error))
  window.addEventListener('unhandledrejection', (event) => reportRuntimeFailure('UNHANDLED_REJECTION', event.reason))
}

function reportRuntimeFailure(errorKind: 'ERROR_EVENT' | 'UNHANDLED_REJECTION', error: unknown): void {
  try {
    let stack: unknown
    try {
      stack = error instanceof Error ? error.stack : undefined
    } catch {
      stack = undefined
    }
    void createFirstPartyStackFingerprint(stack)
      .then((errorFingerprint) => {
        frontendDiagnostics.report(createFrontendDiagnosticEvent({
          eventName: 'frontend.runtime.failed',
          errorKind,
          ...(errorFingerprint === undefined ? {} : { errorFingerprint }),
        }))
      })
      .catch(() => undefined)
  } catch {
    // Runtime diagnostics are isolated from browser error handling.
  }
}
