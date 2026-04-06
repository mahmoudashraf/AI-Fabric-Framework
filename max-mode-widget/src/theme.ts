/**
 * Theme engine — maps the MaxModeThemeConfig into CSS custom properties
 * that get applied to the widget's Shadow DOM host (or wrapper div).
 */

import type { MaxModeThemeConfig } from "./config";

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

  return `:host {\n  ${vars.join("\n  ")}\n}`;
}

/**
 * Apply theme CSS variables to a given container element.
 * Used when not rendering inside Shadow DOM (e.g. React entry).
 */
export function applyThemeToElement(
  element: HTMLElement,
  theme?: MaxModeThemeConfig,
): void {
  if (!theme) return;

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
