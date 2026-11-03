import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react-swc';
import cssInjectedByJsPlugin from 'vite-plugin-css-injected-by-js';
import path from 'node:path';

export default defineConfig({
  plugins: [react(), cssInjectedByJsPlugin(), {
    name: 'trusted-engage-preview-packs',
    resolveId(id) { if (id === '@engage-preview/packs') return '\0engage-preview-packs'; },
    load(id) {
      if (id !== '\0engage-preview-packs') return;
      const pack = (name: string, file: string) => JSON.stringify(path.resolve(__dirname, `../experience-packs/${name}-experience/src/${file}`));
      return `import { genericWorkspacePreviewFixtures } from ${pack('generic-workspace', 'preview-fixtures.ts')};
import { dealershipPreviewFixtures } from ${pack('dealership', 'preview-fixtures.ts')};
import { registerDealershipActionPresentationElements } from ${pack('dealership', 'action-presentations.ts')};
import { supportExperiencePreviewFixtures } from ${pack('support', 'preview-fixtures.ts')};
const packs = {
  'generic-workspace': { fixtures: genericWorkspacePreviewFixtures, register() {} },
  dealership: { fixtures: dealershipPreviewFixtures, register: registerDealershipActionPresentationElements },
  support: { fixtures(context) {
    const sample = supportExperiencePreviewFixtures({ schemaVersion: 'loomai-support-experience-config-v1', app: { assistantLabel: 'Support preview' },
      capabilities: { tickets: context.ticketsEnabled === true, handover: context.handoverEnabled === true, attachments: context.attachmentsEnabled === true, currentPage: false } });
    return { userMessage: sample.userMessage, answer: sample.answer, confirmation: sample.confirmation, documents: [],
      fallback: sample.fallback, presentations: [], ...(sample.receipt ? { receipt: sample.receipt } : {}), ...(sample.attachment ? { attachment: sample.attachment } : {}) };
  }, register() {} }
};
export function getBundledPreviewPack(code) { return Object.hasOwn(packs, code) ? packs[code] : undefined; }`;
    },
  }],
  define: { 'process.env.NODE_ENV': JSON.stringify('production') },
  resolve: { alias: { '@': path.resolve(__dirname, './src') } },
  build: {
    lib: { entry: path.resolve(__dirname, 'src/entries/preview.tsx'), name: 'LoomAIEngagePreview',
      formats: ['iife'], fileName: () => 'engage-preview.iife.js' },
    rollupOptions: { output: { inlineDynamicImports: true } },
    cssCodeSplit: false, sourcemap: false, minify: 'terser', outDir: 'dist', emptyOutDir: false,
  },
});
