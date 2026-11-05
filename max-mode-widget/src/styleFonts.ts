import manrope from './assets/fonts/Manrope.woff2?url';
import editorial from './assets/fonts/InstrumentSerif.woff2?url';
import grotesk from './assets/fonts/SpaceGrotesk.woff2?url';
import mono from './assets/fonts/IBMPlexMono.woff2?url';
import friendly from './assets/fonts/Nunito.woff2?url';
import { resolveWorkspaceStylePreset } from './stylePresets';

const definitions = [
  ['LoomAI Manrope', manrope, '200 800'], ['LoomAI Editorial', editorial, '400'],
  ['LoomAI Grotesk', grotesk, '300 700'], ['LoomAI Mono', mono, '400'], ['LoomAI Friendly', friendly, '200 900'],
] as const;
const installed = new WeakMap<Document, Set<string>>();

/** FontFace crosses Shadow DOM boundaries without injecting global component styles. */
export function ensureWorkspaceStyleFonts(document: Document, preset?: unknown): void {
  const style = resolveWorkspaceStylePreset(preset);
  if (style.id === 'current' || !document.defaultView?.FontFace || !document.fonts) return;
  let names = installed.get(document);
  if (!names) { names = new Set(); installed.set(document, names); }
  for (const [family, url, weight] of definitions) {
    if (!(style.font.includes(family) || style.displayFont.includes(family)) || names.has(family)) continue;
    names.add(family);
    try {
      const font = new document.defaultView.FontFace(family, `url(${JSON.stringify(url)})`, { weight, display: 'swap' });
      document.fonts.add(font);
      void font.load().catch(() => { /* A CSP/font failure keeps the declared system fallback. */ });
    } catch { /* Existing customer CSP remains authoritative. */ }
  }
}
