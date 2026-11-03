import { latestEpisode, SupportStore } from './store'
import type { SupportWorkspaceState } from './types'

/** Coalesces actual canonical updates; failed writes are not timer-replayed. */
export class ConversationActivityObserver {
  private timer?: ReturnType<typeof setTimeout>
  private pending?: { requestId: string; conversationId: string }
  private lastRevision = ''
  private lastAttempt = 0
  private running = false
  private active = true
  constructor(private store: SupportStore, private chat: () => SupportWorkspaceState, private visible: () => boolean,
    private intervalMs = 10000, private settleMs = 350) {}
  changed(chat: SupportWorkspaceState) {
    const state = this.store.getState(), request = state.request
    if (!this.active || !request || !chat.conversationId || !chat.canonicalRevision || chat.error
      || latestEpisode(request)?.conversationId !== chat.conversationId || !this.store.api.activity
      || !state.session?.capabilities.includes('OBSERVE_ACTIVITY')) return
    const revision = JSON.stringify([request.id, chat.conversationId, chat.canonicalRevision])
    if (revision === this.lastRevision) return
    this.lastRevision = revision
    this.pending = { requestId: request.id, conversationId: chat.conversationId }
    this.schedule()
  }
  private schedule() {
    if (!this.active || this.running || this.timer || !this.pending) return
    this.timer = setTimeout(() => { this.timer = undefined; void this.flush() },
      Math.max(this.settleMs, this.lastAttempt + this.intervalMs - Date.now()))
  }
  private async flush() {
    if (!this.active || !this.pending) return
    const state = this.store.getState(), chat = this.chat(), pending = this.pending
    if (state.request?.id !== pending.requestId || chat.conversationId !== pending.conversationId
      || latestEpisode(state.request)?.conversationId !== pending.conversationId || chat.error) { this.pending = undefined; return }
    if (state.busy || chat.busy) { this.schedule(); return }
    this.pending = undefined; this.running = true; this.lastAttempt = Date.now()
    try {
      await this.store.run(async () => {
        const observed = await this.store.observeActivity(state.request!)
        if (this.active && this.store.getState().request?.id === observed.id
          && this.chat().conversationId === pending.conversationId && this.visible()) await this.store.markSeen(observed)
      }, true)
    } finally { this.running = false; this.schedule() }
  }
  destroy() { this.active = false; clearTimeout(this.timer); this.pending = undefined }
}
