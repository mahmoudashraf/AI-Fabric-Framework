import { resolveSupportPresentation, validateConfig } from './config'
import type { SupportExperienceConfig, SupportRequest, SupportSession, TicketCapability } from './types'

/** Synthetic examples only; no network, real identity, presence or delivery claims. */
export function supportExperiencePreviewFixtures(value: SupportExperienceConfig | Record<string, unknown>, capabilities: TicketCapability[] = ['CREATE', 'LIST', 'GET', 'RESOLVE', 'CANCEL', 'REOPEN', 'SEEN', 'LINK_CONVERSATION']) {
  const config = validateConfig(value as Record<string, unknown>, { allowUnbound: true })
  const p = resolveSupportPresentation(config)
  const effective = p.tickets ? capabilities : []
  const session: SupportSession = { authenticated: true, requesterId: 'preview-requester',
    space: { spaceId: config.support?.spaceId ?? 'preview-space', providerBindingId: config.support?.ticketProviderBindingId ?? 'preview-binding',
      humanHandoverEnabled: p.handover, capabilities: effective }, capabilities: effective }
  const request: SupportRequest = { id: 'preview-request', reference: 'SAMPLE-001', providerBindingId: session.space.providerBindingId,
    subject: 'Sample support request', details: 'Synthetic request details. Nothing has been submitted.', status: 'OPEN', version: 'preview:opaque-1',
    createdAt: '2026-01-01T10:00:00Z', updatedAt: '2026-01-01T10:05:00Z', unread: true, openingMessageRequired: false,
    conversation: { episodeId: 'preview-episode', conversationId: 'preview-conversation', delivery: 'NOT_REQUIRED' }, capabilities: effective }
  return {
    fixture: true as const,
    userMessage: 'Can you help me understand the approved guidance?',
    answer: p.welcomeMessage,
    confirmation: 'Confirm this sample support request?',
    documents: [], attachment: p.attachments ? { type: 'document', contextLabel: 'Sample context', data: { id: 'preview-context', title: 'Sample context', content: 'Synthetic context only.' } } : null,
    fallback: { status: 'Sample only', summary: 'No live support operation was performed.' },
    receipt: p.tickets ? { reference: 'SAMPLE-001', status: 'Sample only', message: 'No request was submitted.' } : null,
    presentations: [],
    support: { session, requests: p.tickets ? [request] : [], team: p.teamVisible && p.handover ? [
      { profileRef: 'preview-agent', displayName: 'Sample assigned team member', photoAvailable: false },
    ] : [], topics: config.support?.topics ?? [], presentation: p },
  }
}
