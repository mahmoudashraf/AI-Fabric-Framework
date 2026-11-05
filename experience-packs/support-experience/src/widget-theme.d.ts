import type { SupportExperienceConfig } from './types'

export declare function applyThemeToElement(element: HTMLElement, theme?: SupportExperienceConfig['theme']): void
export declare function ensureWorkspaceStyleFonts(document: Document, preset?: unknown): void
export declare function bindWidgetColorScheme(element: HTMLElement, mode?: NonNullable<SupportExperienceConfig['theme']>['darkMode'], theme?: SupportExperienceConfig['theme']): () => void
