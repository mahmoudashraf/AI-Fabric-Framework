import assert from 'node:assert/strict'
import { spawn } from 'node:child_process'
import { once } from 'node:events'
import { createServer } from 'node:net'
import test from 'node:test'

async function start(ancestors = '') {
  const listener = createServer().listen(0, '127.0.0.1')
  await once(listener, 'listening')
  const port = listener.address().port
  await new Promise(resolve => listener.close(resolve))
  const child = spawn(process.execPath, ['server.mjs'], {
    cwd: new URL('..', import.meta.url),
    env: { ...process.env, PORT: String(port), PLATFORM_API_BASE_URL: 'https://api.example.test', CONSOLE_FRAME_ANCESTORS: ancestors },
    stdio: ['ignore', 'pipe', 'pipe'],
  })
  const exited = once(child, 'exit')
  const ready = new Promise((resolve, reject) => {
    const timer = setTimeout(() => reject(new Error('Engage server did not start')), 10000)
    child.once('exit', code => { clearTimeout(timer); reject(new Error(`Engage server exited ${code}`)) })
    child.stdout.on('data', () => { clearTimeout(timer); resolve() })
  })
  return { child, exited, ready, url: `http://127.0.0.1:${port}` }
}

for (const [name, ancestors, expected] of [
  ['default denies all framing', '', "'none'"],
  ['only exact configured Consoles may frame', 'https://console.example.test, https://staging.example.test/,https://console.example.test', 'https://console.example.test https://staging.example.test'],
]) {
  test(name, async () => {
    const server = await start(ancestors)
    try {
      await server.ready
      for (const path of ['/health', '/runtime-config.js']) {
        const response = await fetch(`${server.url}${path}`)
        assert.equal(response.status, 200)
        assert.equal(response.headers.get('content-security-policy'), `frame-ancestors ${expected}`)
        assert.equal(response.headers.get('x-frame-options'), ancestors ? null : 'DENY')
        assert.equal(response.headers.get('x-content-type-options'), 'nosniff')
        assert.equal(response.headers.get('referrer-policy'), 'no-referrer')
        assert.equal(response.headers.get('cross-origin-opener-policy'), 'same-origin')
      }
      const config = await (await fetch(`${server.url}/runtime-config.js`)).text()
      assert.equal(config, 'window.__LOOMAI_ENGAGE_CONFIG__={"platformApiBaseUrl":"https://api.example.test"};\n')
    } finally {
      server.child.kill()
      await server.exited
    }
  })
}

test('unsafe or wildcard parent origins cannot weaken the frame policy', async () => {
  for (const source of ['*', 'https://*.example.test', 'http://console.example.test', 'https://key:secret@console.example.test', 'https://console.example.test/path', 'https://console.example.test?x=1', 'https://console.example.test#x', 'https://console.example.test;frame-ancestors']) {
    const server = await start(source)
    server.ready.catch(() => {})
    const [code] = await server.exited
    assert.notEqual(code, 0, source)
  }
})
