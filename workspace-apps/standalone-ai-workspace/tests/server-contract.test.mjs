import assert from 'node:assert/strict'
import test from 'node:test'
import { readFile } from 'node:fs/promises'

test('server exposes health and runtime config without embedding provider secrets', async () => {
  const source = await readFile(new URL('../server.mjs', import.meta.url), 'utf8')
  assert.match(source, /app\.get\('\/health'/)
  assert.match(source, /app\.get\('\/runtime-config\.js'/)
  assert.match(source, /'Cross-Origin-Opener-Policy': 'same-origin'/)
  assert.match(source, /'Permissions-Policy':/)
  assert.match(source, /'Referrer-Policy': 'no-referrer'/)
  assert.match(source, /'X-Content-Type-Options': 'nosniff'/)
  assert.match(source, /'X-Frame-Options': 'DENY'/)
  assert.doesNotMatch(source, /SUPABASE.*KEY/i)
})
