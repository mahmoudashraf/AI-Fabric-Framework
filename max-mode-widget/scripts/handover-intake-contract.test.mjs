import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import ts from 'typescript';
const code=ts.transpileModule(readFileSync(new URL('../src/handoverIntake.ts',import.meta.url),'utf8'),{
  compilerOptions:{target:ts.ScriptTarget.ES2022,module:ts.ModuleKind.CommonJS},
}).outputText;
const helpers={};new Function('exports',code)(helpers);
const {adaptHandoverIntakeFields:adapt}=helpers;

test('canonical runtime keys and enums are preserved without mutating input',()=>{
  const fields=[{key:'email',label:'Email',type:'EMAIL',required:true,maxLength:254,consentRequired:true,purpose:'Follow up',options:[]}];
  const before=structuredClone(fields);assert.deepEqual(adapt(fields),{fields,issues:[]});assert.deepEqual(fields,before);
});
test('manifest id and lowercase wire types project into canonical fields',()=>{
  const types={text:'TEXT',email:'EMAIL',tel:'PHONE',phone:'PHONE',boolean:'BOOLEAN',select:'SELECT'};
  for(const [wire,type] of Object.entries(types)){
    const result=adapt([{id:'contact',label:'Contact',type:wire,required:true,maxLength:32,options:['A','B']}]);
    assert.deepEqual(result.issues,[]);assert.equal(result.fields[0].key,'contact');assert.equal(result.fields[0].type,type);
    assert.equal(result.fields[0].required,true);assert.equal(result.fields[0].maxLength,32);assert.deepEqual(result.fields[0].options,['A','B']);
  }
});
test('requiredness, consent, ordering, choices and zero length limits are not silently replaced',()=>{
  const result=adapt([{id:'topic',label:'Topic',type:'select',required:false,options:['B','A'],maxLength:0,consentRequired:true,purpose:'Routing'}]);
  assert.equal(result.fields[0].required,false);assert.equal(result.fields[0].maxLength,0);
  assert.equal(result.fields[0].consentRequired,true);assert.equal(result.fields[0].purpose,'Routing');
  assert.deepEqual(result.fields[0].options,['B','A']);
});
test('unknown types remain explicit issues rather than becoming text fields or disappearing silently',()=>{
  const result=adapt([{id:'appointment',label:'Appointment',type:'calendar',required:true}]);
  assert.equal(result.fields.length,0);assert.match(result.issues[0],/Appointment: unsupported contact field type calendar/);
});
test('missing, duplicate and conflicting identities cannot share the same contact value key',()=>{
  for(const fields of [[{label:'Email',type:'email'}],
    [{key:'email',id:'phone',label:'Contact',type:'TEXT'}],
    [{id:'email',label:'One',type:'text'},{id:'EMAIL',label:'Two',type:'text'}]]){
    assert.ok(adapt(fields).issues.length>0);
  }
});
test('malformed field containers and items are visible contract issues, not an anonymous empty-form success',()=>{
  for(const input of [undefined,null,{},[null],[7],[[]]]) assert.ok(adapt(input).issues.length>0);
  assert.deepEqual(adapt([]),{fields:[],issues:[]});
});
