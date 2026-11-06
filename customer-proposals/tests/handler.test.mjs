import assert from 'node:assert/strict'
import test from 'node:test'
import { createHash } from 'node:crypto'
import { createHandler } from '../supabase/functions/customer-proposals/handler.mjs'

const key = 'k'.repeat(43), code = 'c'.repeat(43), token = 't'.repeat(43)
const digest = value => createHash('sha256').update(value).digest('hex')
const request = (body, secret = key) => new Request('https://functions.example/customer-proposals', {
  method: 'POST', headers: { 'x-proposal-gateway-key': secret }, body: typeof body === 'string' ? body : JSON.stringify(body),
})
const setup = (value = {}) => {
  const calls = []
  return { calls, handler: createHandler({ supabaseUrl: 'https://project.example', serviceKey: 'private-service-key', gatewayKey: key,
    fetchImpl: async (url, options) => { calls.push({ url, options, payload: JSON.parse(options.body) }); return Response.json(value) },
  }) }
}

test('missing or incorrect gateway authentication cannot reach the database', async () => {
  const {handler,calls} = setup()
  assert.equal((await handler(request({},''))).status,403)
  assert.equal((await handler(request({},'x'.repeat(43)))).status,403)
  assert.equal(calls.length,0)
})
test('only POST with a bounded recognized payload is accepted', async () => {
  const {handler,calls} = setup()
  assert.equal((await handler(new Request('https://example.test'))).status,405)
  assert.equal((await handler(request('x'.repeat(3000)))).status,400)
  assert.equal((await handler(request({action:'content',slug:'../secret',token}))).status,400)
  assert.equal((await handler(request({action:'other',slug:'example',token}))).status,400)
  assert.equal(calls.length,0)
})
test('exchange sends only code and session digests to SQL', async () => {
  const {handler,calls} = setup({maxAge:14400})
  const response = await handler(request({action:'exchange',slug:'example',code,clientDigest:'a'.repeat(64)}))
  const result = await response.json()
  assert.equal(response.status,200)
  assert.match(result.token,/^[A-Za-z0-9_-]{43}$/)
  assert.equal(calls[0].payload.p_code_digest,digest(code))
  assert.equal(calls[0].payload.p_token_digest,digest(result.token))
  assert.ok(!JSON.stringify(calls[0].payload).includes(code))
})
test('revocation/expiry denial is preserved for content and downloads', async () => {
  const {handler,calls} = setup({error:'ACCESS_DENIED'})
  for (const action of ['content','download']) {
    assert.equal((await handler(request({action,slug:'example',token}))).status,401)
  }
  assert.equal(calls[0].payload.p_token_digest,digest(token))
  assert.equal(calls[1].payload.p_download,true)
})
test('rate limiting is retained without leaking private database diagnostics', async () => {
  const {handler} = setup({error:'RATE_LIMITED'})
  assert.equal((await handler(request({action:'exchange',slug:'example',code,clientDigest:'a'.repeat(64)}))).status,429)
  const failed = createHandler({supabaseUrl:'https://project.example',serviceKey:'secret',gatewayKey:key,fetchImpl:async()=>{throw new Error('sensitive diagnostic')}})
  const response = await failed(request({action:'content',slug:'example',token}))
  assert.equal(response.status,503)
  assert.ok(!(await response.text()).includes('sensitive'))
})
