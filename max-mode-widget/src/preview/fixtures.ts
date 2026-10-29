import type { MaxModeActionPresentationComponentProps } from '@/actionPresentation';
import type { AttachedItem, Document } from '@/types';

export type PreviewPresentationFixture = Omit<MaxModeActionPresentationComponentProps, 'commands' | 'selectedResultKeys'> & { elementName: string; fixtureId?: string; sample?: 'result' | 'receipt' };
export type PreviewPackFixtures = {
  userMessage: string; answer: string; confirmation: string; documents: Document[]; attachment: AttachedItem;
  fallback: Record<string, unknown>; receipt?: Record<string, unknown>; presentations: PreviewPresentationFixture[];
};
