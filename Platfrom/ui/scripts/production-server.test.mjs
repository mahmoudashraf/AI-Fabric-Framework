import assert from 'node:assert/strict'
import { test } from 'node:test'
import { mkdtemp, mkdir, copyFile, writeFile, rm } from 'node:fs/promises'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import { spawn } from 'node:child_process'
import { createServer } from 'node:net'
import { once } from 'node:events'

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
    assert.equal(child.exitCode, null)
    assert.ok(!output.includes('EISDIR'))
  } finally {
    if (child && child.exitCode == null) { child.kill('SIGTERM'); await once(child, 'exit') }
    await rm(root, { recursive: true, force: true })
  }
})
