import assert from 'node:assert/strict'
import { createHash, randomBytes } from 'node:crypto'
import { execFileSync } from 'node:child_process'
import { existsSync, mkdirSync, readFileSync, writeFileSync } from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { proposalToContent } from '../../Platfrom/loomai-site/scripts/proposal-content.mjs'

process.umask(0o077)
const directory = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')
const privateDirectory = path.join(directory,'.private')
mkdirSync(privateDirectory,{recursive:true,mode:0o700})
const stateFile = path.join(privateDirectory,'access.private.json')
const state = existsSync(stateFile) ? JSON.parse(readFileSync(stateFile,'utf8')) : {gatewayKey:randomBytes(32).toString('base64url')}
const save = () => writeFileSync(stateFile,JSON.stringify(state,null,2)+'\n',{mode:0o600})
const projectRef = process.env.SUPABASE_PROJECT_REF
const managementToken = process.env.SUPABASE_ACCESS_TOKEN
assert.match(projectRef || '',/^[a-z]{20}$/)
assert.ok(managementToken,'SUPABASE_ACCESS_TOKEN_REQUIRED')
const request = async (route, body) => {
  const response = await fetch(`https://api.supabase.com/v1/projects/${projectRef}${route}`,{
    method:body ? 'POST' : 'GET',headers:{Authorization:`Bearer ${managementToken}`,...(body?{'Content-Type':'application/json'}:{})},
    body:body?JSON.stringify(body):undefined,signal:AbortSignal.timeout(60000),
  })
  if(!response.ok) throw new Error(`SUPABASE_HTTP_${response.status}`)
  const text=await response.text()
  return text ? JSON.parse(text) : null
}
const sql = (query,readOnly=false)=>request('/database/query',{query,read_only:readOnly})
const literal = value => "'"+String(value).replaceAll("'","''")+"'"
const sha = value=>createHash('sha256').update(value).digest('hex')
const [mode] = process.argv.slice(2)
save()

if(mode === 'migrate') {
  const before = await sql("select to_regclass('loomai_customer_proposals.customer_proposals') is not null as present",true)
  if(!before[0].present) await sql(readFileSync(path.join(directory,'supabase/migrations/202611060001_customer_proposals.sql'),'utf8'))
  const grants = await sql("select has_schema_privilege('anon','loomai_customer_proposals','USAGE') as anonymous_schema, has_schema_privilege('authenticated','loomai_customer_proposals','USAGE') as authenticated_schema, has_function_privilege('anon','public.exchange_customer_proposal_code(text,text,text,text)','EXECUTE') as anonymous_exchange, has_function_privilege('authenticated','public.read_customer_proposal(text,text,boolean)','EXECUTE') as authenticated_read",true)
  assert.ok(Object.values(grants[0]).every(x=>x===false))
  state.migration={verified:true,grants:grants[0]}; save()
  console.log(JSON.stringify({migration:'PASS',grants:grants[0]}))
} else if(mode === 'deploy-function') {
  assert.ok(state.migration?.verified)
  await request('/secrets',[{name:'CUSTOMER_PROPOSALS_GATEWAY_KEY',value:state.gatewayKey}])
  execFileSync(process.env.SUPABASE_BIN || 'supabase',['functions','deploy','customer-proposals','--workdir',directory,'--project-ref',projectRef,'--use-api','--no-verify-jwt'],{
    env:{...process.env,SUPABASE_ACCESS_TOKEN:managementToken},stdio:['ignore','pipe','pipe'],timeout:180000,
  })
  const entry=(await request('/functions')).find(x=>x.slug==='customer-proposals')
  assert.equal(entry.status,'ACTIVE'); assert.equal(entry.verify_jwt,false)
  state.function={slug:entry.slug,version:entry.version,status:entry.status}; save()
  console.log(JSON.stringify({function:state.function}))
} else if(mode === 'publish') {
  assert.ok(state.migration?.verified && state.function?.status==='ACTIVE')
  const source=path.resolve(process.env.PROPOSAL_SOURCE || '')
  const slug=process.env.PROPOSAL_SLUG
  assert.match(slug || '',/^[a-z0-9]+(?:-[a-z0-9]+)*$/)
  assert.ok(source.endsWith('.md') && existsSync(source))
  const content=proposalToContent(readFileSync(source,'utf8'),{recipient:process.env.PROPOSAL_RECIPIENT,date:process.env.PROPOSAL_DATE,version:1})
  const docx=readFileSync(source.replace(/\.md$/,'.docx')).toString('base64')
  const existing=await sql(`select id,version,status from loomai_customer_proposals.customer_proposals where slug=${literal(slug)}`,true)
  if(existing.length) {
    assert.ok(state.proposal?.slug===slug,'RECONCILE_EXISTING_PROPOSAL_BEFORE_OVERWRITE')
    console.log(JSON.stringify({proposal:slug,status:'Already published; no content or code changed'}))
  } else {
    const code=randomBytes(32).toString('base64url')
    const record=await sql(`with p as (
      insert into loomai_customer_proposals.customer_proposals(slug,recipient_label,status,content,docx_base64)
      values(${literal(slug)},${literal(content.recipient)},'published',${literal(JSON.stringify(content))}::jsonb,${literal(docx)}) returning id,version
    ) insert into loomai_customer_proposals.customer_proposal_codes(proposal_id,proposal_version,code_digest,label,expires_at)
      select id,version,${literal(sha(code))},'Owner review and customer invitation',now()+interval '30 days' from p returning id,proposal_id,expires_at`)
    state.proposal={slug,code,id:record[0].proposal_id,codeId:record[0].id,expiresAt:record[0].expires_at,
      sourceSha256:sha(readFileSync(source)),docxSha256:sha(Buffer.from(docx,'base64')),
      url:`https://loomai.pro/proposals/${slug}?code=${code}`}
    save(); console.log(JSON.stringify({proposal:slug,published:true,expiresAt:state.proposal.expiresAt,privateAccessRecord:stateFile}))
  }
} else if(mode === 'update-presentation') {
  // Presentation-only change: keep the approved Word document and access lifecycle intact.
  const slug=process.env.PROPOSAL_SLUG
  assert.equal(slug,state.proposal?.slug,'OWNED_PROPOSAL_REQUIRED')
  const visual=JSON.parse(readFileSync(process.env.PROPOSAL_PRESENTATION,'utf8'))
  assert.equal(visual.layout,'buying-workspace-v1')
  assert.equal(typeof visual.title,'string')
  const records=await sql(`select id,version,status,content from loomai_customer_proposals.customer_proposals where slug=${literal(slug)}`,true)
  assert.equal(records.length,1)
  const before=records[0]
  assert.equal(before.status,'published')
  const digest=sha(JSON.stringify(before.content))
  assert.equal(digest,process.env.PROPOSAL_EXPECTED_CONTENT_SHA256,'EXPECTED_CONTENT_HASH_REQUIRED')
  const backup=path.join(privateDirectory,`content-before-presentation-${Date.now()}.json`)
  writeFileSync(backup,JSON.stringify(before,null,2)+'\n',{mode:0o600})
  const result=await sql(`update loomai_customer_proposals.customer_proposals set content=jsonb_set(content,'{presentation}',${literal(JSON.stringify(visual))}::jsonb) where id=${literal(before.id)} and version=${before.version} and status='published' and content=${literal(JSON.stringify(before.content))}::jsonb returning id,version`)
  assert.equal(result.length,1,'PROPOSAL_CHANGED_DURING_UPDATE')
  state.presentation={sha256:sha(JSON.stringify(visual)),updatedAt:new Date().toISOString(),version:before.version,backup}
  save()
  console.log(JSON.stringify({presentation:'UPDATED',version:before.version,wordDocumentUnchanged:true,codesUnchanged:true,backup}))
} else if(mode === 'qualify-database') {
  const query=readFileSync(path.join(directory,'tests/database-qualification.sql'),'utf8')
  await sql(query)
  state.databaseQualification='PASS'; save()
  console.log(JSON.stringify({databaseQualification:'PASS',cases:['code','scope','revocation','expiry','withdrawal','version','rate-limit'],fixturesRolledBack:true}))
} else throw new Error('Use migrate, deploy-function, publish, update-presentation or qualify-database')
