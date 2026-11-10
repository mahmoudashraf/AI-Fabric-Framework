import assert from 'node:assert/strict';
import {createHash,createHmac,randomBytes} from 'node:crypto';
import {mkdirSync,existsSync,readFileSync,writeFileSync} from 'node:fs';
import {spawn,spawnSync} from 'node:child_process';
import {fileURLToPath,pathToFileURL} from 'node:url';
import {resolve,dirname} from 'node:path';
import {cases,formalChecks} from './fixtures.mjs';

// Explicit operator-only qualification, never a Maven/CI test or an automatic credential lookup.
assert.equal(process.argv[2],'--approved-real-provider-run','Explicit task-owner approval flag is required');
const stage=process.argv[3]??'initial';
assert.ok(['initial','post-metadata-policy'].includes(stage));
const postPolicy=stage==='post-metadata-policy';
const baselineAttempts=postPolicy?15:0;
const selectedCases=postPolicy?['swagger2-normalized','behaviour-profiles','data-http-mapping','engage-settings','specialists-current'].map(id=>{
  const fixture=structuredClone(cases.find(c=>c.id===id));
  fixture.id='postpolicy-'+id;fixture.request.turnId=fixture.id;
  if(id==='data-http-mapping')fixture.request.query+=' Also explain why a customer SQL or local-folder adapter cannot be substituted under this catalogue.';
  return fixture;
}):cases;
assert.ok(baselineAttempts+selectedCases.length<=20);
const moduleRoot=resolve(dirname(fileURLToPath(import.meta.url)),'../..');
const initialOut=resolve(moduleRoot,'verification/authoring/results/2026-11-10-real-gpt54mini');
const out=postPolicy?initialOut+'-post-metadata-policy':initialOut;
assert.ok(!existsSync(out),'A qualification run cannot be silently repeated or overwrite evidence');
const helper=process.env.LOOMAI_PRIVATE_OPERATIONS_HELPER;
const container=process.env.LOOMAI_INTERNAL_STAGING_RUNTIME_CONTAINER;
assert.ok(helper&&/^[a-f0-9]{12,64}$/.test(container??''),'Select the existing private operations helper and approved internal staging container');
const {ssh}=await import(pathToFileURL(resolve(helper)).href);
const [remote]=JSON.parse(ssh('staging',`sudo -n docker inspect ${container}`));
const remoteEnv=Object.fromEntries(remote.Config.Env.map(line=>{const i=line.indexOf('=');return[line.slice(0,i),line.slice(i+1)];}));
assert.equal(remoteEnv.AI_FABRIC_RUNTIME_DEPLOYMENT_ID,'dep-3c4dbe4f','Only the approved existing internal staging runtime is a key source');
assert.equal(remoteEnv.LOOMAI_RUNTIME_TARGET_PROFILE_ID,'dtp-coolify-staging-behavior');
const providerKey=remoteEnv.OPENAI_API_KEY;
assert.ok(providerKey&&providerKey===remoteEnv.AI_PROVIDERS_OPENAI_API_KEY,'One consistent LoomAI-owned provider credential required');
const credentialIdentity='sha256:'+createHash('sha256').update(providerKey).digest('hex');
assert.equal(remoteEnv.LOOMAI_PROVIDER_BUDGET_CREDENTIAL_IDENTITY,credentialIdentity);
const ingress=randomBytes(32).toString('hex'),signing=randomBytes(32).toString('hex');
const jar=resolve(moduleRoot,'target/ai-fabric-runtime-0.1.0-preview.jar');
const base='http://127.0.0.1:18097';
const customerId='loomai-internal',deploymentId='configuration-authoring-qualification-01037',tenantId='loomai-internal';
const issuer='platform-authoring-local',audience=deploymentId;
const rateCardReference='openai-gpt4omini-gpt54mini-embedding3small-20261024-v2';
mkdirSync(out,{recursive:true});
const sanitize=text=>[providerKey,ingress,signing].reduce((s,secret)=>s.replaceAll(secret,'[REDACTED]'),String(text));
const save=(name,value)=>writeFileSync(resolve(out,name+'.json'),sanitize(JSON.stringify(value,null,2))+'\n');
const approval=postPolicy?JSON.parse(readFileSync(resolve(initialOut,'owner-approval.json'),'utf8')).approval:{id:'authoring-01037-task-owner-20261110',scope:{customerId,deploymentId,targetProfileId:deploymentId,environment:'staging',credentialIdentity,rateCardReference},limits:{chatAttempts:20,totalTokens:2000000,costNanoUsd:5000000000},expiresAt:new Date(Date.now()+6*60*60*1000).toISOString()};
assert.equal(approval.scope.credentialIdentity,credentialIdentity);
save('owner-approval',{authority:'Current task owner explicitly approved prepaid real-provider qualification for a dedicated local service allocation, not customer allowance',maximumGenerationCalls:20,oneAttemptPerCase:true,hiddenRetries:false,providerKeySource:'Existing LoomAI-owned internal staging runtime; read-only private operations SSH helper',providerKeyPersisted:false,model:'gpt-5.4-mini',deadlineMs:120000,maxCompletionTokens:8192,approval});
function payload(operation,value){const result=spawnSync('java',['-Dloader.main=com.ai.fabric.runtime.authoring.ConfigurationAuthoringAllocationPayload','-cp',jar,'org.springframework.boot.loader.launch.PropertiesLauncher',operation],{input:JSON.stringify(value),encoding:'utf8',timeout:20000});assert.equal(result.status,0,`Existing allocation helper failed: ${sanitize(result.stderr)}`);return JSON.parse(result.stdout);}
function token(scope,correlation='authoring-local-admin'){
  const claims={sub:correlation,subjectType:'END_USER',authMode:'PRIVATE_RUNTIME_BACKEND_MEDIATED',callerType:'TRUSTED_BACKEND',sessionId:correlation,deploymentId,customerId,tenantId,iss:issuer,aud:audience,exp:new Date(Date.now()+180000).toISOString(),scopes:[scope]};
  const segment=Buffer.from(JSON.stringify(claims)).toString('base64url');
  return 'rpa1.'+segment+'.'+createHmac('sha256',signing).update(segment).digest('base64url');
}
async function call(path,{scope,correlation,body,timeout=15000}={}){
  const headers={'Content-Type':'application/json'};
  if(scope){headers['X-AIFABRIC-RUNTIME-API-KEY']=ingress;headers['X-AIFABRIC-RUNTIME-AUTHORIZATION']='Bearer '+token(scope,correlation);}
  const response=await fetch(base+path,{method:body===undefined?'GET':'POST',headers,body:body===undefined?undefined:JSON.stringify(body),signal:AbortSignal.timeout(timeout)});
  const raw=await response.text();
  let output;try{output=JSON.parse(raw);}catch{output={nonJsonResponse:raw};}
  return {httpStatus:response.status,output};
}
const env={...process.env,SPRING_PROFILES_ACTIVE:'configuration-authoring',LOOMAI_AUTHORING_ENABLED:'true',OPENAI_ENABLED:'true',OPENAI_API_KEY:providerKey,OPENAI_MODEL:'gpt-5.4-mini',AI_FABRIC_RUNTIME_AUTH_INGRESS_MODE:'VERIFIED_CONTEXT_REQUIRED',AI_FABRIC_RUNTIME_TRUSTED_BACKEND_API_KEY:ingress,AI_FABRIC_RUNTIME_PRIVATE_ASSERTION_SIGNING_KEY:signing,AI_FABRIC_RUNTIME_AUTH_ACCEPTED_ISSUERS:issuer,AI_FABRIC_RUNTIME_AUTH_ACCEPTED_AUDIENCES:audience,AI_FABRIC_RUNTIME_CUSTOMER_ID:customerId,AI_FABRIC_RUNTIME_DEPLOYMENT_ID:deploymentId,AI_FABRIC_RUNTIME_TENANT_ID:tenantId,LOOMAI_RUNTIME_TARGET_PROFILE_ID:deploymentId,LOOMAI_PROVIDER_BUDGET_ENVIRONMENT:'staging',LOOMAI_PROVIDER_BUDGET_CREDENTIAL_IDENTITY:credentialIdentity,LOOMAI_PROVIDER_BUDGET_RATE_CARD_REFERENCE:rateCardReference,LOOMAI_PROVIDER_BUDGET_OUTPUT_CAP_MODE:'MAX_COMPLETION_TOKENS',LOOMAI_AUTHORING_MAX_OUTPUT_TOKENS:'8192',LOOMAI_AUTHORING_DEADLINE_MS:'120000',LOOMAI_AUTHORING_PROVIDER_TIMEOUT_SECONDS:'115',SPRING_DATASOURCE_URL:`jdbc:h2:file:${moduleRoot}/target/authoring-real-qualification/state;DB_CLOSE_ON_EXIT=FALSE`,SPRING_DATASOURCE_DRIVER_CLASS_NAME:'org.h2.Driver',SPRING_DATASOURCE_USERNAME:'sa',SPRING_DATASOURCE_PASSWORD:'',SERVER_ADDRESS:'127.0.0.1',PORT:'18097'};
// Prevent unrelated developer environment overrides from changing this dedicated fixture.
for(const key of ['SPRING_FLYWAY_SCHEMAS','SPRING_FLYWAY_DEFAULT_SCHEMA','SPRING_CONFIG_IMPORT'])delete env[key];
const logs=[];
const child=spawn('java',['-jar',jar],{cwd:moduleRoot,env,stdio:['ignore','pipe','pipe']});
child.stdout.on('data',data=>logs.push(data.toString()));child.stderr.on('data',data=>logs.push(data.toString()));
let exited=false;child.on('exit',()=>{exited=true;});
const summary={startedAt:new Date().toISOString(),stage,initialProviderAttempts:baselineAttempts,model:'gpt-5.4-mini',maximumCalls:20,cases:[],boot:{}};
try{
  let live;
  for(let i=0;i<100;i++){
    assert.ok(!exited,'Packaged runtime exited during boot; inspect redacted runtime.log');
    try{live=await call('/actuator/health/liveness');if(live.httpStatus===200)break;}catch{}
    await new Promise(r=>setTimeout(r,1000));
  }
  assert.equal(live?.httpStatus,200,'Packaged JAR liveness must become UP');
  summary.boot.liveness=live;summary.boot.readinessBeforeAdministration=await call('/actuator/health/readiness');
  assert.equal(summary.boot.readinessBeforeAdministration.httpStatus,postPolicy?200:503);
  assert.equal((await call('/api/chat/me/conversations')).httpStatus,404);
  if(!postPolicy){
    const allocation=payload('initialize',approval);save('initialize-request',allocation);
    const init=await call('/api/admin/provider-budget/initialize',{scope:'runtime:admin:provider-budget:write',body:allocation});save('initialize-response',init);assert.equal(init.httpStatus,200);
    const activation=payload('activate',init.output);save('activate-request',activation);
    const active=await call('/api/admin/provider-budget/activate',{scope:'runtime:admin:provider-budget:write',body:activation});save('activate-response',active);assert.equal(active.httpStatus,200);
  }
  summary.boot.fundedReadiness=await call('/actuator/health/readiness');assert.equal(summary.boot.fundedReadiness.httpStatus,200);save('boot',summary.boot);
  const before=await call('/api/admin/provider-budget',{scope:'runtime:admin:overview'});save('accounting-before',before);assert.equal(before.httpStatus,200);assert.equal(before.output.providerAttempts,baselineAttempts);assert.equal(before.output.chatAttempts,baselineAttempts);assert.equal(before.output.approvalId,approval.id);
  // Persist intent before each sole generation attempt. No provider retry or case re-run exists.
  for(const fixture of selectedCases){
    assert.ok(baselineAttempts+summary.cases.length<20);
    save(fixture.id+'-request',fixture.request);
    save('inflight',{id:fixture.id,generationOrdinal:baselineAttempts+summary.cases.length+1,attempt:1,startedAt:new Date().toISOString()});
    const start=Date.now();let result;
    try{result=await call('/api/private/configuration-authoring/generate',{scope:'configuration:author',correlation:`authoring:${fixture.request.sessionId}:${fixture.request.turnId}`,body:fixture.request,timeout:130000});}
    catch(error){result={httpStatus:null,output:{transportFailure:sanitize(error.name)}};}
    const checks=formalChecks(fixture,result.httpStatus,result.output);
    const accounting=await call('/api/admin/provider-budget',{scope:'runtime:admin:overview'});
    const record={id:fixture.id,attempt:1,elapsedMs:Date.now()-start,...result,formalChecks:checks,accounting:accounting.output};
    save(fixture.id+'-result',record);summary.cases.push({id:record.id,httpStatus:record.httpStatus,status:record.output.status??null,errorCode:record.output.error?.code??null,formalChecks:checks,elapsedMs:record.elapsedMs,runtime:record.output.runtime});
    save('summary',summary);
    console.log(JSON.stringify({id:record.id,httpStatus:record.httpStatus,status:record.output.status??null,errorCode:record.output.error?.code??null,formalPassed:checks.passed,elapsedMs:record.elapsedMs,providerAttempts:accounting.output.providerAttempts,accountedUsage:accounting.output.accountedUsage}));
    assert.equal(accounting.httpStatus,200);assert.equal(accounting.output.providerAttempts,baselineAttempts+summary.cases.length,'Exactly one provider attempt per case is permitted');
  }
  summary.accountingAfter=(await call('/api/admin/provider-budget',{scope:'runtime:admin:overview'})).output;
  summary.finishedAt=new Date().toISOString();summary.generationCalls=summary.cases.length;summary.taskGenerationCalls=baselineAttempts+summary.cases.length;summary.formalPassed=summary.cases.filter(x=>x.formalChecks.passed).length;
  save('summary',summary);save('accounting-after',summary.accountingAfter);
  console.log(JSON.stringify({generationCalls:summary.generationCalls,formalPassed:summary.formalPassed,providerAttempts:summary.accountingAfter.providerAttempts,accountedUsage:summary.accountingAfter.accountedUsage}));
}finally{
  if(!exited){child.kill('SIGTERM');await Promise.race([new Promise(r=>child.once('exit',r)),new Promise(r=>setTimeout(r,15000))]);if(!exited){child.kill('SIGKILL');await new Promise(r=>child.once('exit',r));}}
  writeFileSync(resolve(out,'runtime.log'),sanitize(logs.join('')));
  save('process',{stopped:exited,exitCode:child.exitCode,signalCode:child.signalCode});
}
