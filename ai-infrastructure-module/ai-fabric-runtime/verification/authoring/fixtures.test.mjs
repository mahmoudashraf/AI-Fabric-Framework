import assert from 'node:assert/strict';
import {test} from 'node:test';
import {cases,formalChecks} from './fixtures.mjs';

test('fictional qualification inputs are finite, unique and use current formal enums',()=>{
  assert.equal(cases.length,15);
  assert.equal(new Set(cases.map(c=>c.id)).size,cases.length);
  for(const {id,request} of cases){
    assert.equal(request.turnId,id);
    assert.equal(request.contractVersion,'LOOMAI_CONFIGURATION_AUTHORING_V1');
    assert.ok(['DEPLOYMENT_DRAFT','PRIVATE_DATA_PLUGIN','ENGAGE_APP'].includes(request.resourceKind));
    assert.ok(['ACTIONS','CONNECTIONS','DATA_PLUGIN','KNOWLEDGE','BEHAVIOUR','MODELS','ENGAGE','HUMAN_SUPPORT','SPECIALISTS','KIT'].includes(request.task));
    assert.ok(request.query.length<=8000&&JSON.stringify(request).length<=120000);
    assert.ok(request.sources.every(s=>s.content.length<=32000));
    assert.ok(request.priorTurns.every(t=>['USER','ASSISTANT'].includes(t.role)&&t.content.length<=8000));
  }
});

function envelope(){return {contractVersion:'LOOMAI_CONFIGURATION_AUTHORING_V1',status:'READY',message:'Proposal only',changes:[{path:'/actionsConfig/actions',operation:'UPSERT',key:'fictional',value:{name:'fictional',params:[]}}],questions:[],assumptions:[],sourceReferences:[],runtime:{modelCalls:1,accountingPath:'RUNTIME_PROVIDER_BUDGET',providerStatus:'SUCCESS',providerError:false,usage:{prompt_tokens:10,completion_tokens:5},tokensUsed:15}};}

test('formal checks accept model statuses and dynamic values without semantic text matching',()=>{
  for(const status of ['READY','NEEDS_INPUT','EXPLAIN','UNSUPPORTED']){
    const value=envelope();value.status=status;
    assert.equal(formalChecks(cases[0],200,value).passed,true);
  }
});

test('formal checks identify provider errors, wrong enum, missing values and extra calls',()=>{
  assert.deepEqual(formalChecks(cases[0],502,{error:{code:'AUTHORING_INVALID_OUTPUT'}}).failures,['HTTP_200']);
  const value=envelope();value.status='SUCCESS';delete value.changes[0].value;value.runtime.modelCalls=2;
  const result=formalChecks(cases[0],200,value);
  for(const code of ['status_enum','value_present','one_model_call'])assert.ok(result.failures.includes(code));
});

test('reported advisory defects are accepted, but unknown references cannot be classified as verified',()=>{
  const value=envelope();
  value.diagnostics=[{code:'AUTHORING_SOURCE_REFERENCE_UNVERIFIED',field:'/sourceReferences/0',message:'Unverified source',policy:'REPORT_AND_PRESERVE',originalValue:'unknown',verified:false}];
  assert.equal(formalChecks(cases[0],200,value).passed,true);
  value.diagnostics[0].verified=true;
  assert.ok(formalChecks(cases[0],200,value).failures.includes('unknown_reference_unverified'));
});
