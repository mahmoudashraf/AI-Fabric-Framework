import { spawn } from 'node:child_process'
import { readFile } from 'node:fs/promises'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const __filename = fileURLToPath(import.meta.url)
const __dirname = path.dirname(__filename)
const rootDir = path.resolve(__dirname, '..')
const publicDir = path.join(rootDir, 'public')
const port = Number(process.env.SMOKE_PORT || 4189)
const baseUrl = `http://127.0.0.1:${port}`

function assert(condition, message) {
  if (!condition) {
    throw new Error(message)
  }
}

async function assertCopy() {
  const merchant = await readFile(path.join(publicDir, 'index.html'), 'utf8')
  const partner = await readFile(path.join(publicDir, 'partners.html'), 'utf8')
  const css = await readFile(path.join(publicDir, 'styles.css'), 'utf8')
  const combined = `${merchant}\n${partner}\n${css}`

  assert(merchant.includes('LoomAI products for commerce and support work.'), 'product-suite headline missing')
  assert(merchant.includes('Loom Companion'), 'Companion product copy missing')
  assert(merchant.includes('Loom Thinker'), 'Thinker product copy missing')
  assert(merchant.includes('Loom Resolver'), 'Resolver product copy missing')
  assert(merchant.includes('Request product access'), 'product access form missing')
  assert(partner.includes('Help clients launch LoomAI products.'), 'partner product-suite headline missing')
  assert(partner.includes('Companion'), 'partner Companion product copy missing')
  assert(partner.includes('Thinker'), 'partner Thinker product copy missing')
  assert(partner.includes('Resolver'), 'partner Resolver product copy missing')
  assert(partner.includes('Partner Sign In'), 'partner sign-in CTA missing')

  const forbidden = [
    '20% recurring',
    '25% revenue',
    'Install on Shopify',
    'order tracking',
    'returns execution',
    'white-label'
  ]
  for (const phrase of forbidden) {
    assert(!combined.toLowerCase().includes(phrase.toLowerCase()), `unsafe public claim found: ${phrase}`)
  }
}

async function waitForServer(processHandle) {
  const deadline = Date.now() + 8000
  while (Date.now() < deadline) {
    if (processHandle.exitCode !== null) {
      throw new Error(`server exited early with code ${processHandle.exitCode}`)
    }
    try {
      const response = await fetch(`${baseUrl}/health`)
      if (response.ok) {
        return
      }
    } catch {
      await new Promise((resolve) => setTimeout(resolve, 200))
    }
  }
  throw new Error('server did not become healthy')
}

async function assertHttp() {
  const server = spawn(process.execPath, ['server.mjs'], {
    cwd: rootDir,
    env: {
      ...process.env,
      PORT: String(port),
      LOOMAI_LEADS_FILE: 'disabled'
    },
    stdio: ['ignore', 'pipe', 'pipe']
  })

  const logs = []
  server.stdout.on('data', (chunk) => logs.push(chunk.toString('utf8')))
  server.stderr.on('data', (chunk) => logs.push(chunk.toString('utf8')))

  try {
    await waitForServer(server)
    const merchant = await fetch(`${baseUrl}/`)
    assert(merchant.status === 200, `merchant page returned ${merchant.status}`)
    assert((await merchant.text()).includes('LoomAI products for commerce and support work.'), 'merchant page body mismatch')

    const partner = await fetch(`${baseUrl}/`, { headers: { 'X-Forwarded-Host': `partners.localhost:${port}` } })
    assert(partner.status === 200, `partner host page returned ${partner.status}`)
    assert((await partner.text()).includes('Help clients launch LoomAI products.'), 'partner host body mismatch')

    const partnerPath = await fetch(`${baseUrl}/partners`)
    assert(partnerPath.status === 200, `partner path returned ${partnerPath.status}`)

    const lead = await fetch(`${baseUrl}/api/leads`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        kind: 'merchant-private-install',
        name: 'Smoke Test',
        email: 'smoke@example.com',
        shopDomain: 'example.myshopify.com',
        consent: true,
        sourceUrl: `${baseUrl}/`
      })
    })
    assert(lead.status === 202, `lead endpoint returned ${lead.status}`)

    const runtime = await fetch(`${baseUrl}/runtime-config.js`)
    assert(runtime.status === 200, `runtime config returned ${runtime.status}`)
    assert((await runtime.text()).includes('__LOOMAI_LANDING_CONFIG__'), 'runtime config body mismatch')
  } finally {
    server.kill('SIGTERM')
    await new Promise((resolve) => server.once('exit', resolve))
    if (server.exitCode && server.exitCode !== 0 && server.exitCode !== null) {
      throw new Error(`server exited with ${server.exitCode}: ${logs.join('')}`)
    }
  }
}

await assertCopy()
await assertHttp()
console.log('LoomAI landing site smoke checks passed.')
