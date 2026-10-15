import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import ts from 'typescript'

const source = readFileSync(new URL('../src/installer/credentialBroker.ts', import.meta.url), 'utf8')
const compiled = ts.transpileModule(source, { compilerOptions: { module: ts.ModuleKind.ESNext, target: ts.ScriptTarget.ES2022 } }).outputText
const { credentialBrokerTokenSupplier } = await import(`data:text/javascript;base64,${Buffer.from(compiled).toString('base64')}`)
const broker = { url: 'https://runtime.example.test/api/public/auth/runtime-token', credentials: 'omit', externalAuthorizationHeader: 'Authorization' }
const manifest = { assignmentRevision: 'sha256:reviewed' }
let owner = { version: '1.0.0', identity: { status: 'authenticated', subjectKey: 'user-a' }, getIdentityAccessToken: async () => 'provider-token' }
let requests = 0
let expiresAt = Date.now() + 300_000
let resultSubject = 'user-a'
globalThis.fetch = async () => {
  requests++
  return new Response(JSON.stringify({ schemaVersion: 'loomai-workspace-runtime-token-v1', tokenType: 'Bearer',
    accessToken: 'runtime-token-with-at-least-twenty-characters', expiresAt: new Date(expiresAt).toISOString(), subjectId: resultSubject }))
}
const token = credentialBrokerTokenSupplier(broker, manifest, false, () => owner, () => {})
assert.equal(await token(), await token())
assert.equal(requests, 1)
owner = { ...owner, identity: { status: 'authenticated', subjectKey: 'user-b' } }
await assert.rejects(token(), /identity changed/)
assert.equal(requests, 1)
owner = { ...owner, identity: { status: 'authenticated', subjectKey: 'user-a' } }
assert.ok(await token())
assert.equal(requests, 2)
owner.getIdentityAccessToken = async () => null
await assert.rejects(token(), /Authentication is required/)
owner.getIdentityAccessToken = async () => 'provider-token'
resultSubject = 'user-b'
await assert.rejects(token(), /invalid identity/)
resultSubject = 'user-a'
expiresAt = Date.now() - 1
await assert.rejects(token(), /already expired/)
let complete
globalThis.fetch = () => new Promise((resolve) => { complete = resolve; requests++ })
const pending = token()
await new Promise((resolve) => setTimeout(resolve, 0))
owner.identity = { status: 'anonymous' }
complete(new Response(JSON.stringify({ schemaVersion: 'loomai-workspace-runtime-token-v1', tokenType: 'Bearer',
  accessToken: 'runtime-token-with-at-least-twenty-characters', expiresAt: new Date(Date.now() + 300_000).toISOString(), subjectId: 'user-a' })))
await assert.rejects(pending, /identity changed/)
const anonymous = credentialBrokerTokenSupplier(broker, manifest, false,
  () => ({ version: '1.0.0', identity: { status: 'anonymous' }, getIdentityAccessToken: async () => null }), () => {})
assert.equal(await anonymous(), null)
console.log('Credential broker owner/expiry/account-switch smoke passed.')
