import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {test} from 'node:test';
import {formalChecks} from './fixtures.mjs';

const initial=new URL('./results/2026-11-10-real-gpt54mini/',import.meta.url);
const followup=new URL('./results/2026-11-10-real-gpt54mini-post-metadata-policy/',import.meta.url);
const read=(root,name)=>JSON.parse(readFileSync(new URL(name+'.json',root),'utf8'));
const phases=[initial,followup].map(root=>({root,summary:read(root,'summary')}));
const records=phases.flatMap(({root,summary})=>summary.cases.map(c=>({root,record:read(root,c.id+'-result')})));
const final=read(followup,'accounting-after');

test('real qualification made exactly twenty distinct one-attempt requests without reset or top-up',()=>{
  assert.equal(records.length,20);
  assert.equal(new Set(records.map(({record})=>record.id)).size,20);
  assert.equal(new Set(records.map(({record})=>record.output.runtime.requestId)).size,20);
  assert.equal(final.chatAttempts,20);assert.equal(final.providerAttempts,20);
  assert.equal(final.limits.chatAttempts,20);
  for(const {summary} of phases)assert.ok(summary.generationCalls<=20);
  const before=read(initial,'accounting-before').output;
  const resumed=read(followup,'accounting-before').output;
  assert.equal(before.providerAttempts,0);assert.equal(resumed.providerAttempts,15);
  for(const field of ['storeUuid','approvalId','runtimeFingerprint']){
    assert.equal(before[field],final[field]);assert.equal(resumed[field],final[field]);
  }
});

test('actual provider tokens reconcile exactly to independently persisted per-request ledger receipts',()=>{
  let input=0,output=0;
  for(let i=0;i<records.length;i++){
    const {record}=records[i],runtime=record.output.runtime;
    assert.equal(record.attempt,1);assert.equal(runtime.modelCalls,1);
    assert.equal(runtime.accountingPath,'RUNTIME_PROVIDER_BUDGET');
    assert.equal(runtime.providerStatus,'SUCCESS');assert.equal(runtime.providerError,false);
    assert.equal(runtime.model,'gpt-5.4-mini-2026-03-17');
    assert.ok(runtime.elapsedMs<120000&&runtime.providerProcessingTimeMs>0);
    assert.ok(!Number.isNaN(Date.parse(runtime.deadlineAt)));
    assert.equal(runtime.accountingReceipt.ordinal,i+1);
    assert.equal(record.accounting.chatAttempts,i+1);assert.equal(record.accounting.providerAttempts,i+1);
    assert.ok(Number.isInteger(runtime.usage.prompt_tokens)&&runtime.usage.prompt_tokens>0);
    assert.ok(Number.isInteger(runtime.usage.completion_tokens)&&runtime.usage.completion_tokens<=8192);
    assert.equal(runtime.tokensUsed,runtime.usage.total_tokens);
    assert.equal(runtime.usage.total_tokens,runtime.usage.prompt_tokens+runtime.usage.completion_tokens);
    input+=runtime.usage.prompt_tokens;output+=runtime.usage.completion_tokens;
    assert.equal(record.accounting.accountedUsage.inputTokens,input);
    assert.equal(record.accounting.accountedUsage.outputTokens,output);
  }
  assert.equal(input,25155);assert.equal(output,6465);
  assert.deepEqual(final.accountedUsage,{inputTokens:input,outputTokens:output,costNanoUsd:47958750});
});

test('all original visible failures remain recorded and post-policy responses retain honest diagnostics',()=>{
  assert.equal(phases[0].summary.formalPassed,9);assert.equal(phases[1].summary.formalPassed,5);
  const failed=records.filter(({record})=>record.httpStatus!==200);
  assert.equal(failed.length,6);
  for(const {record} of failed){assert.equal(record.httpStatus,502);assert.equal(record.output.error.code,'AUTHORING_INVALID_OUTPUT');assert.equal(record.output.runtime.modelCalls,1);}
  for(const {root,record} of records){
    const fixture={request:read(root,record.id+'-request')};
    assert.deepEqual(formalChecks(fixture,record.httpStatus,record.output),record.formalChecks);
  }
  const profile=read(followup,'postpolicy-behaviour-profiles-result').output;
  assert.equal(profile.runtime.metadataRecoveryPolicy,'REPORT_AND_PRESERVE');
  assert.equal(profile.status,'READY');assert.equal(profile.changes.length,2);
  assert.deepEqual(profile.sourceReferences,[]);
  assert.equal(profile.diagnostics[0].code,'AUTHORING_SOURCE_REFERENCE_UNVERIFIED');
  assert.equal(profile.diagnostics[0].originalValue,'catalogue');assert.equal(profile.diagnostics[0].verified,false);
});

test('packaged JAR was healthy with existing funding and both qualification processes stopped',()=>{
  const boot=read(initial,'boot');
  assert.equal(boot.liveness.httpStatus,200);assert.equal(boot.unfundedReadiness.httpStatus,503);assert.equal(boot.fundedReadiness.httpStatus,200);
  const resumed=read(followup,'boot');
  assert.equal(resumed.readinessBeforeAdministration.httpStatus,200);assert.equal(resumed.fundedReadiness.httpStatus,200);
  assert.equal(read(initial,'process').stopped,true);assert.equal(read(followup,'process').stopped,true);
});
