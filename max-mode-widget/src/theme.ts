/**
 * Theme engine — maps the MaxModeThemeConfig into CSS custom properties
 * that get applied to the widget's Shadow DOM host (or wrapper div).
 */

import type { MaxModeThemeConfig } from "./config";
import { resolveWorkspaceStylePreset } from './stylePresets';

/** Convert a hex/rgb color to HSL components for Tailwind's hsl() usage */
function hexToHSL(hex: string): string | null {
  // Accept hex (#fff, #ffffff) or return null
  let r: number, g: number, b: number;

  const shorthand = /^#?([a-f\d])([a-f\d])([a-f\d])$/i;
  const fullHex = hex.replace(shorthand, (_m, r, g, b) => r + r + g + g + b + b);
  const result = /^#?([a-f\d]{2})([a-f\d]{2})([a-f\d]{2})$/i.exec(fullHex);

  if (!result) return null;

  r = parseInt(result[1], 16) / 255;
  g = parseInt(result[2], 16) / 255;
  b = parseInt(result[3], 16) / 255;

  const max = Math.max(r, g, b);
  const min = Math.min(r, g, b);
  let h = 0;
  let s = 0;
  const l = (max + min) / 2;

  if (max !== min) {
    const d = max - min;
    s = l > 0.5 ? d / (2 - max - min) : d / (max + min);
    switch (max) {
      case r:
        h = ((g - b) / d + (g < b ? 6 : 0)) / 6;
        break;
      case g:
        h = ((b - r) / d + 2) / 6;
        break;
      case b:
        h = ((r - g) / d + 4) / 6;
        break;
    }
  }

  return `${Math.round(h * 360)} ${Math.round(s * 100)}% ${Math.round(l * 100)}%`;
}

/**
 * Build a CSS string of custom properties for the given theme config.
 * These variables are consumed by our Tailwind color definitions.
 */
export function buildThemeCSS(theme?: MaxModeThemeConfig): string {
  const vars: string[] = [];

  if (theme?.primaryColor) {
    const hsl = hexToHSL(theme.primaryColor);
    if (hsl) {
      vars.push(`--mxw-primary: ${hsl};`);
    }
  }

  if (theme?.borderRadius) {
    vars.push(`--mxw-radius: ${theme.borderRadius};`);
  }

  if (theme?.fontFamily) {
    vars.push(`--mxw-font-family: ${theme.fontFamily};`);
  }

  // Dark mode defaults
  if (theme?.darkMode === true) {
    vars.push(
      `--mxw-background: 222.2 84% 4.9%;`,
      `--mxw-foreground: 210 40% 98%;`,
      `--mxw-card: 222.2 84% 4.9%;`,
      `--mxw-card-foreground: 210 40% 98%;`,
      `--mxw-popover: 222.2 84% 4.9%;`,
      `--mxw-popover-foreground: 210 40% 98%;`,
      `--mxw-muted: 217.2 32.6% 17.5%;`,
      `--mxw-muted-foreground: 215 20.2% 65.1%;`,
      `--mxw-border: 217.2 32.6% 17.5%;`,
      `--mxw-input: 217.2 32.6% 17.5%;`,
    );
  }

  if (vars.length === 0) return "";

  // Root defaults are injected later; keep configured values authoritative there.
  return `:host, :host .max-mode-widget-root {\n  ${vars.join("\n  ")}\n}`;
}

const luminance = (hex: string): number => {
  const values = hex.replace('#', '').match(/../g)!.map(value => parseInt(value, 16) / 255).map(value => value <= 0.04045 ? value / 12.92 : ((value + 0.055) / 1.055) ** 2.4);
  return values[0] * 0.2126 + values[1] * 0.7152 + values[2] * 0.0722;
};
function onColour(hex: string): string {
  return luminance(hex) > 0.179 ? '#101412' : '#ffffff';
}

export function workspaceStyleVariables(theme: MaxModeThemeConfig | undefined, dark: boolean): Record<string, string> {
  const style = resolveWorkspaceStylePreset(theme?.stylePreset);
  if (style.id === 'current') return {};
  const palette = dark ? style.dark : style.light;
  const configured = theme?.primaryColor;
  // A saved preset's light accent follows its dark counterpart; a genuine brand override stays explicit.
  const accent = configured && /^#[0-9a-f]{6}$/i.test(configured) && configured.toLowerCase() !== style.light.accent.toLowerCase()
    ? configured : palette.accent;
  const variables: Record<string, string> = {
    '--mxw-style-surface': palette.surface, '--mxw-style-canvas': palette.canvas,
    '--mxw-style-text': palette.text, '--mxw-style-muted': palette.muted, '--mxw-style-border': palette.border,
    '--mxw-style-accent': accent, '--mxw-style-on-accent': onColour(accent), '--mxw-style-link': palette.accent,
    '--mxw-style-tint': palette.tint, '--mxw-style-user': palette.user, '--mxw-style-human': palette.human,
    '--mxw-style-font': theme?.fontFamily || style.font, '--mxw-style-display-font': theme?.fontFamily && theme.fontFamily !== style.font ? theme.fontFamily : style.displayFont,
    '--mxw-style-radius': theme?.borderRadius || style.radius, '--mxw-style-elevation': style.elevation,
    '--mxw-background': hexToHSL(palette.surface)!, '--mxw-foreground': hexToHSL(palette.text)!,
    '--mxw-card': hexToHSL(palette.surface)!, '--mxw-card-foreground': hexToHSL(palette.text)!,
    '--mxw-popover': hexToHSL(palette.surface)!, '--mxw-popover-foreground': hexToHSL(palette.text)!,
    '--mxw-muted': hexToHSL(palette.canvas)!, '--mxw-muted-foreground': hexToHSL(palette.muted)!,
    '--mxw-border': hexToHSL(palette.border)!, '--mxw-input': hexToHSL(palette.border)!,
    '--mxw-primary': hexToHSL(accent)!, '--mxw-primary-foreground': hexToHSL(onColour(accent))!,
    '--mxw-secondary': hexToHSL(palette.tint)!, '--mxw-secondary-foreground': hexToHSL(palette.text)!,
    '--mxw-accent': hexToHSL(palette.tint)!, '--mxw-accent-foreground': hexToHSL(palette.text)!,
    '--mxw-ring': hexToHSL(palette.accent)!, '--mxw-font-family': theme?.fontFamily || style.font,
    '--mxw-radius': theme?.borderRadius || style.radius,
  };
  return variables;
}

const managedStyleProperties = new WeakMap<HTMLElement, string[]>();

/**
 * Apply theme CSS variables to a given container element.
 * Used when not rendering inside Shadow DOM (e.g. React entry).
 */
export function applyThemeToElement(
  element: HTMLElement,
  theme?: MaxModeThemeConfig,
): void {
  for (const name of managedStyleProperties.get(element) || []) element.style.removeProperty(name);
  const variables = workspaceStyleVariables(theme, element.classList.contains('dark'));
  managedStyleProperties.set(element, Object.keys(variables));
  element.dataset.mxwStyle = resolveWorkspaceStylePreset(theme?.stylePreset).id;
  for (const [name, value] of Object.entries(variables)) element.style.setProperty(name, value);
  if (!theme || element.dataset.mxwStyle !== 'current') return;

  if (theme.primaryColor) {
    const hsl = hexToHSL(theme.primaryColor);
    if (hsl) element.style.setProperty("--mxw-primary", hsl);
  }
  if (theme.borderRadius) {
    element.style.setProperty("--mxw-radius", theme.borderRadius);
  }
  if (theme.fontFamily) {
    element.style.setProperty("--mxw-font-family", theme.fontFamily);
  }
}

export function bindWidgetColorScheme(element: HTMLElement, mode?: MaxModeThemeConfig['darkMode'], theme?: MaxModeThemeConfig): () => void {
  const media = element.ownerDocument.defaultView?.matchMedia('(prefers-color-scheme: dark)');
  const previous = element.classList.contains('dark');
  const update = () => {
    element.classList.toggle('dark', mode === true || (mode === 'auto' && media?.matches === true));
    if (theme) applyThemeToElement(element, theme);
  };
  update();
  if (mode === 'auto') media?.addEventListener('change', update);
  return () => {
    if (mode === 'auto') media?.removeEventListener('change', update);
    element.classList.toggle('dark', previous);
  };
}
