/** Trusted first-party host bridge. No case text, identity token or private assertion is embedded in HTML. */
export function supportWorkspaceDocument() {
  return `<!doctype html><html><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<style>html,body{height:100%;margin:0;overflow:hidden;background:transparent}</style></head><body><script>
(() => {
  const origin = new URL(document.baseURI).origin;
  let nonce = null, controller = null, pack = null, mounted = false;
  const pending = new Map();
  const send = (type, detail = {}) => parent.postMessage({ type, nonce, ...detail }, origin);
  const report = () => send('loomai:support:error', { message: 'The workspace could not load. Your saved request is unchanged.' });
  window.LoomAIWorkspace = { registerExperiencePack(value) {
    if (value.code !== 'generic-workspace') throw new Error('Unsupported support experience');
    pack = value;
  }};
  function getToken() {
    return new Promise((resolve, reject) => {
      const requestId = crypto.randomUUID();
      const timer = setTimeout(() => { pending.delete(requestId); reject(new Error('Console identity is unavailable')); }, 10000);
      pending.set(requestId, { resolve, reject, timer });
      send('loomai:support:token-request', { requestId });
    });
  }
  function loadScript(value, integrity, platformOrigin) {
    const url = new URL(value, platformOrigin);
    if (url.origin !== platformOrigin || !url.pathname.startsWith('/api/public/ai-workspace/')
      || !/^sha384-[A-Za-z0-9+/=]+$/.test(integrity)) throw new Error('Invalid workspace asset');
    return new Promise((resolve, reject) => {
      const script = document.createElement('script');
      const timer = setTimeout(() => reject(new Error('Workspace asset timed out')), 15000);
      script.src = url.href; script.integrity = integrity; script.crossOrigin = 'anonymous';
      script.onload = () => { clearTimeout(timer); resolve(); };
      script.onerror = () => { clearTimeout(timer); reject(new Error('Workspace asset could not be verified')); };
      document.head.appendChild(script);
    });
  }
  async function configure(message) {
    if (mounted) return;
    mounted = true; nonce = message.nonce;
    const b = message.bootstrap;
    const platformOrigin = new URL(message.platformBaseUrl, origin).origin;
    const platform = new URL(platformOrigin);
    if (platform.protocol !== 'https:' && !['localhost', '127.0.0.1', '::1'].includes(platform.hostname)) throw new Error('HTTPS is required');
    const api = new URL(b.apiBaseUrl, platformOrigin);
    const expected = '/api/support/cases/' + encodeURIComponent(message.caseId) + '/runtime/api';
    if (api.origin !== platformOrigin || api.pathname !== expected || api.search || api.hash) throw new Error('Invalid support adapter');
    await loadScript(b.workspaceAssetUrl, b.workspaceIntegrity, platformOrigin);
    await loadScript(b.experienceModuleUrl, b.experienceIntegrity, platformOrigin);
    if (!pack || !window.MaxMode?.openConversation) throw new Error('Workspace host API unavailable');
    const base = '/chat/me/conversations/{conversationId}';
    controller = await pack.mount({
      installationId: b.installationId, assignmentRevision: b.assignmentRevision, connectionMode: 'backend-mediated-private-runtime', deliveryMode: 'standalone',
      maxMode: window.MaxMode,
      hostSession: { namespace: b.principalNamespace, accountKey: message.accountKey },
      configuration: { ...b.configuration, capabilities: { ...b.configuration.capabilities, currentPage: false, handover: b.conversationHandover?.enabled === true },
        theme: { ...b.configuration.theme, darkMode: message.dark } },
      widgetConfig: {
        storageNamespace: b.installationId, integrationMode: 'backend-mediated-private-runtime', launcher: false,
        features: { cart: false, debug: false, conversations: false },
        host: { readOnly: message.readOnly, showCloseButton: false, showUtilityPanel: false },
        apiConfig: { chatBaseUrl: api.href, fetchCredentials: 'omit', probeShellConfigOnOpen: false,
          runtimeAuth: { getBearerToken: getToken, probeAuthContextOnOpen: false },
          conversationHandover: b.conversationHandover,
          runtimeRoutes: { conversationItemUrlTemplate: base, conversationMessageUrlTemplate: base + '/messages',
            handoverRequestUrlTemplate: base + '/handover-requests', handoverCancelUrlTemplate: base + '/handover-requests/{handoverId}/cancel',
            handoverRetryUrlTemplate: base + '/handover-requests/{handoverId}/retry', conversationControlUrlTemplate: base + '/control',
            conversationEventsUrlTemplate: base + '/events', aiResumeRequestUrlTemplate: base + '/ai-resume-requests' } },
      },
      refreshAssignment: async () => { throw new Error('Select an authorized request in Console'); },
    });
    controller.onStateChange(state => send('loomai:support:state', { state }));
    await controller.openConversation(b.conversationId);
  }
  addEventListener('message', event => {
    if (event.source !== parent || event.origin !== origin || !event.data) return;
    const message = event.data;
    if (message.type === 'loomai:support:configure' && typeof message.nonce === 'string') { configure(message).catch(report); return; }
    if (message.nonce !== nonce) return;
    if (message.type === 'loomai:support:token-response') {
      const request = pending.get(message.requestId);
      if (!request) return;
      pending.delete(message.requestId); clearTimeout(request.timer);
      if (typeof message.token === 'string' && message.token) request.resolve(message.token);
      else request.reject(new Error('Console sign-in is required'));
    }
    if (message.type === 'loomai:support:refresh') controller?.refreshConversation().catch(report);
    if (message.type === 'loomai:support:destroy') {
      controller?.destroy();
      for (const request of pending.values()) { clearTimeout(request.timer); request.reject(new Error('Workspace closed')); }
      pending.clear();
    }
  });
  send('loomai:support:ready');
})();
</script></body></html>`
}
