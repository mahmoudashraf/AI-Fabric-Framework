import React, { useLayoutEffect, useRef, useState } from 'react';
import { createRoot } from 'react-dom/client';
import { FileText, Search, Sparkles } from 'lucide-react';
import { MaxModeHeader } from '@/components/MaxModeHeader';
import { AssistantBrandMark } from '@/components/AssistantBrandMark';
import { MessageList } from '@/components/Chat/MessageList';
import { ComposerInputRow } from '@/components/Chat/Composer/ComposerInputRow';
import { applyThemeToElement } from '@/theme';
import { setWidgetConfig } from '@/config';
import type { ChatMessage } from '@/types';
import '@/styles/index.css';

type Preview = {
  assistantLabel: string; appName: string; logoUrl: string; primaryColor: string;
  dark: boolean; welcomeMessage: string; placeholder: string; toolLabels: string[];
  layout: 'embedded' | 'max' | 'standalone'; sample: 'welcome' | 'conversation' | 'human';
};
const noop = () => {};
const timestamp = '2026-01-01T10:00:00Z';

function validPreview(value: unknown): value is Preview {
  if (!value || typeof value !== 'object' || Array.isArray(value)) return false;
  const item = value as Preview;
  if (!['assistantLabel', 'appName', 'logoUrl', 'primaryColor', 'welcomeMessage', 'placeholder']
    .every(key => typeof item[key as keyof Preview] === 'string' && String(item[key as keyof Preview]).length <= 2048)) return false;
  if (!/^#[0-9a-f]{6}$/i.test(item.primaryColor) || typeof item.dark !== 'boolean'
    || !['embedded', 'max', 'standalone'].includes(item.layout)
    || !['welcome', 'conversation', 'human'].includes(item.sample)
    || !Array.isArray(item.toolLabels) || item.toolLabels.length > 8
    || !item.toolLabels.every(label => typeof label === 'string' && label.length <= 300)) return false;
  if (item.logoUrl) {
    try {
      const url = new URL(item.logoUrl);
      if (url.protocol !== 'https:' || url.username || url.password || url.hash) return false;
    } catch { return false; }
  }
  return true;
}

function AppearancePreview({ config }: { config: Preview }) {
  const wrapper = useRef<HTMLDivElement>(null);
  const latest = useRef<HTMLDivElement>(null);
  const end = useRef<HTMLDivElement>(null);
  const input = useRef<HTMLTextAreaElement>(null);
  const [query, setQuery] = useState('');
  const [feedback, setFeedback] = useState(false);
  const [focused, setFocused] = useState(false);
  const [expanded, setExpanded] = useState(false);
  useLayoutEffect(() => {
    if (wrapper.current) applyThemeToElement(wrapper.current, { primaryColor: config.primaryColor });
    document.documentElement.classList.toggle('dark', config.dark);
  }, [config]);

  const welcome: ChatMessage = { id: 'preview-welcome', type: 'ai',
    content: config.welcomeMessage || 'How can I help you today?', timestamp, resultType: 'INFORMATION_PROVIDED' };
  const messages: ChatMessage[] = config.sample === 'welcome' ? [welcome] : [welcome,
    { id: 'preview-user', type: 'user', content: 'Can you help me explore my options?', timestamp },
    { id: 'preview-reply', type: 'ai', content: config.sample === 'human'
      ? "Hi, I'm Alex from the support team. How can I help?"
      : 'We can explore the available information together. What matters most to you?', timestamp,
      participantRole: config.sample === 'human' ? 'human' : 'ai',
      participantDisplayName: config.sample === 'human' ? 'Alex - Support' : undefined, resultType: 'INFORMATION_PROVIDED' },
  ];
  const docked = config.layout === 'embedded' && !expanded;
  const content = <MessageList containerClassName="h-full overflow-y-auto px-4 py-6" messages={messages}
    latestMessageRef={latest} messagesEndRef={end} isLoading={false}
    getAiStyles={() => ({ icon: Sparkles, bg: 'bg-white dark:bg-gray-900', border: 'border-transparent',
      text: 'text-foreground', iconColor: 'text-primary', label: 'Response', hideBadge: true })}
    isPanelVisible={false} attachedItems={[]} confirmationStatus={{}} expandedActions={{}} debugEnabled={false}
    onOpenDebug={noop} onResendAction={noop} onReattachItem={noop} onOpenSourcesMobile={noop}
    onOpenSourcesDesktop={noop} onConfirm={noop} onExpandActionResults={noop} isItemAttached={() => false}
    onAttachActionResultItem={noop} onNextStepClick={noop} onPresentationAsk={noop}
    onAttachPresentationResult={noop} onDetachPresentationResult={noop} isPresentationResultAttached={() => false}
    onCustomerAccountConnect={noop} />;
  const composer = <ComposerInputRow searchCategory={null} hasAiSearch={false} aiSearchCategory={null}
    onClearSearchCategory={noop} onRemoveAiSearch={noop} oldConversationLocked={false} onOpenHistory={noop}
    chatQuery={query} onChatQueryChange={setQuery} isInputFocused={focused} onInputFocusChange={setFocused}
    chatInputRef={input} isLoading={false} currentPosition="landing" currentMode="executor" availableModes={['executor']}
    onModeChange={noop} debugEnabled={false} conversationsEnabled={false} onOpenDebug={noop}
    nonAiAttachmentsCount={0} onSubmit={() => setFeedback(true)} conversationControlState="AI_ACTIVE"
    placeholder={config.placeholder || 'Ask a question...'} />;

  return <div ref={wrapper} className={`max-mode-widget-root h-screen overflow-hidden ${config.dark ? 'dark' : ''}`}>
    <div className="h-full bg-white text-gray-950 dark:bg-gray-950 dark:text-gray-100">
      {docked ? <>
        <div className="border-b border-gray-200 p-6 dark:border-gray-800">
          <div className="flex items-center gap-3 text-lg font-bold"><FileText className="h-5 w-5 text-primary" />{config.appName || 'Your website'}</div>
        </div>
        <div className="px-8 pt-10"><div className="h-3 w-24 rounded bg-gray-200 dark:bg-gray-800" />
          <div className="mt-5 h-7 w-3/4 rounded bg-gray-100 dark:bg-gray-900" />
          <div className="mt-3 h-3 w-1/2 rounded bg-gray-100 dark:bg-gray-900" />
          <div className="mt-8 grid grid-cols-3 gap-3">{[1, 2, 3].map(id => <div key={id} className="h-24 rounded-lg border border-gray-200 bg-gray-50 dark:border-gray-800 dark:bg-gray-900" />)}</div>
        </div>
        <section aria-label="Embedded workspace preview" className="absolute bottom-4 left-4 right-4 overflow-hidden rounded-lg border border-gray-200 bg-white shadow-xl dark:border-gray-700 dark:bg-gray-950">
          <div className="flex items-center justify-between border-b border-gray-200 px-4 py-3 dark:border-gray-800">
            <div className="flex min-w-0 items-center gap-2"><AssistantBrandMark logoUrl={config.logoUrl} className="h-7 w-7 text-primary" /><strong className="truncate text-sm">{config.assistantLabel}</strong></div>
            <button type="button" className="rounded border border-gray-300 px-3 py-1 text-xs font-semibold text-primary dark:border-gray-700" onClick={() => setExpanded(true)}>MAX</button>
          </div>
          <div className="h-40">{content}</div><div className="border-t border-gray-200 p-3 dark:border-gray-800">{composer}</div>
        </section>
      </> : <>
        <MaxModeHeader assistantLabel={config.assistantLabel} onClose={() => setExpanded(false)}
          showCloseButton={config.layout === 'embedded'} handoverEnabled={config.sample === 'human'}
          handoverState={config.sample === 'human' ? 'HUMAN_ACTIVE' : 'AI_ACTIVE'} handoverBusy={false} onOpenHandover={noop} />
        <div className="absolute inset-0 flex flex-col pt-20">
          {config.layout === 'standalone' && <div className="break-words border-b border-gray-200 px-5 pb-4 text-sm font-semibold dark:border-gray-800">{config.appName || config.assistantLabel}</div>}
          {config.toolLabels.length > 0 && <div className="flex flex-wrap gap-2 border-b border-gray-200 px-5 pb-4 dark:border-gray-800">{config.toolLabels.map(label => <button key={label}
            onClick={() => setFeedback(true)} className="flex items-center gap-2 rounded-lg border border-gray-200 px-3 py-2 text-xs font-semibold dark:border-gray-700"><Search className="h-4 w-4 text-primary" />{label}</button>)}</div>}
          <div className="min-h-0 flex-1">{content}</div>
          <div className="border-t border-gray-200 p-4 dark:border-gray-800">{composer}</div>
        </div>
      </>}
      {feedback && <div role="status" className="fixed bottom-2 left-1/2 z-[110] -translate-x-1/2 rounded-lg border border-gray-300 bg-white px-3 py-2 text-xs text-gray-700 shadow-lg dark:bg-gray-900 dark:text-gray-200" onClick={() => setFeedback(false)}>Visual preview only. Nothing was sent.</div>}
    </div>
  </div>;
}

// This entry has no live controller, transport, credentials, storage or installation bootstrap.
const root = createRoot(document.getElementById('preview-root')!);
window.addEventListener('message', event => {
  if (event.source !== window.parent || event.data?.type !== 'loomai:engage-preview:configure'
    || !validPreview(event.data.configuration)) return;
  const config = event.data.configuration;
  setWidgetConfig({ apiConfig: { chatBaseUrl: '' }, theme: { primaryColor: config.primaryColor, logoUrl: config.logoUrl },
    host: { assistantLabel: config.assistantLabel }, features: { cart: false, conversations: false } });
  root.render(<AppearancePreview config={event.data.configuration} />);
});
window.parent.postMessage({ type: 'loomai:engage-preview:ready' }, '*');
