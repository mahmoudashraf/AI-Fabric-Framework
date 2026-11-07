import assert from 'node:assert/strict';
import {readFile, mkdtemp} from 'node:fs/promises';
import {tmpdir} from 'node:os';
import path from 'node:path';
import http from 'node:http';
import {chromium} from 'playwright';

const bundle=await readFile(process.argv[2]||new URL('../dist/max-mode-widget.iife.js',import.meta.url),'utf8');
const screenshots=await mkdtemp(path.join(tmpdir(),'handover-intake-contract-'));
const fields=[
  {id:'name',label:'Name',type:'text',required:false,maxLength:120},
  {id:'email',label:'Email',type:'email',required:true,maxLength:254,consentRequired:true,purpose:'Follow up'},
  {id:'phone',label:'Phone',type:'tel',required:false,maxLength:32},
  {id:'topic',label:'Topic',type:'select',required:true,options:['Setup','Usage']},
  {id:'updates',label:'Updates',type:'boolean',required:false},
];
const cases={
  manifest:fields,
  runtime:[{key:'reason',label:'Reason',type:'TEXT',required:false}],
  unsupported:[{id:'email',label:'Email',type:'email'}, {id:'date',label:'Appointment',type:'calendar',required:true}],
  duplicate:[{id:'same',label:'First',type:'text'}, {id:'same',label:'Second',type:'text'}],
  missing:[{label:'Missing identity',type:'email',required:true}],
  zero:[{id:'empty',label:'Empty only',type:'text',maxLength:0}],
};
const requests=[],unexpected=[];
let origin;
const server=http.createServer(async(request,response)=>{
  const url=new URL(request.url,'http://fixture');
  if(url.pathname==='/widget.js')return response.writeHead(200,{'Content-Type':'application/javascript'}).end(bundle);
  if(url.pathname==='/'){
    const scenario=url.searchParams.get('case')||'manifest';
    const routes=Object.fromEntries(['conversationMessageUrlTemplate','handoverRequestUrlTemplate','handoverCancelUrlTemplate','handoverRetryUrlTemplate','conversationControlUrlTemplate','conversationEventsUrlTemplate','aiResumeRequestUrlTemplate']
      .map(name=>[name,`/${scenario}/${name}/{conversationId}`]));
    const config={apiConfig:{chatBaseUrl:origin,probeShellConfigOnOpen:false,runtimeAuth:{probeAuthContextOnOpen:false},runtimeRoutes:routes,
      conversationHandover:{enabled:true,intake:{anonymousAllowed:false,fields:cases[scenario]}}},
      host:{assistantLabel:'Support',showUtilityPanel:false},features:{cart:false,conversations:false,quickActions:false}};
    return response.writeHead(200,{'Content-Type':'text/html'}).end(`<!doctype html><html><head><meta name="viewport" content="width=device-width,initial-scale=1"></head><body>
      <script src="/widget.js"></script><script>MaxMode.init(${JSON.stringify(config)});MaxMode.open();</script></body></html>`);
  }
  if(url.pathname.includes('/conversationEventsUrlTemplate/'))return response.writeHead(200,{'Content-Type':'text/event-stream'}).end(': fixture\n\n');
  const conversationId=url.pathname.split('/').at(-1);
  if(url.pathname.includes('/conversationControlUrlTemplate/'))return json(response,200,{conversationId,controlState:'AI_ACTIVE',controlVersion:0,messages:[]});
  if(url.pathname.includes('/handoverRequestUrlTemplate/')){
    let raw='';for await(const chunk of request)raw+=chunk;
    const body=JSON.parse(raw);requests.push({scenario:url.pathname.split('/')[1],body});
    const conversation={conversationId,controlState:'AI_ACTIVE',controlVersion:0,messages:[]};
    if(url.pathname.startsWith('/runtime/'))return json(response,422,{outcome:'HANDOVER_INTAKE_REQUIRED',conversation,
      intakeSchema:{schemaVersion:'HANDOVER_INTAKE_V1',anonymousAllowed:false,fields:[{key:'email',label:'Required email',type:'EMAIL',required:true,maxLength:90,options:[]}]}});
    return json(response,200,{outcome:'HANDOVER_REQUESTED',conversation:{...conversation,controlState:'HUMAN_PENDING',controlVersion:1},message:'Fixture accepted'});
  }
  if(url.pathname==='/favicon.ico')return response.writeHead(204).end();
  unexpected.push(`${request.method} ${url.pathname}`);json(response,404,{message:'Unexpected fixture request'});
});
await new Promise(resolve=>server.listen(0,'127.0.0.1',resolve));
origin=`http://127.0.0.1:${server.address().port}`;
const browser=await chromium.launch({headless:true});
let passed=0;
async function run(name,scenario,test,mobile=false){
  const page=await browser.newPage({viewport:mobile?{width:390,height:844}:{width:1440,height:1000},reducedMotion:'reduce'});
  page.setDefaultTimeout(15000);const errors=[],outside=[];
  page.on('pageerror',error=>errors.push(error.message));
  await page.route('**/*',route=>{
    if(new URL(route.request().url()).origin===origin)return route.continue();
    outside.push(route.request().url());return route.abort();
  });
  const before=requests.length;
  try{
    await page.goto(`${origin}/?case=${scenario}`);
    await page.getByRole('button',{name:'Talk to a person',exact:true}).click();
    const dialog=page.getByRole('dialog',{name:'Talk to a person',exact:true});await dialog.waitFor();
    const submit=dialog.getByRole('button',{name:'Talk to a person',exact:true});
    await test({page,dialog,submit,submissions:()=>requests.slice(before)});
    assert.deepEqual(errors,[]);assert.deepEqual(outside,[]);assert.deepEqual(unexpected,[]);
    passed++;console.log(`PASS ${name}`);
  }catch(error){console.error({name,errors,outside,submissions:requests.slice(before),text:await page.locator('body').innerText()});throw error;}
  finally{await page.close();}
}
try{
  await run('manifest identity/type projection, independent values, required fields, consent, options and exact submitted keys','manifest',async t=>{
    const ids=await t.dialog.locator('input,select').evaluateAll(elements=>elements.map(element=>element.id));
    assert.deepEqual(ids,['handover-name','handover-email','handover-phone','handover-topic','handover-updates']);
    assert.equal(new Set(ids).size,ids.length);
    assert.equal(await t.dialog.getByLabel('Email *',{exact:true}).getAttribute('type'),'email');
    assert.equal(await t.dialog.getByLabel('Phone',{exact:true}).getAttribute('type'),'tel');
    assert.equal(await t.submit.isDisabled(),true);await t.submit.evaluate(element=>element.click());assert.equal(t.submissions().length,0);
    await t.dialog.getByLabel('Name',{exact:true}).fill('Support visitor');
    assert.equal(await t.dialog.getByLabel('Email *',{exact:true}).inputValue(),'');
    await t.dialog.getByLabel('Email *',{exact:true}).fill('visitor@example.test');
    await t.dialog.getByLabel('Phone',{exact:true}).fill('+447700900123');
    assert.equal(await t.dialog.getByLabel('Name',{exact:true}).inputValue(),'Support visitor');
    assert.equal(await t.dialog.getByLabel('Phone',{exact:true}).getAttribute('maxlength'),'32');
    assert.deepEqual(await t.dialog.getByLabel('Topic *',{exact:true}).locator('option').allTextContents(),['Select an option','Setup','Usage']);
    await t.dialog.getByLabel('Topic *',{exact:true}).selectOption('Setup');
    assert.equal(await t.submit.isDisabled(),true,'Email consent remains required');
    await t.dialog.getByRole('checkbox',{name:/I agree that this information/}).check();
    assert.equal(await t.submit.isEnabled(),true);
    await t.page.screenshot({path:path.join(screenshots,'desktop-contact-fields.png'),animations:'disabled'});
    await t.submit.click();await t.dialog.waitFor({state:'detached'});
    assert.equal(t.submissions().length,1);
    assert.deepEqual(t.submissions()[0].body.intake,{name:'Support visitor',email:'visitor@example.test',phone:'+447700900123',topic:'Setup'});
    assert.deepEqual(t.submissions()[0].body.consentedFields,['email']);
  });
  await run('canonical runtime intake returned by 422 preserves required email and prevents an empty second request','runtime',async t=>{
    await t.submit.click();
    await t.dialog.getByLabel('Required email *',{exact:true}).waitFor();
    assert.equal(await t.dialog.getByLabel('Required email *',{exact:true}).getAttribute('type'),'email');
    assert.equal(await t.dialog.getByLabel('Required email *',{exact:true}).getAttribute('maxlength'),'90');
    assert.equal(await t.submit.isDisabled(),true);await t.submit.evaluate(element=>element.click());assert.equal(t.submissions().length,1);
    await t.dialog.getByLabel('Required email *',{exact:true}).fill('runtime@example.test');assert.equal(await t.submit.isEnabled(),true);
  });
  await run('unsupported field stays visible and cannot become a silent text input or successful contact request','unsupported',async t=>{
    await t.dialog.getByRole('alert').getByText(/Appointment: unsupported contact field type calendar/).waitFor();
    await t.dialog.getByLabel('Email',{exact:true}).fill('reader@example.test');
    assert.equal(await t.dialog.locator('input').count(),1);assert.equal(await t.submit.isDisabled(),true);
    await t.submit.evaluate(element=>element.click());assert.equal(t.submissions().length,0);
  });
  for(const scenario of ['duplicate','missing'])await run(`${scenario} field identity is a visible configuration issue without malformed submitted keys`,scenario,async t=>{
    await t.dialog.getByRole('alert').waitFor();assert.equal(await t.submit.isDisabled(),true);assert.equal(t.submissions().length,0);
    const ids=await t.dialog.locator('input').evaluateAll(elements=>elements.map(element=>element.id));assert.equal(new Set(ids).size,ids.length);assert.ok(ids.every(id=>!id.includes('undefined')));
  });
  await run('zero character limit is retained','zero',async t=>{
    assert.equal(await t.dialog.getByLabel('Empty only',{exact:true}).getAttribute('maxlength'),'0');assert.equal(t.submissions().length,0);
  });
  await run('390px form retains separate contact controls and keeps an empty required request disabled','manifest',async t=>{
    assert.equal(await t.submit.isDisabled(),true);assert.equal(t.submissions().length,0);
    await t.dialog.getByLabel('Email *',{exact:true}).scrollIntoViewIfNeeded();
    assert.equal(await t.dialog.evaluate(element=>element.scrollWidth<=element.clientWidth+1),true);
    await t.page.screenshot({path:path.join(screenshots,'mobile-required-contact.png'),animations:'disabled'});
  },true);
  console.log(JSON.stringify({passed,screenshots,evidence:'Real widget IIFE with local controlled HTTP fixtures only; no live handover submissions.'}));
}finally{await browser.close();await new Promise(resolve=>server.close(resolve));}
function json(response,status,body){response.writeHead(status,{'Content-Type':'application/json'}).end(JSON.stringify(body));}
