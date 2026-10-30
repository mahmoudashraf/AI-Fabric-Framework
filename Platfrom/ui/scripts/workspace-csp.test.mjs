import test from 'node:test'
import assert from 'node:assert/strict'
import { spawn } from 'node:child_process'
import { createServer } from 'node:net'
import { once } from 'node:events'

async function unusedPort() {
  const listener = createServer().listen(0, '127.0.0.1')
  await once(listener, 'listening')
  const port = listener.address().port
  await new Promise(resolve => listener.close(resolve))
  return port
}

for (const variant of ['container', 'railway']) {
  for (const [name, api, expected] of [
    ['same-origin', '', "script-src 'self' 'unsafe-inline'"],
    ['exact workspace assets', 'https://api.example.test/ignored-path?query=ignored', "script-src 'self' 'unsafe-inline' https://api.example.test/api/public/ai-workspace/assets/"],
    ['local development', 'http://127.0.0.1:8100', "script-src 'self' 'unsafe-inline' http://127.0.0.1:8100/api/public/ai-workspace/assets/"],
  ]) {
    test(`${variant}: ${name} retains the narrow script policy`, async () => {
      const port = await unusedPort()
      const child = spawn(process.execPath, [new URL(`../deploy/${variant}/server.mjs`, import.meta.url).pathname], {
        env: { ...process.env, PORT: String(port), PLATFORM_UI_API_BASE_URL: api, VITE_PLATFORM_API_BASE_URL: '', NODE_ENV: 'production' },
        stdio: ['ignore', 'pipe', 'pipe'],
      })
      let output = ''
      const started = new Promise((resolve, reject) => {
        const deadline = setTimeout(() => reject(new Error('Console server did not start')), 10000)
        child.once('exit', code => { clearTimeout(deadline); reject(new Error(`Console server exited ${code}`)) })
        child.stdout.on('data', data => {
          output += data
          if (output.includes('Platform UI listening')) { clearTimeout(deadline); resolve() }
        })
      })
      try {
        await started
        const response = await fetch(`http://127.0.0.1:${port}/health`)
        assert.equal(response.status, 200)
        const policy = response.headers.get('content-security-policy').split('; ')
        assert.equal(policy.find(value => value.startsWith('script-src ')), expected)
        assert.ok(policy.includes("frame-ancestors 'none'"))
        assert.ok(policy.includes("object-src 'none'"))
        assert.equal(response.headers.get('x-frame-options'), 'DENY')
      } finally {
        child.kill()
        await once(child, 'exit')
      }
    })
  }
  test(`${variant}: remote HTTP and embedded credentials cannot become script sources`, async () => {
    for (const api of ['http://api.example.test', 'https://key:secret@api.example.test']) {
      const child = spawn(process.execPath, [new URL(`../deploy/${variant}/server.mjs`, import.meta.url).pathname], {
        env: { ...process.env, PORT: '0', PLATFORM_UI_API_BASE_URL: api }, stdio: 'ignore',
      })
      const [code] = await once(child, 'exit')
      assert.notEqual(code, 0)
    }
  })
}
