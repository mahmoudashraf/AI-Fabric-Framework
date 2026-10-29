// Synthetic API fixture for browser acceptance; never connects to a real account or runtime.
import { createServer } from '../../Platfrom/ui/node_modules/vite/dist/node/index.js'
const customerId = 'preview-customer', consumerId = 'preview-consumer', now = new Date().toISOString()
const consumers = [{ customerId, consumerId, displayName: 'Knowledge connection', status: 'ACTIVE', boundDeploymentId: 'preview-deployment',
  boundDeploymentName: 'Knowledge workspace', boundDeploymentEnvironment: 'STAGING', boundReleaseId: 'preview-release', boundTargetProfileId: 'preview-target', bindingRevision: 1 }]
const deployments = ['preview-deployment', 'draft-deployment'].map((deploymentId, index) => ({ deploymentId,
  name: index ? 'New knowledge deployment' : 'Knowledge workspace', environmentName: 'STAGING', status: 'ACTIVE', tenantId: 'tenant', bootstrapTargetProfileId: 'preview-target' }))
const configuration = { app: { assistantLabel: 'Support AI' }, capabilities: { currentPage: true, handover: true }, page: { rootSelector: 'main', maxChars: 4000 },
  theme: { primaryColor: '#155eef', darkMode: false }, copy: { welcomeMessage: 'Welcome to your workspace.', placeholder: 'Ask us a question...' },
  toolGroups: { initialScope: 'default', default: { label: 'Browse knowledge', tools: Array.from({ length: 10 }, (_, n) => ({ id: `tool-${n}`, label: `Knowledge tool ${n + 1}`, icon: 'search', query: `Example ${n}` })) },
    contextual: { label: 'Current document', tools: [{ id: 'summarize', label: 'Summarize document', icon: 'documents', query: 'Summarize context' }] } },
  requestContext: { preferredVectorSpaces: ['document'] } }
let installation = { id: 'preview-id', installationId: 'preview-installation', customerId, consumerId, displayName: 'Support workspace', status: 'DISABLED',
  experiencePackCode: 'generic-workspace', experiencePackVersion: '1.0.0', connectionMode: 'public-runtime-anonymous', connectionProfileCode: 'public-anonymous', connectionProfileVersion: '1.0.0',
  connectionConfiguration: {}, allowedOrigins: ['https://example.com'], configuration, deliveryMode: 'embedded', appSlug: null, appUrl: null,
  interactionPolicy: 'public-with-protected-capabilities', authProfileCode: 'supabase', authProfileVersion: '1.0.0', authConfiguration: { projectUrl: 'https://example.supabase.co', publishableKey: 'synthetic-public-key', methods: ['email-password'] },
  domains: [], deploymentId: 'preview-deployment', releaseId: 'preview-release', assignmentRevision: '1', ready: true, readinessChecks: [], rowVersion: 1, createdAt: now, updatedAt: now }
const mutations = []
const server = await createServer({ root: new URL('../../Platfrom/ui', import.meta.url).pathname,
  server: { host: '127.0.0.1', port: 5187, strictPort: true }, plugins: [{ name: 'engage-connection-fixture', configureServer(server) {
    server.middlewares.use(async (req, res, next) => {
      const path = new URL(req.url, 'http://localhost').pathname
      const json = value => { res.setHeader('Content-Type', 'application/json'); res.end(JSON.stringify(value)) }
      if (path === '/runtime-config.js') { res.setHeader('Content-Type', 'text/javascript'); return res.end('window.__PLATFORM_RUNTIME_CONFIG__={apiBaseUrl:window.location.origin};') }
      if (path === '/fixture-state') return json({ installation, consumers, mutations })
      if (!path.startsWith('/api/')) return next()
      if (req.method !== 'GET') {
        let body = ''; for await (const part of req) body += part
        const payload = body ? JSON.parse(body) : {}; mutations.push({ method: req.method, path, payload })
        if (path.endsWith('/connections')) {
          const consumer = { ...consumers[0], consumerId: `engage-fixture-${payload.operationKey}`, boundDeploymentId: payload.deploymentId,
            boundTargetProfileId: payload.targetProfileId, boundReleaseId: payload.releaseId, bindingRevision: 1 }
          consumers.push(consumer); return json(consumer)
        }
        if (path.endsWith('/disable')) installation = { ...installation, status: 'DISABLED', rowVersion: installation.rowVersion + 1 }
        else if (req.method === 'PUT') { const connection = consumers.find(item => item.consumerId === payload.consumerId)
          installation = { ...installation, ...payload, deploymentId: connection.boundDeploymentId, releaseId: connection.boundReleaseId, rowVersion: installation.rowVersion + 1 } }
        return json(installation)
      }
      if (path === '/api/platform/auth/customer-config') return json({ enabled: false, projectUrl: null, publicKey: null, deskClients: [] })
      if (path === '/api/platform/auth/session') return json({ enabled: true, authenticated: true, role: 'CUSTOMER_ADMIN', authenticationMode: 'SESSION', actorId: 'preview-actor', customerId, customerName: 'Preview workspace', displayName: 'Preview owner', sessionAuthEnabled: true, apiKeyAuthEnabled: false })
      if (path === '/api/platform/customers') return json([{ id: customerId, name: 'Preview workspace', slug: 'preview', status: 'ACTIVE', consumers, tenants: [] }])
      if (path === '/api/customer-self-service/deployments') return json(deployments)
      if (path === '/api/customer-self-service/catalogue') return json([])
      if (path.endsWith('/preview-capabilities')) return json({ runtimeConfigured: true, handover: { enabled: true, userCanCancelPending: true, userCanResumeAi: true, intake: { anonymousAllowed: true, fields: [{ key: 'email', label: 'Email', type: 'EMAIL', required: true, purpose: 'Reply to your request', maxLength: 254, options: [] }] } } })
      if (path.endsWith('/target-profiles')) return json([{ id: 'preview-target', name: 'Customer staging', environmentName: 'STAGING', active: true }])
      if (path.endsWith('/releases')) return json(path.includes('draft-deployment') ? [] : [{ id: 'preview-release', deploymentId: 'preview-deployment', targetProfileId: 'preview-target', status: 'APPLIED_VERIFIED', verificationStatus: 'PASSED', createdAt: now }])
      if (path === `/api/platform/customers/${customerId}/ai-workspace-installations`) return json([installation])
      if (path === '/api/platform/ai-workspaces/catalog') return json({ assetsReady: true, assetStatus: 'READY', experiencePacks: [{ code: 'generic-workspace', version: '1.0.0', name: 'Generic workspace', enabled: true }], connectionProfiles: [{ code: 'public-anonymous', version: '1.0.0', name: 'Public anonymous', mode: 'public-runtime-anonymous', enabled: true }], authenticationProfiles: [{ code: 'supabase', version: '1.0.0', name: 'Supabase', enabled: true }] })
      res.statusCode = 404; return json({ message: `Unimplemented fixture read: ${path}` })
    })
  } }] })
await server.listen()
console.log('Synthetic Engage acceptance: http://127.0.0.1:5187/ai-workspaces?view=manage&previewInstallationId=preview-installation')
