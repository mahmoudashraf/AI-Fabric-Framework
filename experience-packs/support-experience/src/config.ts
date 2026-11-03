import type { SupportExperienceConfig, SupportCreateRequest } from './types'

export function validateConfig(value: Record<string, unknown>, options: { allowUnbound?: boolean } = {}): SupportExperienceConfig {
  const config = value as unknown as SupportExperienceConfig
  // These checks protect an executable configuration, not semantic answer quality.
  if (config?.schemaVersion !== 'loomai-support-experience-config-v1') throw new Error('Unsupported support configuration schema.')
  if (!config.app || typeof config.app.assistantLabel !== 'string' || !config.app.assistantLabel.trim()) throw new Error('Support requires app.assistantLabel.')
  if (!options.allowUnbound && config.capabilities?.tickets && (!config.support?.spaceId?.trim() || !config.support.ticketProviderBindingId?.trim())) {
    throw new Error('Tracked requests require a support space and provider binding.')
  }
  if (config.support?.requesterOrigin) {
    const url = new URL(config.support.requesterOrigin)
    const local = ['localhost', '127.0.0.1', '[::1]'].includes(url.hostname)
    if ((url.protocol !== 'https:' && !(local && url.protocol === 'http:')) || url.username || url.password || url.search || url.hash || url.pathname !== '/') {
      throw new Error('The requester origin must be a safe HTTPS origin.')
    }
  }
  const topics = config.support?.topics ?? []
  if (!Array.isArray(topics) || topics.length > 30 || topics.some(t => !t || typeof t.code !== 'string' || !t.code.trim() || typeof t.label !== 'string' || !t.label.trim()) || new Set(topics.map(t => t.code)).size !== topics.length) {
    throw new Error('Support topics must have unique codes and labels (maximum 30).')
  }
  return config
}

export function validateIntake(input: SupportCreateRequest, config: SupportExperienceConfig): void {
  // Required execution fields and server-declared intake bounds; no intent inference.
  if (!input.subject.trim() || input.subject.length > 240) throw new Error('Enter a subject of up to 240 characters.')
  if (!input.details.trim() || input.details.length > 12000) throw new Error('Enter details of up to 12,000 characters.')
  if (input.topic && !config.support?.topics?.some(topic => topic.code === input.topic)) throw new Error('Select a configured topic.')
}

export function resolveSupportPresentation(config: SupportExperienceConfig) {
  return {
    title: config.app.title ?? 'Help and support', assistantLabel: config.app.assistantLabel,
    requestLabel: config.support?.requestLabel ?? 'My Requests',
    welcomeMessage: config.copy?.welcomeMessage ?? 'Ask a question or submit a support request.',
    placeholder: config.copy?.placeholder ?? 'How can we help?',
    starterSuggestions: config.copy?.starterSuggestions ?? [],
    tickets: config.capabilities?.tickets === true,
    teamVisible: config.support?.teamVisible === true,
    attachments: config.capabilities?.attachments !== false,
    currentPage: config.capabilities?.currentPage === true,
    handover: config.capabilities?.handover === true,
    theme: config.theme ?? {},
  }
}
