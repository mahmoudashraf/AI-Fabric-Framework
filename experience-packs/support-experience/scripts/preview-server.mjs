import http from 'node:http'
import { readFile } from 'node:fs/promises'
import { createHash } from 'node:crypto'
import { fileURLToPath, pathToFileURL } from 'node:url'
import { build } from 'vite'

/** Local synthetic fixture server. It has no forwarding path to a customer service. */
export async function startPreview(port = 0, { initializeEpisodes = true } = {}) {
  const widget = await readFile(new URL('../../../max-mode-widget/dist/max-mode-widget.iife.js', import.meta.url))
  const pack = await readFile(new URL('../dist/support-experience.esm.js', import.meta.url))
  const bundled = await build({ configFile: false, publicDir: false, logLevel: 'error', build: { write: false, minify: false,
    lib: { entry: fileURLToPath(new URL('../../../max-mode-widget/src/installer/isolatedHost.ts', import.meta.url)), formats: ['es'] } } })
  const helper = (Array.isArray(bundled) ? bundled[0] : bundled).output.find(item => item.type === 'chunk').code
  const integrity = `sha384-${createHash('sha384').update(widget).digest('base64')}`
  const requests = [], tickets = new Map(), receipts = new Map(), conversations = new Map()
  const directPrefix = '/api/desk/requester/v1/spaces/preview-space'
  conversations.set('preview-chat', { controlState: 'AI_ACTIVE', controlVersion: 1, lastInteractionAt: '2026-01-01T10:00:01Z' })
  let sequence = 1, notificationEnabled = false
  const server = http.createServer(async (req, res) => {
    const url = new URL(req.url, 'http://fixture.invalid')
    if (['/engine.js', '/pack.js', '/host.js'].includes(url.pathname)) {
      res.setHeader('Content-Type', 'text/javascript')
      res.end(url.pathname === '/engine.js' ? widget : url.pathname === '/pack.js' ? pack : helper); return
    }
    if (!url.pathname.startsWith('/api/')) {
      res.setHeader('Content-Type', 'text/html'); res.end(html(integrity)); return
    }
    let raw = ''
    for await (const chunk of req) raw += chunk
    const body = raw ? JSON.parse(raw) : undefined
    const recorded = { path: url.pathname, method: req.method, body, key: req.headers['idempotency-key'],
      authorization: req.headers.authorization, runtimeAuthorization: req.headers['x-loomai-runtime-authorization'] }
    requests.push(recorded)
    const send = (value, status = 200) => { recorded.status = status; res.writeHead(status, { 'Content-Type': 'application/json', 'Cache-Control': 'no-store' }); res.end(JSON.stringify(value)) }
    if (url.pathname.startsWith('/api/runtime/')) {
      if (!['Bearer fixture-token', 'Bearer fixture-runtime-token'].includes(req.headers.authorization)) { send({ code: 'UNAUTHORIZED' }, 401); return }
      if (url.pathname === '/api/runtime/chat/me/suggestions') { send({ suggestions: [] }); return }
      const id = url.pathname.split('/')[6]
      if (!id) { send({ code: 'NOT_FOUND' }, 404); return }
      // Canonical item reads never create scope; only the authorized control route does.
      if (url.pathname.endsWith('/control') && !conversations.has(id)) conversations.set(id, {
        controlState: 'AI_ACTIVE', controlVersion: 1, participantMessages: [], lastInteractionAt: '2026-01-01T10:00:00Z',
      })
      const state = conversations.get(id)
      if (!state) { send({ code: 'CONVERSATION_NOT_FOUND' }, 404); return }
      if (url.pathname.endsWith('/events')) { recorded.status = 200; res.writeHead(200, { 'Content-Type': 'text/event-stream' }); res.write(': fixture\n\n'); return }
      if (url.pathname.endsWith('/control')) { send({ conversationId: id, ...state }); return }
      send({ id, status: state.controlState === 'CLOSED' ? 'CLOSED' : 'ACTIVE', createdAt: '2026-01-01T10:00:00Z',
        participantMessages: [{ messageId: `${id}-user`, role: 'USER', sequence: 1, content: 'A sample question about the guidance.', createdAt: '2026-01-01T10:00:00Z' },
          { messageId: `${id}-ai`, role: 'AI', sequence: 2, content: 'This is a synthetic conversation from the fixture preview. No live support service was contacted.', createdAt: '2026-01-01T10:00:01Z' }], completedTurns: [], ...state }); return
    }
    const direct = url.pathname.startsWith(directPrefix + '/')
    if (direct) {
      url.pathname = '/api/support' + url.pathname.slice(directPrefix.length)
      if (req.headers.authorization !== 'Bearer fixture-token') { send({ code: 'UNAUTHORIZED' }, 401); return }
      if (req.method !== 'GET' && (body?.existingConversationId || /\/(start|episodes|activity)$/.test(url.pathname))
        && req.headers['x-loomai-runtime-authorization'] !== 'Bearer fixture-runtime-token') { send({ code: 'RUNTIME_IDENTITY_REQUIRED' }, 401); return }
    }
    const caps = url.searchParams.get('readonly') === 'true' ? ['LIST', 'GET', 'SEEN'] : ['CREATE', 'LIST', 'GET', 'RESOLVE', 'CANCEL', 'REOPEN', 'SEEN', 'LINK_CONVERSATION', 'OBSERVE_ACTIVITY']
    if (url.pathname === '/api/support/session') { send({ authenticated: true, requesterId: 'preview-requester', space: { spaceId: 'preview-space', providerBindingId: 'preview-provider', humanHandoverEnabled: false, capabilities: caps }, capabilities: caps }); return }
    if (url.pathname === '/api/support/notifications') {
      if (req.method === 'PUT') notificationEnabled = !!body.enabled
      send({ available: true, enabled: notificationEnabled, deliveryStatus: 'NONE' }); return
    }
    if (url.pathname === '/api/support/team') { send([{ profileRef: 'preview-agent', displayName: 'Sample assigned agent', photoAvailable: false }]); return }
    if (url.pathname === '/api/support/requests' && req.method === 'GET') { send({ items: [...tickets.values()].filter(ticket => !url.searchParams.get('status') || ticket.status === url.searchParams.get('status')), nextCursor: null }); return }
    const key = req.headers['idempotency-key']
    if (key && receipts.has(key)) { send(receipts.get(key)); return }
    if (url.pathname === '/api/support/requests' && req.method === 'POST') {
      const ticket = { id: `preview-request-${sequence}`, reference: `SAMPLE-${sequence++}`, providerBindingId: 'preview-provider', subject: body.subject, details: body.details, topic: body.topic,
        status: 'OPEN', version: 'fixture-v1', unread: true, updatedAt: '2026-01-01T10:00:00Z', openingMessageRequired: !body.existingConversationId,
        conversation: { episodeId: 'preview-episode', conversationId: body.existingConversationId ?? `preview-ticket-${sequence}`, delivery: body.existingConversationId ? 'NOT_REQUIRED' : 'PENDING' }, capabilities: caps }
      ticket.episodes = [structuredClone(ticket.conversation)]
      tickets.set(ticket.id, ticket); if (key) receipts.set(key, ticket); send(ticket, 201); return
    }
    const [, , , , id, command] = url.pathname.split('/')
    const ticket = tickets.get(id)
    if (!ticket) { send({ code: 'NOT_FOUND' }, 404); return }
    if (req.method === 'GET') { send(ticket); return }
    if (body.expectedVersion !== ticket.version) { send({ code: 'CONFLICT', current: ticket }, 409); return }
    if (command === 'seen') { ticket.unread = false; ticket.version += '-seen' }
    else if (command === 'start') {
      if (!conversations.has(ticket.conversation.conversationId)) conversations.set(ticket.conversation.conversationId, {
        controlState: 'AI_ACTIVE', controlVersion: 1, lastInteractionAt: '2026-01-01T10:00:01Z',
      })
      ticket.conversation.delivery = 'DELIVERED'
      ticket.episodes[ticket.episodes.length - 1].delivery = 'DELIVERED'
    }
    else if (command === 'activity') {
      const activity = conversations.get(ticket.conversation.conversationId)?.lastInteractionAt ?? '2026-01-01T10:00:01Z'
      if (activity > ticket.updatedAt) { ticket.updatedAt = activity; ticket.unread = true }
      ticket.version += '-activity'
    }
    else if (command === 'episodes') {
      const previous = ticket.conversation
      if (ticket.status !== 'OPEN' || conversations.get(previous.conversationId)?.controlState !== 'CLOSED') { send({ code: 'CONVERSATION_NOT_TERMINAL' }, 409); return }
      const next = { episodeId: `preview-episode-${sequence++}`, conversationId: `preview-continuation-${sequence}`, openingMessageRequired: false, delivery: 'NOT_REQUIRED' }
      if (initializeEpisodes) {
        const origin = `http://127.0.0.1:${server.address().port}`
        const headers = { Authorization: direct ? req.headers['x-loomai-runtime-authorization'] : 'Bearer fixture-runtime-token' }
        const control = await fetch(`${origin}/api/runtime/chat/me/conversations/${next.conversationId}/control`, { headers, redirect: 'error' })
        if (!control.ok) { send({ code: 'CONVERSATION_INITIALIZATION_FAILED' }, 503); return }
        const canonical = await fetch(`${origin}/api/runtime/chat/me/conversations/${next.conversationId}`, { headers, redirect: 'error' })
        if (!canonical.ok) { send({ code: 'CONVERSATION_INITIALIZATION_FAILED' }, 503); return }
      }
      ticket.episodes[ticket.episodes.length - 1] = structuredClone(previous)
      ticket.conversation = next
      ticket.episodes.push(structuredClone(ticket.conversation))
      ticket.openingMessageRequired = false; ticket.version += '-episode'
    }
    else if (['resolve', 'cancel', 'reopen'].includes(command)) { ticket.status = { resolve: 'RESOLVED', cancel: 'CANCELLED', reopen: 'OPEN' }[command]; ticket.version += '-next' }
    else { send({ code: 'NOT_FOUND' }, 404); return }
    if (key) receipts.set(key, structuredClone(ticket))
    send(ticket)
  })
  await new Promise(resolve => server.listen(port, '127.0.0.1', resolve))
  return { url: `http://127.0.0.1:${server.address().port}`, requests, server, conversations,
    close: async () => { server.closeAllConnections(); await new Promise(resolve => server.close(resolve)) } }
}

function html(integrity) { return `<!doctype html><html><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>Support kit fixture preview</title>
<style>body{margin:0;background:#eef2f3;color:#182421;font:14px system-ui}header.fixture{padding:12px 20px;display:flex;gap:16px;align-items:center;flex-wrap:wrap;background:#233d38;color:white}main{max-width:1200px;margin:24px auto;padding:0 16px;height:max(480px,calc(100dvh - 200px))}#support-preview{height:100%}select{padding:6px}#preview-error{padding:16px;color:#922f2a}</style></head><body>
<header class="fixture"><strong>Fixture preview</strong><span>Synthetic data only. No live service is connected.</span><label>Presentation <select id="mode"><option value="page">Page</option><option value="widget">Widget</option><option value="standalone">Standalone</option></select></label></header>
<main><div id="support-preview"></div></main><p id="preview-error" role="alert" hidden></p>
<script type="module">
import {mountInstallation} from '/pack.js'; import {createIsolatedWorkspaceHost} from '/host.js';
const query=new URLSearchParams(location.search), container=document.getElementById('support-preview');
const direct=query.get('requester')==='direct', requesterRoot=direct?'/api/desk/requester/v1/spaces/preview-space':'/api/support';
const frame=await createIsolatedWorkspaceHost({url:location.origin+'/engine.js',integrity:${JSON.stringify(integrity)}}); window.fixtureFrame=frame;
const config={schemaVersion:'loomai-support-experience-config-v1',app:{assistantLabel:'Example Support',title:'Help and support'},support:{spaceId:'preview-space',ticketProviderBindingId:'preview-provider',teamVisible:query.get('team')!=='false',topics:[{code:'guidance',label:'Guidance'}]},capabilities:{tickets:query.get('tickets')!=='false',handover:query.get('handover')==='true',attachments:query.get('attachments')!=='false',currentPage:false},copy:{welcomeMessage:'Ask about the guidance or submit a sample request.',starterSuggestions:['How can this support experience help?']}};
if(direct)config.support.requesterOrigin=location.origin;
const fetchWorkspace=async(requestId)=>{
  if(query.get('runtime')==='off')throw new Error('Fixture runtime unavailable');
  const ticket=requestId?await fetch(requesterRoot+'/requests/'+encodeURIComponent(requestId),{headers:{Authorization:'Bearer fixture-token'}}).then(response=>response.json()):null;
  return {apiBaseUrl:location.origin+'/api/runtime',conversationId:ticket?.conversation.conversationId??'preview-chat',principalNamespace:ticket?requestId+':'+ticket.conversation.episodeId:'support-preview',conversationHandover:{enabled:config.capabilities.handover,...(query.get('intake')==='true'?{intake:{anonymousAllowed:true,fields:[{key:'email',label:'Email',type:'email',required:true}]}}:{})}};
};
try{window.controller=await mountInstallation({installationId:'preview-installation',assignmentRevision:'preview-assignment',deliveryMode:'embedded',connectionMode:direct?'public-runtime-authenticated':'backend-mediated-private-runtime',configuration:config,widgetConfig:{host:{companionDock:query.get('docked')==='true'}},hostSession:{namespace:'support-preview',accountKey:'preview-account'},presentation:{mode:query.get('docked')==='true'?'docked':'page',container,containerSelector:'#support-preview'},maxMode:frame.maxMode,workspaceFrame:frame,credentials:{getIdentityAccessToken:()=> 'fixture-token',getRuntimeAccessToken:()=> 'fixture-runtime-token',getIdentity:()=>({status:'authenticated',subjectKey:'preview-account'})},supportHost:{...(direct?{}:{apiBaseUrl:location.origin+'/api/support',runtimeCredentialMode:'host-mediated'}),getIdentityAccessToken:()=> 'fixture-token',fetchWorkspace},refreshAssignment:async()=>{}});
document.getElementById('mode').onchange=event=>window.controller.setPresentation(event.target.value,event.target.value==='page'?document.getElementById('support-preview'):undefined);
window.fixtureReady=true;}catch(error){const element=document.getElementById('preview-error');element.hidden=false;element.textContent=error.message;throw error;}
</script></body></html>` }

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  const preview = await startPreview(Number(process.env.PORT ?? 4178))
  console.log(`Support fixture preview: ${preview.url}`)
  process.on('SIGINT', async () => { await preview.close(); process.exit(0) })
}
