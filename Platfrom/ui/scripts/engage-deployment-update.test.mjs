import test from 'node:test'
import assert from 'node:assert/strict'
import {readFile} from 'node:fs/promises'
import ts from 'typescript'
const source=await readFile(new URL('../src/components/customer/engageDeploymentUpdate.ts',import.meta.url),'utf8')
const compiled=ts.transpileModule(source,{compilerOptions:{target:ts.ScriptTarget.ES2022,module:ts.ModuleKind.ESNext}})
const {applyReviewedEngageUpdate}=await import(`data:text/javascript;base64,${Buffer.from(compiled.outputText).toString('base64')}`)
const review={deploymentId:'deployment',draftId:'draft',targetProfileId:'production-image',sourceArtifactId:'current-image'}
const version={id:'version',deploymentId:'deployment',sourceDraftId:'draft'}
const release={id:'release',deploymentId:'deployment',deploymentVersionId:'version',targetProfileId:'production-image'}
function fixture(overrides={}){
  const calls=[]
  const api={authorize:async()=>{calls.push('authorize')},assertCurrent:()=>{calls.push('scope')},
    publish:async value=>{calls.push(['publish',value]);return version},compatible:async(...args)=>{calls.push(['compatible',...args]);return [{id:'current-image'}]},
    apply:async(...args)=>{calls.push(['apply',...args]);return release},...overrides}
  return {api,calls}
}
test('reviewed apply keeps the existing target, image and operation key; it never binds or activates',async()=>{
  const {api,calls}=fixture()
  assert.deepEqual(await applyReviewedEngageUpdate(review,'stable-key',api),release)
  assert.deepEqual(calls.filter(Array.isArray),[['publish',review],['compatible','deployment','version'],
    ['apply','deployment','version','production-image','current-image','stable-key']])
  assert.equal(calls.filter(x=>x==='authorize').length,2)
})
test('a foreign publication cannot be applied',async()=>{
  const {api,calls}=fixture({publish:async()=>({...version,deploymentId:'foreign'})})
  await assert.rejects(applyReviewedEngageUpdate(review,'key',api),/does not match/)
  assert.equal(calls.some(x=>Array.isArray(x)&&x[0]==='apply'),false)
})
test('an incompatible current image is not silently replaced with the first compatible image',async()=>{
  const {api,calls}=fixture({compatible:async()=>[{id:'new-image'}]})
  await assert.rejects(applyReviewedEngageUpdate(review,'key',api),/current runtime image/)
  assert.equal(calls.some(x=>Array.isArray(x)&&x[0]==='apply'),false)
})
test('scope changes after publication stop before applying',async()=>{
  const {api,calls}=fixture({assertCurrent:()=>{throw new Error('Account changed')}})
  await assert.rejects(applyReviewedEngageUpdate(review,'key',api),/Account changed/)
  assert.equal(calls.some(x=>Array.isArray(x)&&x[0]==='apply'),false)
})
test('a failed second authorization never applies a published version',async()=>{
  let count=0
  const {api,calls}=fixture({authorize:async()=>{if(++count===2)throw new Error('Expired session')}})
  await assert.rejects(applyReviewedEngageUpdate(review,'key',api),/Expired session/)
  assert.equal(calls.some(x=>Array.isArray(x)&&x[0]==='apply'),false)
})
test('Git-source updates do not invent an image argument',async()=>{
  const {api,calls}=fixture()
  await applyReviewedEngageUpdate({...review,sourceArtifactId:null},'git-key',api)
  assert.deepEqual(calls.find(x=>Array.isArray(x)&&x[0]==='apply'),['apply','deployment','version','production-image',undefined,'git-key'])
  assert.equal(calls.some(x=>Array.isArray(x)&&x[0]==='compatible'),false)
})
test('an uncertain apply error propagates without dispatching another request',async()=>{
  let attempts=0
  const {api}=fixture({apply:async()=>{attempts++;throw new Error('Timeout')}})
  await assert.rejects(applyReviewedEngageUpdate(review,'stable-key',api),/Timeout/)
  assert.equal(attempts,1)
})
test('a returned release must match the reviewed deployment, version and target',async()=>{
  for(const changed of [{deploymentId:'foreign'},{deploymentVersionId:'other'},{targetProfileId:'staging-image'}]){
    const {api}=fixture({apply:async()=>({...release,...changed})})
    await assert.rejects(applyReviewedEngageUpdate(review,'key',api),/does not match/)
  }
})
