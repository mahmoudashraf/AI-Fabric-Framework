import { createElement, useLayoutEffect, useRef, useState } from 'react';
import type { MaxModeActionPresentationCommands, MaxModeActionPresentationComponentProps } from '@/actionPresentation';
import type { PreviewPresentationFixture } from './fixtures';

/** Use the registered pack element, with every command replaced by local feedback. */
export function PreviewRegisteredResult({ fixture, feedback, attachmentsEnabled }: {
  fixture: PreviewPresentationFixture; feedback: () => void; attachmentsEnabled: boolean;
}) {
  const element = useRef<HTMLElement & { presentation?: Omit<MaxModeActionPresentationComponentProps, 'commands'>; commands?: MaxModeActionPresentationCommands }>(null);
  const [selected, setSelected] = useState<string[]>([]);
  useLayoutEffect(() => {
    if (!element.current) return;
    element.current.commands = {
      ask: async () => feedback(), navigate: feedback,
      attachResult: key => { if (attachmentsEnabled) setSelected(current => [...new Set([...current, key])]); feedback(); },
      detachResult: key => { setSelected(current => current.filter(value => value !== key)); feedback(); },
    };
    element.current.presentation = { ...fixture, selectedResultKeys: selected };
  }, [fixture, selected, feedback, attachmentsEnabled]);
  return createElement(fixture.elementName, { ref: element, 'data-preview-renderer': fixture.rendererId });
}
