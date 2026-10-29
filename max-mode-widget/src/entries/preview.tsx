import React, { useCallback, useEffect, useLayoutEffect, useMemo, useRef, useState } from 'react';
import { createRoot } from 'react-dom/client';
import { BookOpen, Calendar, FileText, GitCompare, LayoutGrid, MapPin, Phone, Search, Shield, Sparkles, X, Zap } from 'lucide-react';
import { getBundledPreviewPack } from '@engage-preview/packs';
import { MaxModeHeader } from '@/components/MaxModeHeader';
import { AssistantBrandMark } from '@/components/AssistantBrandMark';
import { CompanionDock } from '@/components/CompanionDock';
import { MessageList } from '@/components/Chat/MessageList';
import { Composer } from '@/components/Chat/Composer';
import { ConversationHistoryPanel } from '@/components/Conversations/ConversationHistoryPanel';
import { DocumentsView } from '@/components/DesktopContextPanel/DocumentsView';
import { ToolGroupTabs } from '@/components/ToolGroupTabs';
import { MobileToolRail } from '@/components/MobileToolRail';
import { QuickActionsMobileSheet } from '@/components/QuickActionsMobileSheet';
import { ConversationHandoverDialog } from '@/components/Handover/ConversationHandoverDialog';
import { PreviewRegisteredResult } from '@/preview/PreviewRegisteredResult';
import { readPreviewScenario, sanitizeWorkspacePreview, type PreviewScenario, type WorkspacePreviewConfiguration } from '@/preview/configuration';
import { applyThemeToElement } from '@/theme';
import { setWidgetConfig } from '@/config';
import type { MaxModeHostAuthenticationConfig, MaxModeToolScope } from '@/config';
import type { MaxModeController, MaxModeResolvedToolGroup, MaxModeResolvedToolRailItem } from '@/hooks/useMaxModeController';
import type { ChatMessage, ConversationControlState } from '@/types';
import '@/styles/index.css';

const noop = () => {};
const timestamp = '2026-01-01T10:00:00Z';
const icons = { calendar: Calendar, compare: GitCompare, details: FileText, documents: BookOpen, location: MapPin, phone: Phone, search: Search, shield: Shield, sparkles: Sparkles, tools: LayoutGrid };
const previewIcon = (name: string) => icons[name as keyof typeof icons] || Zap;
const resultStyles = () => ({ icon: Sparkles, bg: 'bg-white dark:bg-gray-900', border: 'border-transparent', text: 'text-foreground', iconColor: 'text-primary', label: 'Response', hideBadge: true });

function AppearancePreview({ config, scenario }: { config: WorkspacePreviewConfiguration; scenario: PreviewScenario }) {
  const wrapper = useRef<HTMLDivElement>(null), latest = useRef<HTMLDivElement>(null), end = useRef<HTMLDivElement>(null);
  const input = useRef<HTMLTextAreaElement>(null), documentsRef = useRef<HTMLDivElement>(null), documentsEnd = useRef<HTMLDivElement>(null);
  const [query, setQuery] = useState(''), [feedback, setFeedback] = useState(false), [focused, setFocused] = useState(scenario.sample !== 'welcome');
  const [expanded, setExpanded] = useState(false), [history, setHistory] = useState(false), [sources, setSources] = useState(false);
  const [toolsOpen, setToolsOpen] = useState(false), [collapsed, setCollapsed] = useState(config.toolRail.initiallyCollapsed);
  const [signedIn, setSignedIn] = useState(scenario.signedIn), [attachment, setAttachment] = useState(scenario.attachment);
  const [suggestionsOpen, setSuggestionsOpen] = useState(true), [handoverOpen, setHandoverOpen] = useState(false);
  const [control, setControl] = useState<ConversationControlState>(scenario.sample === 'human' ? 'HUMAN_ACTIVE' : scenario.sample === 'pending' ? 'HUMAN_PENDING' : 'AI_ACTIVE');
  const [confirmationStatus, setConfirmationStatus] = useState<Record<string, 'confirmed' | 'rejected'>>({});
  const [expandedActions, setExpandedActions] = useState<Record<string, number>>({});
  const showFeedback = useCallback(() => setFeedback(true), []);
  const fixtures = useMemo(() => getBundledPreviewPack(config.packCode)?.fixtures(config.rendererContext), [config.packCode, config.rendererContext]);
  const attachmentAllowed = config.capabilities.attachments || config.currentPage.enabled;
  const attachedItems = attachmentAllowed && attachment && fixtures ? [fixtures.attachment] : [];
  const contextualAvailable = attachedItems.length > 0 || config.toolGroups.contextual.availableWithoutAttachments;
  const [scope, setScope] = useState<MaxModeToolScope>((scenario.attachment || config.toolGroups.initialScope === 'contextual') && contextualAvailable ? 'contextual' : 'default');
  useEffect(() => { setScope((scenario.attachment || config.toolGroups.initialScope === 'contextual') && contextualAvailable ? 'contextual' : 'default'); }, [config.toolGroups.initialScope]);
  const activeScope = scope === 'contextual' && !contextualAvailable ? 'default' : scope;
  const activeContextLabel = attachedItems[0]?.contextLabel || config.toolGroups.contextual.contextLabel;
  const state = config.capabilities.handover.enabled ? control : 'AI_ACTIVE';
  const canAuthenticate = config.interactionPolicy !== 'public' && config.capabilities.authentication.signInAvailable;
  const locked = config.interactionPolicy === 'login-required' && !signedIn;
  const authentication: MaxModeHostAuthenticationConfig = {
    interactionPolicy: config.interactionPolicy, status: signedIn ? 'authenticated' : 'anonymous',
    signInAvailable: canAuthenticate, signOutAvailable: canAuthenticate,
    displayName: signedIn ? 'Sample visitor' : undefined,
    onSignIn: () => { setSignedIn(true); showFeedback(); }, onSignOut: () => { setSignedIn(false); showFeedback(); },
  };
  useEffect(() => { setCollapsed(config.toolRail.initiallyCollapsed); }, [config.toolRail.initiallyCollapsed]);
  useEffect(() => { if (!config.capabilities.handover.enabled) setHandoverOpen(false); }, [config.capabilities.handover.enabled]);
  useLayoutEffect(() => {
    if (wrapper.current) applyThemeToElement(wrapper.current, config.theme);
    document.documentElement.classList.toggle('dark', scenario.dark);
  }, [config.theme, scenario.dark]);
  const attach = () => { if (config.capabilities.attachments) { setAttachment(true); setScope('contextual'); } showFeedback(); };
  const attachPage = () => { if (config.currentPage.enabled) { setAttachment(true); setScope('contextual'); } showFeedback(); };
  const removeAttachment = () => { setAttachment(false); setScope('default'); };
  const selectScope = (next: MaxModeToolScope) => { if (next !== 'contextual' || contextualAvailable) setScope(next); };
  const openSources = () => { if (config.capabilities.sources) setSources(true); };
  const confirm = (id: string, accepted: boolean) => { setConfirmationStatus(current => ({ ...current, [id]: accepted ? 'confirmed' : 'rejected' })); showFeedback(); };
  const resume = () => { setControl('AI_ACTIVE'); setHandoverOpen(false); showFeedback(); };
  const groups: MaxModeResolvedToolGroup[] = (['default', 'contextual'] as const).map(groupScope => {
    const group = config.toolGroups[groupScope];
    return { scope: groupScope, label: group.label, icon: previewIcon(group.icon), available: groupScope === 'default' || contextualAvailable,
      actions: group.tools.map(tool => ({ label: tool.label, icon: previewIcon(tool.icon), query: '', mode: 'executor' as const, position: 'search' as const,
        color: 'text-primary', bg: 'bg-blue-500/10', border: 'border-blue-500/30' })) };
  }).filter(group => group.label || group.actions.length);
  const actions = groups.find(group => group.scope === activeScope)?.actions ?? [];
  const rail: MaxModeResolvedToolRailItem[] = config.toolRail.items
    .filter(item => item.action !== 'open-documents' || config.capabilities.sources)
    .map(item => ({ ...item, icon: previewIcon(item.icon), query: '', mode: 'executor', position: 'search' }));
  const hasRegisteredReceipt = fixtures?.presentations.some(fixture => fixture.sample === 'receipt' && config.rendererIds.includes(fixture.rendererId));

  const welcome: ChatMessage = { id: 'preview-welcome', type: 'ai', content: config.copy.welcomeMessage, timestamp, resultType: 'INFORMATION_PROVIDED' };
  const messages: ChatMessage[] = config.copy.welcomeMessage ? [welcome] : [];
  if (scenario.sample !== 'welcome' && fixtures) {
    messages.push({ id: 'preview-user', type: 'user', content: fixtures.userMessage, timestamp });
    messages.push({ id: 'preview-answer', type: 'ai', content: state === 'HUMAN_ACTIVE' ? 'Hi, I am Alex from the support team. How can I help?' : fixtures.answer, timestamp,
      participantRole: state === 'HUMAN_ACTIVE' ? 'human' : 'ai', participantDisplayName: state === 'HUMAN_ACTIVE' ? 'Alex - Sample support' : undefined,
      resultType: 'INFORMATION_PROVIDED', documents: config.capabilities.sources ? fixtures.documents : [],
      result: { type: 'INFORMATION_PROVIDED', success: true, data: { answer: fixtures.answer }, sanitizedPayload: { type: 'INFORMATION_PROVIDED', success: true } } as ChatMessage['result'],
    });
    if (scenario.sample === 'confirmation') messages.push({ id: 'preview-confirmation', type: 'ai', content: fixtures.confirmation, timestamp, resultType: 'CONFIRMATION_REQUIRED' });
    if (scenario.sample === 'fallback' || (scenario.sample === 'receipt' && !hasRegisteredReceipt && fixtures.receipt)) messages.push({ id: 'preview-generic-result', type: 'ai', content: '', timestamp, resultType: 'ACTION_EXECUTED',
      result: { type: 'ACTION_EXECUTED', success: true, sanitizedPayload: { type: 'ACTION_EXECUTED', success: true, data: scenario.sample === 'receipt' ? fixtures.receipt : fixtures.fallback } } });
  }
  const messageProps = {
    messages, latestMessageRef: latest, messagesEndRef: end, isLoading: false, getAiStyles: resultStyles,
    isPanelVisible: sources, attachedItems, confirmationStatus, expandedActions, debugEnabled: false,
    onOpenDebug: showFeedback, onResendAction: showFeedback, onReattachItem: attach, onOpenSourcesMobile: openSources, onOpenSourcesDesktop: openSources,
    onConfirm: confirm, onExpandActionResults: (id: string, count: number) => setExpandedActions(current => ({ ...current, [id]: count })),
    isItemAttached: () => attachment, onAttachActionResultItem: attach, onNextStepClick: showFeedback, onPresentationAsk: showFeedback,
    onAttachPresentationResult: attach, onDetachPresentationResult: removeAttachment, isPresentationResultAttached: () => attachment, onCustomerAccountConnect: showFeedback,
  };
  const handover = config.capabilities.handover;
  const dockController = {
    attachedItems, assistantLabel: config.assistantLabel, chatMessages: locked ? [] : messages, chatQuery: query, setChatQuery: setQuery,
    isInputFocused: focused, setIsInputFocused: setFocused, chatInputRef: input, latestMessageRef: latest, messagesEndRef: end,
    isLoading: false, oldConversationLocked: locked, debugEnabled: false, currentMode: 'executor', currentPosition: 'landing', searchCategory: null,
    handoverEnabled: handover.enabled && !locked, conversationControlState: state, isHandoverBusy: false,
    conversationControl: { safeHumanDisplayName: 'Alex - Sample support' },
    isHandoverDialogOpen: handoverOpen, setIsHandoverDialogOpen: setHandoverOpen, openHandover: () => setHandoverOpen(true),
    submitHandover: () => { setControl('HUMAN_PENDING'); showFeedback(); }, cancelHandover: resume, retryHandover: showFeedback, resumeAi: resume,
    handoverIntakeSchema: handover.intake,
    getResultStyles: resultStyles, isPanelVisible: sources, confirmationStatus, expandedActions,
    openDebugInspector: showFeedback, handleChatQuery: showFeedback, resendChatQuery: showFeedback,
    reattachItemWithToast: attach, openSourcesMobile: openSources, openSourcesDesktop: openSources,
    handleConfirmation: confirm, expandActionResults: messageProps.onExpandActionResults, isItemAttached: () => attachment,
    handleAttachActionResultItem: attach, handleActionPresentationAsk: showFeedback, handleAttachPresentationResult: attach,
    handleDetachPresentationResult: removeAttachment, isPresentationResultAttached: () => attachment, connectCustomerAccount: showFeedback, handleClarificationSubmit: showFeedback,
    currentPageAttachmentEnabled: config.currentPage.enabled && !locked, currentPageAttachments: attachedItems,
    currentPageAttachment: attachedItems[0], currentPageAttachmentMaxChars: config.currentPage.maxChars,
    currentPageAttachmentMaxPages: config.currentPage.maxPages, currentPageAttachmentMaxTotalChars: config.currentPage.maxTotalChars,
    currentPageAttachmentTotalChars: attachment ? 64 : 0, canAttachCurrentPage: true, isCapturingCurrentPage: false, attachCurrentPage: attachPage, removeCurrentPageAttachment: removeAttachment,
  } as unknown as MaxModeController;
  const docked = scenario.layout === 'embedded' && !expanded;
  const launcher = scenario.layout === 'launcher' && !expanded;
  const presentations = useMemo(() => fixtures?.presentations.filter(fixture => config.rendererIds.includes(fixture.rendererId))
    .map(fixture => ({ ...fixture, context: { ...fixture.context, ...config.rendererContext } })) ?? [], [fixtures, config.rendererIds, config.rendererContext]);
  const missingFixtures = config.rendererIds.filter(id => !fixtures?.presentations.some(fixture => fixture.rendererId === id));

  return <div ref={wrapper} className={`max-mode-widget-root h-screen overflow-hidden ${scenario.dark ? 'dark' : ''}`}>
    <div data-max-mode-view className="relative flex h-full min-w-0 flex-col bg-white text-gray-950 dark:bg-gray-950 dark:text-gray-100" style={{ fontFamily: config.theme.fontFamily }}>
      {(docked || launcher) ? <>
        <div className="border-b border-gray-200 p-6 dark:border-gray-800"><div className="flex items-center gap-3 text-lg font-bold"><FileText className="h-5 w-5 text-primary" />{scenario.appName || config.assistantLabel}</div></div>
        <div className="px-8 pt-10" aria-hidden="true"><div className="h-3 w-24 rounded bg-gray-200 dark:bg-gray-800" /><div className="mt-5 h-7 w-3/4 rounded bg-gray-100 dark:bg-gray-900" /><div className="mt-3 h-3 w-1/2 rounded bg-gray-100 dark:bg-gray-900" /></div>
        {launcher ? config.launcher ? <button aria-label="Open assistant" onClick={() => setExpanded(true)} className="fixed bottom-5 right-5 flex h-16 w-16 items-center justify-center rounded-full bg-primary text-white shadow-xl"><AssistantBrandMark logoUrl={config.theme.logoUrl} className="h-7 w-7" /></button>
          : <p role="status" className="p-6 text-sm">This pack uses the website dock instead of a launcher.</p>
          : <CompanionDock controller={dockController} onOpenMax={() => setExpanded(true)} contextLabel={config.currentPage.contextLabel}
            modeLabel={config.copy.companionModeLabel} placeholder={config.copy.placeholder} emptyMessage={config.copy.emptyMessage} />}
        {docked && canAuthenticate && !signedIn && <button className="fixed right-4 top-20 text-sm text-primary" onClick={() => { setSignedIn(true); showFeedback(); }}>Sign in</button>}
      </> : <>
        <div className="h-20 shrink-0"><MaxModeHeader assistantLabel={config.assistantLabel} onClose={() => setExpanded(false)} showCloseButton={scenario.layout === 'embedded' || scenario.layout === 'launcher'}
          authentication={authentication} handoverEnabled={handover.enabled && !locked} handoverState={state} handoverBusy={false} onOpenHandover={() => setHandoverOpen(true)} /></div>
        {scenario.layout === 'standalone' && <div className="break-words border-b border-gray-200 px-5 pb-3 text-sm font-semibold dark:border-gray-800">{scenario.appName || config.assistantLabel}</div>}
        {locked ? <div className="flex min-h-0 flex-1 flex-col items-center justify-center gap-4 p-6 text-center"><Shield className="h-8 w-8 text-primary" /><h2 className="text-lg font-semibold">Sign in to continue</h2>
          {canAuthenticate ? <button className="rounded-lg bg-primary px-4 py-2 text-white" onClick={() => { setSignedIn(true); showFeedback(); }}>Sign in</button> : <p role="status">Sign-in is unavailable in the selected contract.</p>}</div> : <>
          <div className="shrink-0 border-b border-gray-200 px-4 py-3 dark:border-gray-800">
            <ToolGroupTabs groups={groups} activeScope={activeScope} contextLabel={activeContextLabel} onSelect={selectScope} compact />
            <div className="mt-2 flex gap-2 overflow-x-auto pb-1" role="tabpanel" id={`max-mode-tools-${activeScope}`}>
              {actions.map((action, index) => <button key={`${index}-${action.label}`} data-max-mode-quick-action={action.label} type="button" onClick={showFeedback}
                className="flex min-h-11 shrink-0 items-center gap-2 rounded-lg border border-gray-200 px-3 py-2 text-xs font-semibold dark:border-gray-700"><action.icon className="h-4 w-4 shrink-0 text-primary" /><span className="max-w-64 break-words">{action.label}</span></button>)}
            </div>
            <div className="mt-2 flex flex-wrap items-center gap-2">
              {rail.length > 0 && <button type="button" className="md:hidden" aria-label="Open tools" onClick={() => setToolsOpen(true)}><LayoutGrid className="h-5 w-5 text-primary" /></button>}
            </div>
          </div>
          <div className="min-h-0 flex-1 overflow-y-auto" style={{ paddingBottom: 'var(--max-mode-composer-inset, 220px)' }}>
            <MessageList {...messageProps} containerClassName="px-4 py-5" />
            {scenario.sample === 'welcome' && !messages.length && <p className="p-6 text-center text-sm text-gray-500">{config.copy.emptyMessage}</p>}
            {(scenario.sample === 'results' || scenario.sample === 'receipt') && <div className="mx-auto max-w-3xl space-y-4 px-4 py-5">
              {presentations.filter(fixture => scenario.sample !== 'receipt' || fixture.sample === 'receipt').map(fixture =>
                <PreviewRegisteredResult key={fixture.fixtureId ?? fixture.rendererId} fixture={fixture} feedback={showFeedback} attachmentsEnabled={config.capabilities.attachments} />)}
              {scenario.sample === 'results' && presentations.length === 0 && <p role="status" className="text-sm text-gray-500">This pack uses the generic result presentation. Choose Generic result to inspect it.</p>}
              {scenario.sample === 'receipt' && !hasRegisteredReceipt && !fixtures?.receipt && <p role="status" className="text-sm text-gray-500">No receipt fixture is available for the currently enabled presentations.</p>}
            </div>}
          </div>
          <Composer attachedItems={attachedItems} onRemoveAttachment={removeAttachment} searchCategory={null} onClearSearchCategory={noop} onRemoveAiSearch={noop}
            suggestions={config.copy.starterSuggestions} showSuggestions={suggestionsOpen} isLoadingSuggestions={false} onDismissSuggestions={() => setSuggestionsOpen(false)} onShowSuggestions={() => setSuggestionsOpen(true)}
            onSuggestionSelect={value => { setQuery(value); showFeedback(); }} oldConversationLocked={false} onStartNewConversation={showFeedback} onOpenHistory={() => setHistory(true)}
            chatQuery={query} onChatQueryChange={setQuery} isInputFocused={focused} onInputFocusChange={setFocused} chatInputRef={input} isLoading={false}
            currentPosition="landing" currentMode="executor" availableModes={['executor']} onModeChange={noop} debugEnabled={false} conversationsEnabled={config.capabilities.history} onOpenDebug={showFeedback} onSubmit={showFeedback}
            handoverEnabled={handover.enabled} handoverState={state} handoverDisplayName="Alex - Sample support" handoverCanCancel={handover.userCanCancelPending} handoverCanResume={handover.userCanResumeAi}
            handoverBusy={false} onOpenHandover={() => setHandoverOpen(true)} onCancelHandover={resume} onRetryHandover={showFeedback} onResumeAi={resume} placeholder={config.copy.placeholder} />
          <MobileToolRail items={rail} activeToolScope={activeScope} contextualToolsAvailable={contextualAvailable} contextDocumentsCount={config.capabilities.sources ? fixtures?.documents.length ?? 0 : 0}
            collapsed={collapsed} onCollapsedChange={setCollapsed} onOpenTools={next => { selectScope(next); setToolsOpen(true); }} onOpenDocuments={openSources} onPrompt={showFeedback} />
          <QuickActionsMobileSheet isOpen={toolsOpen} setIsOpen={setToolsOpen} quickActions={actions} toolGroups={groups} activeToolScope={activeScope} activeContextLabel={activeContextLabel} onSelectToolScope={selectScope} onQuickAction={showFeedback} />
        </>}
      </>}
      {config.capabilities.history && <ConversationHistoryPanel isOpen={history} onClose={() => setHistory(false)} conversations={[{ id: 'preview-history', title: 'Sample conversation', createdAt: timestamp, turnsCount: 2 }]}
        isLoadingConversations={false} currentConversationId="preview-history" onStartNewConversation={showFeedback} onOpenConversation={() => { setHistory(false); showFeedback(); }}
        onDeleteConversation={(_, event) => { event.stopPropagation(); showFeedback(); }} integrationMode={config.interactionPolicy === 'login-required' ? 'public-runtime-authenticated' : 'public-runtime-anonymous'} />}
      {config.capabilities.sources && sources && <div className="fixed inset-2 z-[105] flex flex-col rounded-lg border border-gray-200 bg-white shadow-xl dark:border-gray-700 dark:bg-gray-950" role="dialog" aria-label="Sample sources">
        <div className="flex items-center justify-between border-b border-gray-200 p-3 dark:border-gray-700"><strong>Sources</strong><button aria-label="Close sources" onClick={() => setSources(false)}><X className="h-5 w-5" /></button></div>
        <DocumentsView contextDocuments={fixtures?.documents ?? []} focusedMessageId="preview-answer" newDocuments={[]} viewedDocumentIds={new Set()} contextPanelRef={documentsRef} contextPanelEndRef={documentsEnd}
          isItemAttached={() => attachment} onOpenProductDetails={showFeedback} onAttachDocument={attach} /></div>}
      {!docked && handover.enabled && <ConversationHandoverDialog open={handoverOpen} onOpenChange={setHandoverOpen} state={state} displayName="Alex - Sample support" fields={handover.intake?.fields ?? []} anonymousAllowed={handover.intake?.anonymousAllowed !== false}
        canCancel={handover.userCanCancelPending} canResume={handover.userCanResumeAi} busy={false} onSubmit={() => { setControl('HUMAN_PENDING'); showFeedback(); }} onCancel={resume} onRetry={showFeedback} onResume={resume} />}
      {(config.limitations.length > 0 || missingFixtures.length > 0) && <details className="fixed bottom-1 left-1 z-[106] max-w-[90%] rounded border border-amber-300 bg-amber-50 p-2 text-xs text-amber-950"><summary>Preview limitations</summary>
        {[...config.limitations, ...missingFixtures.map(() => 'A configured renderer needs a trusted bundled preview fixture.')].map((item, index) => <p key={index}>{item}</p>)}</details>}
      {feedback && <button role="status" className="fixed left-1/2 top-2 z-[110] w-max max-w-[90%] -translate-x-1/2 rounded-lg border border-gray-300 bg-white px-3 py-2 text-xs text-gray-700 shadow-lg dark:bg-gray-900 dark:text-gray-200" onClick={() => setFeedback(false)}>Preview only. Nothing was sent or saved.</button>}
    </div>
  </div>;
}

// Deliberately no live controller, installer, auth provider, transport or storage.
const root = createRoot(document.getElementById('preview-root')!);
window.addEventListener('message', event => {
  if (event.source !== window.parent || event.data?.type !== 'loomai:engage-preview:configure') return;
  const config = sanitizeWorkspacePreview(event.data.configuration);
  const scenario = readPreviewScenario(event.data.scenario);
  getBundledPreviewPack(config.packCode)?.register();
  setWidgetConfig({ apiConfig: { chatBaseUrl: '', conversationHandover: config.capabilities.handover }, theme: config.theme,
    host: { assistantLabel: config.assistantLabel }, features: { cart: false, conversations: config.capabilities.history } });
  root.render(<AppearancePreview key={`${config.packCode}:${JSON.stringify(scenario)}`} config={config} scenario={scenario} />);
});
window.parent.postMessage({ type: 'loomai:engage-preview:ready' }, '*');
