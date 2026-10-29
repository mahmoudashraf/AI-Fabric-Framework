export type PreviewInteractionPolicy = 'public' | 'login-required' | 'public-with-protected-capabilities';
export type PreviewCapabilities = {
  history: boolean;
  attachments: boolean;
  currentPage: boolean;
  sources: boolean;
  authentication: { signInAvailable: boolean };
  handover: { enabled: boolean; runtimeConfigured: boolean | null; userCanCancelPending: boolean; userCanResumeAi: boolean;
    intake?: { anonymousAllowed: boolean; fields: { key: string; label: string; type: 'TEXT' | 'EMAIL' | 'PHONE' | 'BOOLEAN' | 'SELECT'; required: boolean; consentRequired: boolean; purpose: string; maxLength: number; options: string[] }[] } };
};
export type PreviewTool = { label: string; icon: string };
export type PreviewGroup = { label: string; icon: string; contextLabel: string; availableWithoutAttachments: boolean; tools: PreviewTool[] };
export type PreviewRailItem = PreviewTool & {
  id: string; tone: 'primary' | 'teal' | 'violet' | 'amber' | 'neutral';
  action: 'open-tools' | 'open-documents' | 'prompt'; scope: 'default' | 'contextual'; requiresContext: boolean;
};
export type WorkspacePreviewConfiguration = {
  packCode: string;
  assistantLabel: string;
  theme: { primaryColor: string; logoUrl: string; borderRadius: string; fontFamily: string; darkMode: boolean | 'auto' };
  copy: { welcomeMessage: string; placeholder: string; emptyMessage: string; companionModeLabel: string; starterSuggestions: string[] };
  toolGroups: { initialScope: 'default' | 'contextual'; default: PreviewGroup; contextual: PreviewGroup };
  toolRail: { initiallyCollapsed: boolean; items: PreviewRailItem[] };
  currentPage: { enabled: boolean; contextLabel: string; maxChars: number; maxPages: number; maxTotalChars: number };
  launcher: boolean;
  interactionPolicy: PreviewInteractionPolicy;
  capabilities: PreviewCapabilities;
  rendererIds: string[];
  rendererContext: Record<string, boolean>;
  limitations: string[];
};
export type PreviewScenario = {
  appName: string; dark: boolean; layout: 'launcher' | 'embedded' | 'max' | 'standalone';
  sample: 'welcome' | 'conversation' | 'confirmation' | 'receipt' | 'fallback' | 'results' | 'pending' | 'human';
  signedIn: boolean; attachment: boolean;
};

export const previewObject = (value: unknown): Record<string, any> =>
  value && typeof value === 'object' && !Array.isArray(value) ? value as Record<string, any> : {};
export const previewText = (value: unknown) => typeof value === 'string' ? value : '';
const icons = new Set(['calendar', 'compare', 'details', 'documents', 'location', 'phone', 'search', 'shield', 'sparkles', 'tools']);
const icon = (value: unknown, fallback: string) => icons.has(previewText(value)) ? previewText(value) : fallback;
const bounded = (value: unknown, fallback: number, max: number) => typeof value === 'number' && Number.isFinite(value) ? Math.max(1, Math.min(Math.floor(value), max)) : fallback;
export function publicPreviewImage(value: unknown) {
  const raw = previewText(value);
  try {
    if (!/^https:\/\//i.test(raw) || /[\u0000-\u0020\\\u007f]/.test(raw) || raw.length > 2048) return '';
    const url = new URL(raw);
    const credentialQuery = [...url.searchParams.keys()].some(key => /(?:token|signature|credential|secret|api.?key|authorization)/i.test(key));
    return url.protocol === 'https:' && !url.username && !url.password && !url.hash && !credentialQuery ? raw : '';
  } catch { return ''; }
}

/** This allowlist protects the parent -> opaque iframe boundary, not model output. */
export function sanitizeWorkspacePreview(value: unknown): WorkspacePreviewConfiguration {
  const input = previewObject(value), theme = previewObject(input.theme), copy = previewObject(input.copy);
  const groups = previewObject(input.toolGroups), rail = previewObject(input.toolRail), page = previewObject(input.currentPage);
  const caps = previewObject(input.capabilities), auth = previewObject(caps.authentication), handover = previewObject(caps.handover);
  const group = (value: unknown, fallbackIcon: string): PreviewGroup => {
    const item = previewObject(value);
    return { label: previewText(item.label), icon: icon(item.icon, fallbackIcon), contextLabel: previewText(item.contextLabel),
      availableWithoutAttachments: item.availableWithoutAttachments === true,
      tools: Array.isArray(item.tools) ? item.tools.map((tool: unknown) => ({ label: previewText(previewObject(tool).label), icon: icon(previewObject(tool).icon, '') })).filter((tool: PreviewTool) => tool.label.trim()) : [] };
  };
  const limitations = Array.isArray(input.limitations) ? input.limitations.map(previewText).filter(Boolean) : [];
  const radius = previewText(theme.borderRadius), family = previewText(theme.fontFamily);
  if (radius && !/^(?:\d+(?:\.\d+)?)(?:px|rem|em|%)$/.test(radius)) limitations.push('Configured border radius is not supported in preview.');
  if (family && !/^[a-zA-Z0-9 ,"'_-]+$/.test(family)) limitations.push('Configured typography is not supported in preview.');
  return {
    packCode: previewText(input.packCode), assistantLabel: previewText(input.assistantLabel),
    theme: { primaryColor: /^#[0-9a-f]{6}$/i.test(previewText(theme.primaryColor)) ? theme.primaryColor : '#155eef',
      logoUrl: publicPreviewImage(theme.logoUrl), borderRadius: /^(?:\d+(?:\.\d+)?)(?:px|rem|em|%)$/.test(radius) ? radius : '0.5rem',
      fontFamily: /^[a-zA-Z0-9 ,"'_-]+$/.test(family) ? family : 'Inter, system-ui, sans-serif', darkMode: theme.darkMode === 'auto' ? 'auto' : theme.darkMode === true },
    copy: { welcomeMessage: previewText(copy.welcomeMessage), placeholder: previewText(copy.placeholder), emptyMessage: previewText(copy.emptyMessage),
      companionModeLabel: previewText(copy.companionModeLabel), starterSuggestions: Array.isArray(copy.starterSuggestions) ? copy.starterSuggestions.map(previewText) : [] },
    toolGroups: { initialScope: groups.initialScope === 'contextual' ? 'contextual' : 'default', default: group(groups.default, 'search'), contextual: group(groups.contextual, 'details') },
    toolRail: { initiallyCollapsed: rail.initiallyCollapsed === true, items: Array.isArray(rail.items) ? rail.items.flatMap((value: unknown, index: number) => {
      const item = previewObject(value);
      if (!['open-tools', 'open-documents', 'prompt'].includes(item.action)) return [];
      const tones = ['primary', 'teal', 'violet', 'amber'] as const;
      return [{ id: previewText(item.id), label: previewText(item.label), icon: icon(item.icon, item.action === 'open-documents' ? 'documents' : item.action === 'open-tools' ? 'tools' : 'sparkles'),
        tone: ['primary', 'teal', 'violet', 'amber', 'neutral'].includes(item.tone) ? item.tone : tones[index % tones.length],
        action: item.action, scope: item.scope === 'contextual' ? 'contextual' : 'default', requiresContext: item.requiresContext === true }];
    }) : [] },
    currentPage: { enabled: page.enabled === true && caps.currentPage === true, contextLabel: previewText(page.contextLabel),
      maxChars: bounded(page.maxChars, 1800, 20000), maxPages: bounded(page.maxPages, 3, 10), maxTotalChars: bounded(page.maxTotalChars, 10000, 50000) },
    launcher: input.launcher === true,
    interactionPolicy: ['public', 'login-required', 'public-with-protected-capabilities'].includes(input.interactionPolicy) ? input.interactionPolicy : 'login-required',
    capabilities: { history: caps.history === true, attachments: caps.attachments === true, sources: caps.sources === true, currentPage: caps.currentPage === true,
      authentication: { signInAvailable: auth.signInAvailable === true },
      handover: { enabled: handover.enabled === true && handover.runtimeConfigured !== false, runtimeConfigured: typeof handover.runtimeConfigured === 'boolean' ? handover.runtimeConfigured : null,
        userCanCancelPending: handover.enabled === true && handover.runtimeConfigured !== false && handover.userCanCancelPending === true, userCanResumeAi: handover.enabled === true && handover.runtimeConfigured !== false && handover.userCanResumeAi === true,
        intake: { anonymousAllowed: previewObject(handover.intake).anonymousAllowed !== false,
          fields: handover.enabled === true && Array.isArray(previewObject(handover.intake).fields) ? previewObject(handover.intake).fields.flatMap((value: unknown) => {
            const field = previewObject(value);
            if (!['TEXT', 'EMAIL', 'PHONE', 'BOOLEAN', 'SELECT'].includes(field.type)) return [];
            return [{ key: previewText(field.key), label: previewText(field.label), type: field.type, required: field.required === true, consentRequired: field.consentRequired === true,
              purpose: previewText(field.purpose), maxLength: bounded(field.maxLength, 500, 4096), options: Array.isArray(field.options) ? field.options.map(previewText) : [] }];
          }) : [] } } },
    rendererIds: Array.isArray(input.rendererIds) ? input.rendererIds.map(previewText).filter(Boolean) : [],
    rendererContext: Object.fromEntries(Object.entries(previewObject(input.rendererContext)).filter(([key, value]) => typeof value === 'boolean' && !/(?:token|auth|secret|private|credential|password)/i.test(key))) as Record<string, boolean>,
    limitations: [...new Set(limitations)],
  };
}

export function readPreviewScenario(value: unknown): PreviewScenario {
  const item = previewObject(value);
  return { appName: previewText(item.appName), dark: item.dark === true,
    layout: ['launcher', 'embedded', 'max', 'standalone'].includes(item.layout) ? item.layout : 'max',
    sample: ['welcome', 'conversation', 'confirmation', 'receipt', 'fallback', 'results', 'pending', 'human'].includes(item.sample) ? item.sample : 'welcome',
    signedIn: item.signedIn === true, attachment: item.attachment === true };
}
