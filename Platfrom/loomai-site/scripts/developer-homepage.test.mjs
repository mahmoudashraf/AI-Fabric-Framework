import assert from 'node:assert/strict'
import { spawn } from 'node:child_process'
import { once } from 'node:events'
import { mkdtemp, mkdir, rm, writeFile } from 'node:fs/promises'
import { createServer } from 'node:net'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import { test } from 'node:test'
import { fileURLToPath } from 'node:url'

test('remembered developer entry redirects only the homepage, with explicit business opt-out', { timeout: 15000 }, async t => {
  const dist = await mkdtemp(join(tmpdir(), 'loomai-website-entry-'))
  await mkdir(join(dist, 'products'))
  await writeFile(join(dist, 'index.html'), '<h1>Business home</h1>')
  await writeFile(join(dist, 'products', 'index.html'), '<h1>Products</h1>')
  const reservation = createServer().listen(0, '127.0.0.1')
  await once(reservation, 'listening')
  const port = reservation.address().port
  await new Promise(resolve => reservation.close(resolve))
  const server = spawn(process.execPath, [fileURLToPath(new URL('../deploy/container/server.mjs', import.meta.url))], {
    env: { ...process.env, PORT: String(port), LOOMAI_SITE_DIST_DIR: dist }, stdio: ['ignore', 'pipe', 'pipe'],
  })
  let logs = ''
  for (const stream of [server.stdout, server.stderr]) stream.on('data', data => { logs = (logs + data).slice(-4000) })
  t.after(async () => {
    if (server.exitCode === null) { const stopped = once(server, 'close'); server.kill('SIGTERM'); await stopped }
    await rm(dist, { recursive: true, force: true })
  })
  const origin = `http://127.0.0.1:${port}`
  let ready = false
  for (let attempt = 0; attempt < 60; attempt++) {
    try { ready = (await fetch(origin + '/health', { signal: AbortSignal.timeout(500) })).ok } catch {}
    if (ready || server.exitCode !== null) break
    await new Promise(resolve => setTimeout(resolve, 100))
  }
  assert.ok(ready, logs)
  const request = (path, cookie, method = 'GET') => fetch(origin + path, { method, redirect: 'manual', headers: cookie ? { Cookie: cookie } : {} })
  for (const method of ['GET', 'HEAD']) {
    const developer = await request('/', 'other=value; loomai-website-experience=DEVELOPER', method)
    assert.equal(developer.status, 302)
    assert.equal(developer.headers.get('location'), 'https://developer.loomai.pro/')
    assert.equal(developer.headers.get('cache-control'), 'private, no-store')
    assert.equal(developer.headers.get('vary'), 'Cookie')
  }
  for (const cookie of [null, 'loomai-website-experience=CUSTOMER', 'loomai-website-experience=ADMIN', 'loomai-website-experience=DEVELOPER-extra']) {
    const ordinary = await request('/', cookie)
    assert.equal(ordinary.status, 200)
    assert.equal(await ordinary.text(), '<h1>Business home</h1>')
    assert.equal(ordinary.headers.get('cache-control'), 'private, no-store')
    assert.equal(ordinary.headers.get('vary'), 'Cookie')
  }
  const explicit = await request('/?entryPoint=CUSTOMER', 'loomai-website-experience=DEVELOPER')
  assert.equal(explicit.status, 200)
  assert.equal(explicit.headers.get('set-cookie'), 'loomai-website-experience=CUSTOMER; Domain=loomai.pro; Path=/; Max-Age=31536000; SameSite=Lax; Secure')
  assert.equal((await request('/?entryPoint=DEVELOPER')).status, 302)
  assert.equal((await request('/?entryPoint=ADMIN', 'loomai-website-experience=DEVELOPER')).status, 302)
  const product = await request('/products/', 'loomai-website-experience=DEVELOPER')
  assert.equal(product.status, 200)
  assert.equal(await product.text(), '<h1>Products</h1>')
  assert.equal((await request('/health', 'loomai-website-experience=DEVELOPER')).status, 200)
  assert.equal((await request('/experiences/ai-customer-experience', 'loomai-website-experience=DEVELOPER')).headers.get('location'),
    'https://console.loomai.pro/experiences/ai-customer-experience')
})
