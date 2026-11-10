import { useEffect, useRef, type CSSProperties, type ReactNode } from 'react';

export type ControlledConversationMessage = {
  id: string;
  role: 'user' | 'assistant';
  text: string;
};

export type ControlledConversationProps<T extends ControlledConversationMessage> = {
  messages: readonly T[];
  value: string;
  onValueChange: (value: string) => void;
  onSubmit: (value: string) => void;
  onCancel?: () => void;
  busy: boolean;
  disabled?: boolean;
  error?: string | null;
  renderMessage?: (message: T) => ReactNode;
  composerExtras?: ReactNode;
  submitLabel?: string;
  className?: string;
  style?: CSSProperties;
};

// Transport, persistence and trusted rich-message rendering belong to the host.
export function ControlledConversation<T extends ControlledConversationMessage>({
  messages, value, onValueChange, onSubmit, onCancel, busy, disabled, error,
  renderMessage, composerExtras, submitLabel = 'Send', className, style,
}: ControlledConversationProps<T>) {
  const log = useRef<HTMLDivElement>(null);
  const end = messages[messages.length - 1];
  useEffect(() => {
    if (log.current) log.current.scrollTop = log.current.scrollHeight;
  }, [messages.length, end?.text, busy]);
  const submit = () => {
    if (!busy && !disabled && value.trim()) onSubmit(value.trim());
  };
  return <section className={className} aria-label="Conversation" style={{ display: 'flex', flexDirection: 'column', flex: 1, minHeight: 0, minWidth: 0, ...style }}>
    <div ref={log} role="log" aria-label="Conversation messages" aria-live="polite" aria-relevant="additions text" style={{ flex: 1, minHeight: 0, overflow: 'auto', overflowWrap: 'anywhere' }}>
      {messages.map(message => <article key={message.id} aria-label={message.role === 'user' ? 'You' : 'Assistant'} style={{ padding: '12px 0', whiteSpace: 'pre-wrap' }}>
        {renderMessage ? renderMessage(message) : <><strong>{message.role === 'user' ? 'You' : 'Assistant'}</strong><div>{message.text}</div></>}
      </article>)}
      {busy && <div role="status" style={{ padding: '12px 0' }}>Working...</div>}
    </div>
    {error && <div role="alert" style={{ whiteSpace: 'pre-wrap', overflowWrap: 'anywhere', padding: '8px 0' }}>{error}</div>}
    <form onSubmit={event => { event.preventDefault(); submit(); }} style={{ flexShrink: 0, display: 'flex', flexDirection: 'column', gap: 8, paddingTop: 12 }}>
      {composerExtras}
      <textarea aria-label="Message" value={value} onChange={event => onValueChange(event.target.value)} disabled={busy || disabled} rows={3}
        onKeyDown={event => { if (event.key === 'Enter' && !event.shiftKey && !event.nativeEvent.isComposing) { event.preventDefault(); submit(); } }}
        style={{ resize: 'vertical', minHeight: 64, maxHeight: 180, width: '100%', boxSizing: 'border-box', font: 'inherit', color: 'inherit', background: 'transparent', border: '1px solid currentColor', borderRadius: 4, padding: 10 }} />
      <div style={{ display: 'flex', gap: 8, justifyContent: 'flex-end' }}>
        {busy && onCancel && <button type="button" onClick={onCancel}>Cancel</button>}
        <button type="submit" disabled={busy || disabled || !value.trim()}>{submitLabel}</button>
      </div>
    </form>
  </section>;
}
