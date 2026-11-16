import assert from 'node:assert/strict'
import { spawn } from 'node:child_process'
import { once } from 'node:events'
import { createServer } from 'node:http'
import test from 'node:test'
import { chromium } from 'playwright'

test('a real browser permits the trusted Console and blocks other parents', async () => {
  let child
  let browser
  let frameUrl = ''
  const parents = [createServer(handler), createServer(handler)]
  function handler(_request, response) {
    response.setHeader('Content-Type', 'text/html')
    response.end(`<iframe title="Customer workspace" src="${frameUrl}"></iframe>`)
  }
  try {
    for (const server of parents) {
      server.listen(0, '127.0.0.1')
      await once(server, 'listening')
    }
    const listener = createServer().listen(0, '127.0.0.1')
    await once(listener, 'listening')
    const port = listener.address().port
    await new Promise(resolve => listener.close(resolve))
    const trusted = `http://127.0.0.1:${parents[0].address().port}`
    const unrelated = `http://127.0.0.1:${parents[1].address().port}`
    frameUrl = `http://127.0.0.1:${port}/health`
    child = spawn(process.execPath, ['server.mjs'], {
      cwd: new URL('..', import.meta.url),
      env: { ...process.env, PORT: String(port), PLATFORM_API_BASE_URL: 'https://api.example.test', CONSOLE_FRAME_ANCESTORS: trusted },
      stdio: ['ignore', 'pipe', 'pipe'],
    })
    await new Promise((resolve, reject) => {
      const timer = setTimeout(() => reject(new Error('Engage server did not start')), 10000)
      child.once('exit', code => { clearTimeout(timer); reject(new Error(`Engage server exited ${code}`)) })
      child.stdout.once('data', () => { clearTimeout(timer); resolve() })
    })
    browser = await chromium.launch({ headless: true })
    const page = await browser.newPage()
    await page.goto(trusted)
    assert.match(await page.frameLocator('iframe').locator('body').innerText(), /loomai-engage-standalone-workspace/)
    const blocked = []
    page.on('console', message => { if (message.type() === 'error') blocked.push(message.text()) })
    await page.goto(unrelated)
    await assert.rejects(page.frameLocator('iframe').locator('body').filter({ hasText: 'loomai-engage-standalone-workspace' }).waitFor({ timeout: 1500 }))
    assert.ok(blocked.some(message => message.includes('frame-ancestors')), blocked.join('\n'))
  } finally {
    await browser?.close()
    if (child && child.exitCode === null) { child.kill(); await once(child, 'exit') }
    for (const server of parents) await new Promise(resolve => server.close(resolve))
  }
})
