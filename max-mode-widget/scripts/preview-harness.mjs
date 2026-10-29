import { createServer as createHttpServer } from 'node:http';
import { readFile } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';
import { createServer as createViteServer } from 'vite';

// Local synthetic-only harness for CUA verification of the built opaque frame.
const vite = await createViteServer({ configFile: false, optimizeDeps: { noDiscovery: true, include: [] }, server: { middlewareMode: true, hmr: false }, appType: 'custom' });
const { buildWorkspacePreviewConfiguration } = await vite.ssrLoadModule(fileURLToPath(new URL('../../Platfrom/ui/src/components/customer/buildWorkspacePreviewConfiguration.ts', import.meta.url)));
const { engagePreviewDocument } = await vite.ssrLoadModule(fileURLToPath(new URL('../../Platfrom/ui/src/components/customer/engagePreviewDocument.ts', import.meta.url)));
await vite.close();
const bundle = await readFile(new URL('../dist/engage-preview.iife.js', import.meta.url), 'utf8');
const json = value => JSON.stringify(value).replace(/</g, '\\u003c');
const server = createHttpServer((request, response) => {
  const url = new URL(request.url, 'http://127.0.0.1');
  if (url.pathname !== '/') { response.writeHead(404).end(); return; }
  const get = (key, fallback) => url.searchParams.get(key) ?? fallback;
  const packCode = get('pack', 'generic-workspace');
  const configuration = { capabilities: { handover: true } };
  if (get('tools', '') === 'many') configuration.toolGroups = {
    initialScope: 'default', default: { label: 'Explore', icon: 'search', tools: Array.from({ length: 15 }, (_, i) => ({ label: `Configured tool ${i + 1}`, query: 'Synthetic query', icon: 'search' })) },
    contextual: { label: 'Context', icon: 'details', availableWithoutAttachments: false, contextLabel: 'Selected context', tools: [{ label: 'Explain context', query: 'Synthetic query', icon: 'documents' }] },
  };
  const enabled = get('enabled', 'true') === 'true';
  const capabilities = { history: enabled, attachments: enabled, currentPage: enabled, sources: enabled,
    authentication: { signInAvailable: enabled }, handover: { enabled, runtimeConfigured: true, userCanCancelPending: true, userCanResumeAi: true,
      intake: { anonymousAllowed: true, fields: [] } } };
  const config = buildWorkspacePreviewConfiguration({ configuration, packCode, interactionPolicy: get('policy', 'public-with-protected-capabilities'), capabilities });
  const scenario = { layout: get('layout', 'max'), sample: get('sample', 'conversation'), appName: 'Synthetic preview', dark: get('dark', '') === 'true', signedIn: get('signedIn', '') === 'true', attachment: get('attachment', '') === 'true' };
  const width = get('mobile', '') === 'true' ? 390 : 960;
  response.writeHead(200, { 'Content-Type': 'text/html; charset=utf-8', 'Cache-Control': 'no-store' });
  response.end(`<!doctype html><html><head><title>Engage isolated preview test</title><style>body{margin:0;background:#e5e7eb;font:14px system-ui}header{padding:8px}iframe{display:block;width:min(100%,${width}px);height:800px;border:0;margin:auto}</style></head><body><header>Local synthetic preview</header><iframe title="Isolated Engage preview" sandbox="allow-scripts" referrerpolicy="no-referrer"></iframe><script>
const frame=document.querySelector('iframe');
window.addEventListener('message',event=>{if(event.source===frame.contentWindow&&event.data?.type==='loomai:engage-preview:ready')frame.contentWindow.postMessage(${json({ type: 'loomai:engage-preview:configure', configuration: config, scenario })},'*')});
frame.srcdoc=${json(engagePreviewDocument(bundle))};
</script></body></html>`);
});
server.listen(Number(process.env.PORT || 5198), '127.0.0.1', () => console.log(`Preview harness: http://127.0.0.1:${server.address().port}/`));
process.on('SIGINT', () => server.close(() => process.exit(0)));
process.on('SIGTERM', () => server.close(() => process.exit(0)));
