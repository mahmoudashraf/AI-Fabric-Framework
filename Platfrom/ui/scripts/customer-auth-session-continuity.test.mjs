import assert from 'node:assert/strict'
import test from 'node:test'
import { readFile } from 'node:fs/promises'
import { fileURLToPath } from 'node:url'
import { build } from 'vite'

const output = await build({ configFile: false, publicDir: false, logLevel: 'error',
  build: { write: false, minify: false, lib: { entry: fileURLToPath(new URL('../src/auth/customerSession.ts', import.meta.url)), formats: ['es'] } } })
const code = (Array.isArray(output) ? output[0] : output).output.find(item => item.type === 'chunk').code
const { isSameAuthenticatedSubject } = await import(`data:text/javascript;base64,${Buffer.from(code).toString('base64')}`)

test('same-user sign-in/focus/token refresh preserves the authenticated presentation scope', () => {
  assert.equal(isSameAuthenticatedSubject('user-one', 'user-one'), true)
})
test('sign-out, first identity and account change never reuse the previous scope', () => {
  for (const pair of [[null, null], ['', ''], [null, 'user-one'], ['user-one', null], ['user-one', 'user-two']])
    assert.equal(isSameAuthenticatedSubject(...pair), false)
})
test('same identity revalidates server authorization; changed identity clears cached principal', async () => {
  const provider = await readFile(new URL('../src/auth/PlatformAuthProvider.tsx', import.meta.url), 'utf8')
  assert.match(provider, /isSameAuthenticatedSubject\(acceptedSupabaseSubject\.current, subject\)/)
  assert.match(provider, /if \(sameSubject\) void queryClient\.invalidateQueries\(\{ queryKey: \['platform-auth-session'\] \}\)/)
  assert.match(provider, /else replacePrincipal\('', null\)/)
  assert.match(provider, /queryClient\.clear\(\)/)
})
