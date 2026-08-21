import { serializeFrontendEvent, type ReportableFrontendEvent } from './frontendDiagnosticTypes'

const DIAGNOSTIC_ENDPOINT = '/api/client-diagnostics'
const MAX_QUEUE_SIZE = 20
const MAX_BATCH_SIZE = 10
const UPLOAD_TIMEOUT_MS = 2_000

export class DiagnosticTransport {
  private readonly queue: ReportableFrontendEvent[] = []
  private inFlight: ReportableFrontendEvent[] = []
  private flushTimer: ReturnType<typeof setTimeout> | undefined
  private flushing = false
  private pageHidden = false
  private pagehideListener: (() => void) | undefined

  report(event: ReportableFrontendEvent): void {
    try {
      const sanitized = serializeFrontendEvent(event)
      if (!sanitized || this.pageHidden || this.queue.length >= MAX_QUEUE_SIZE) return
      this.queue.push(sanitized)
      this.scheduleFlush()
    } catch {
      // Malformed diagnostics are silently dropped.
    }
  }

  async flush(keepalive = false): Promise<void> {
    this.clearFlushTimer()
    if (keepalive) {
      this.pageHidden = true
      const exitEvents = [...this.inFlight, ...this.queue]
      this.queue.length = 0
      const uploads: Promise<void>[] = []
      for (let index = 0; index < exitEvents.length; index += MAX_BATCH_SIZE) {
        uploads.push(this.upload(exitEvents.slice(index, index + MAX_BATCH_SIZE), true))
      }
      await Promise.all(uploads)
      return
    }
    if (this.flushing) return
    const events = this.queue.splice(0, MAX_BATCH_SIZE)
    if (events.length === 0) return
    this.flushing = true
    this.inFlight = events
    try {
      await this.upload(events, false)
    } finally {
      this.inFlight = []
      this.flushing = false
      if (!this.pageHidden && this.queue.length > 0) this.scheduleFlush()
    }
  }

  installPagehideListener(): void {
    if (this.pagehideListener) return
    this.pagehideListener = () => { void this.flush(true) }
    window.addEventListener('pagehide', this.pagehideListener)
  }

  dispose(): void {
    this.clearFlushTimer()
    if (this.pagehideListener) {
      window.removeEventListener('pagehide', this.pagehideListener)
      this.pagehideListener = undefined
    }
    this.inFlight = []
    this.queue.length = 0
  }

  private async upload(events: ReportableFrontendEvent[], keepalive: boolean): Promise<void> {
    if (events.length === 0) return
    const controller = new AbortController()
    const timeoutId = setTimeout(() => controller.abort(), UPLOAD_TIMEOUT_MS)
    try {
      await fetch(DIAGNOSTIC_ENDPOINT, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ events }),
        keepalive,
        signal: controller.signal,
      })
    } catch {
      // Diagnostics uploads are best-effort and never emit more diagnostics.
    } finally {
      clearTimeout(timeoutId)
    }
  }

  private scheduleFlush(): void {
    if (this.flushTimer) return
    this.flushTimer = setTimeout(() => {
      this.flushTimer = undefined
      void this.flush()
    }, UPLOAD_TIMEOUT_MS)
  }

  private clearFlushTimer(): void {
    if (!this.flushTimer) return
    clearTimeout(this.flushTimer)
    this.flushTimer = undefined
  }
}
