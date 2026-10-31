import assert from 'node:assert/strict'
import { test } from 'node:test'
import { mkdtemp, mkdir, copyFile, writeFile, rm } from 'node:fs/promises'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import { spawn } from 'node:child_process'
import { createServer } from 'node:net'
import { once } from 'node:events'
import { chromium } from 'playwright'

test('SPA routes that share asset-directory names never stream directories or crash the production server', { timeout: 20000 }, async () => {
  const root = await mkdtemp(join(tmpdir(), 'loomai-production-server-'))
  const reservation = createServer().listen(0, '127.0.0.1')
  await once(reservation, 'listening')
  const port = reservation.address().port
  await new Promise(resolve => reservation.close(resolve))
  let child, output = ''
  try {
    await mkdir(join(root, 'dist', 'products'), { recursive: true })
    await copyFile(new URL('../deploy/railway/server.mjs', import.meta.url), join(root, 'server.mjs'))
    await writeFile(join(root, 'dist', 'index.html'), '<main>Product entrance fixture</main>')
    await writeFile(join(root, 'dist', 'products', 'vehicle.webp'), 'test-image-bytes')
    child = spawn(process.execPath, [join(root, 'server.mjs')], { env: { ...process.env, PORT: String(port) }, stdio: ['ignore', 'pipe', 'pipe'] })
    await new Promise((resolve, reject) => {
      child.once('error', reject)
      child.once('exit', code => reject(new Error('Server exited before readiness: ' + code)))
      const timer = setTimeout(() => reject(new Error('Server did not become ready')), 5000)
      child.stdout.on('data', data => { output += data; if (output.includes('Platform UI listening')) { clearTimeout(timer); resolve() } })
      child.stderr.on('data', data => { output += data })
    })
    const origin = `http://127.0.0.1:${port}`
    for (const path of ['/products', '/products/', '/products/platform', '/products/engage', '/products/desk', '/sign-up']) {
      const response = await fetch(origin + path)
      assert.equal(response.status, 200)
      assert.match(response.headers.get('content-type'), /text\/html/)
      assert.equal(await response.text(), '<main>Product entrance fixture</main>')
    }
    const image = await fetch(origin + '/products/vehicle.webp')
    assert.equal(image.headers.get('content-type'), 'image/webp')
    assert.equal(await image.text(), 'test-image-bytes')
    assert.equal((await fetch(origin + '/health')).status, 200)
    const headers = (await fetch(origin + '/support')).headers
    const csp = headers.get('content-security-policy')
    assert.match(csp, /img-src 'self' data: blob: https:/)
    assert.match(csp, /object-src 'none'/)
    assert.ok(!csp.split('; ').find(value => value.startsWith('script-src')).includes('blob:'))
    const browser = await chromium.launch({ headless: true })
    try {
      const page = await browser.newPage()
      await page.goto(origin + '/support')
      const rendered = await page.evaluate(async () => {
        const canvas = document.createElement('canvas'); canvas.width = 8; canvas.height = 8
        canvas.getContext('2d').fillRect(0, 0, 8, 8)
        const blob = await new Promise(resolve => canvas.toBlob(resolve, 'image/jpeg'))
        const url = URL.createObjectURL(blob)
        try {
          const image = new Image()
          const result = new Promise(resolve => { image.onload = () => resolve(image.naturalWidth === 8); image.onerror = () => resolve(false) })
          image.src = url; document.body.append(image)
          return await result
        } finally { URL.revokeObjectURL(url) }
      })
      assert.equal(rendered, true, 'The real production CSP must allow authenticated JPEG Blob previews')
    } finally { await browser.close() }
    assert.equal(child.exitCode, null)
    assert.ok(!output.includes('EISDIR'))
  } finally {
    if (child && child.exitCode == null) { child.kill('SIGTERM'); await once(child, 'exit') }
    await rm(root, { recursive: true, force: true })
  }
})
