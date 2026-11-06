import assert from 'node:assert/strict'
import test from 'node:test'
import { createServer } from 'node:http'
import { createProposalGateway } from '../../Platfrom/loomai-site/deploy/container/proposals.mjs'
const key = 'k'.repeat(43), token = 't'.repeat(43), code = 'c'.repeat(43)

async function fixture(t) {
  let revoked = false
  let logoutUnavailable = false
  const calls = []
  const gateway = createProposalGateway({endpoint:'https://project.example/functions/v1/customer-proposals',gatewayKey:key,origin:'https://loomai.pro',fetchImpl:async(url,options)=>{
    const input = JSON.parse(options.body); calls.push(input)
    if (input.action === 'exchange') return input.code === code ? Response.json({token,maxAge:14400}) : Response.json({error:'ACCESS_DENIED'},{status:401})
    if (input.token !== token || revoked) return Response.json({error:'ACCESS_DENIED'},{status:401})
    if (input.action === 'logout') return logoutUnavailable ? Response.json({error:'UNAVAILABLE'},{status:503}) : Response.json({closed:true})
    if (input.action === 'download') return Response.json({base64:Buffer.from('PK-test-document').toString('base64')})
    return Response.json({title:'Private fixture title',sections:[],downloadAvailable:true})
  }})
  const server = createServer(async(req,res)=>{ if(!await gateway(req,res,new URL(req.url,'http://localhost'))){res.writeHead(404);res.end()} })
  await new Promise(resolve=>server.listen(0,'127.0.0.1',resolve))
  t.after(()=>new Promise(resolve=>{server.close(resolve);server.closeAllConnections()}))
  const base = `http://127.0.0.1:${server.address().port}/proposals/example`
  return {base,calls,revoke:()=>{revoked=true},setLogoutUnavailable:value=>{logoutUnavailable=value},cookie:`__Secure-loomai-proposal-example=${token}`}
}
test('direct content/download need authorization and never cache', async t=>{
  const {base,calls} = await fixture(t)
  for(const route of ['content','download']) {
    const response = await fetch(`${base}/${route}`)
    assert.equal(response.status,401)
    assert.equal(response.headers.get('cache-control'),'private, no-store')
    assert.equal(response.headers.get('referrer-policy'),'no-referrer')
    assert.match(response.headers.get('x-robots-tag'),/noindex/)
    assert.ok(!(await response.text()).includes('Private fixture'))
  }
  assert.equal(calls.length,0)
})
test('new visual assets remain generic and retain the strict proposal CSP', async t=>{
  const {base}=await fixture(t)
  for(const [name,type] of [['presentation.js','javascript'],['presentation.css','text/css'],['integration.css','text/css']]) {
    const response=await fetch(`${new URL(base).origin}/proposals/assets/${name}`)
    assert.equal(response.status,200)
    assert.ok(response.headers.get('content-type').includes(type))
    assert.equal(response.headers.get('cache-control'),'private, no-store')
    assert.doesNotMatch(await response.text(),/Paul Rigby|paulrigbygroup|Private fixture/)
    const csp=response.headers.get('content-security-policy')
    assert.match(csp,/script-src 'self';/)
    assert.doesNotMatch(csp,/unsafe-inline|unsafe-eval/)
  }
})
test('manual entry requires same-origin JSON and issues HttpOnly scoped cookie', async t=>{
  const {base} = await fixture(t)
  const post = (origin,body=JSON.stringify({code}),type='application/json')=>fetch(`${base}/access`,{method:'POST',headers:{Origin:origin,'Content-Type':type},body})
  assert.equal((await post('https://evil.example')).status,403)
  assert.equal((await post('https://loomai.pro','{}','text/plain')).status,415)
  assert.equal((await post('https://loomai.pro','{broken')).status,400)
  assert.equal((await post('https://loomai.pro',JSON.stringify({code:'wrong'}))).status,401)
  const response = await post('https://loomai.pro')
  assert.equal(response.status,204)
  const cookie = response.headers.get('set-cookie')
  for(const flag of ['HttpOnly','Secure','SameSite=Lax','Path=/proposals/example','Max-Age=14400']) assert.ok(cookie.includes(flag))
  assert.equal(await response.text(),'')
})
test('invitation redirects to a clean URL without echoing code or token', async t=>{
  const {base} = await fixture(t)
  const response = await fetch(`${base}?code=${code}`,{redirect:'manual'})
  assert.equal(response.status,303)
  assert.equal(response.headers.get('location'),'/proposals/example')
  assert.equal(await response.text(),'')
  const wrong = await fetch(`${base}?code=${'x'.repeat(43)}`,{redirect:'manual'})
  assert.equal(wrong.status,303)
  assert.equal(wrong.headers.get('location'),'/proposals/example?access=invalid')
  assert.equal(wrong.headers.get('set-cookie'),null)
})
test('authorized content/download and revocation use current Supabase decision', async t=>{
  const {base,cookie,revoke} = await fixture(t)
  const response = await fetch(`${base}/content`,{headers:{Cookie:cookie}})
  assert.equal(response.status,200)
  assert.equal((await response.json()).title,'Private fixture title')
  const file = await fetch(`${base}/download`,{headers:{Cookie:cookie}})
  assert.equal(file.status,200)
  assert.match(file.headers.get('content-disposition'),/attachment/)
  assert.equal(await file.text(),'PK-test-document')
  revoke()
  for(const action of ['content','download']) assert.equal((await fetch(`${base}/${action}`,{headers:{Cookie:cookie}})).status,401)
  assert.equal((await fetch(`${base.replace('example','another')}/content`,{headers:{Cookie:cookie}})).status,401)
})
test('unsafe methods, logout CSRF and repeated code guesses are bounded', async t=>{
  const {base,cookie} = await fixture(t)
  assert.equal((await fetch(`${base}/content`,{method:'POST'})).status,405)
  assert.equal((await fetch(`${base}/logout`,{method:'POST',headers:{Cookie:cookie,Origin:'https://evil.example'}})).status,403)
  const logout = await fetch(`${base}/logout`,{method:'POST',headers:{Cookie:cookie,Origin:'https://loomai.pro'}})
  assert.equal(logout.status,204)
  assert.match(logout.headers.get('set-cookie'),/Max-Age=0/)
  for(let i=0;i<20;i++) await fetch(`${base}/access`,{method:'POST',headers:{Origin:'https://loomai.pro','Content-Type':'application/json'},body:JSON.stringify({code:'bad'})})
  const denied=await fetch(`${base}/access`,{method:'POST',headers:{Origin:'https://loomai.pro','Content-Type':'application/json'},body:JSON.stringify({code})})
  assert.equal(denied.status,429)
})
test('temporary logout failure preserves the cookie needed to retry revocation', async t=>{
  const {base,cookie,setLogoutUnavailable,calls}=await fixture(t)
  setLogoutUnavailable(true)
  const options={method:'POST',headers:{Cookie:cookie,Origin:'https://loomai.pro'}}
  const failed=await fetch(`${base}/logout`,options)
  assert.equal(failed.status,503)
  assert.equal(failed.headers.get('set-cookie'),null)
  setLogoutUnavailable(false)
  const retried=await fetch(`${base}/logout`,options)
  assert.equal(retried.status,204)
  assert.match(retried.headers.get('set-cookie'),/Max-Age=0/)
  assert.equal(calls.filter(x=>x.action==='logout').length,2)
})
