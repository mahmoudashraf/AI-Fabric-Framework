import assert from 'node:assert/strict'
import { mkdtemp, rm } from 'node:fs/promises'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import { fileURLToPath } from 'node:url'
import { chromium } from 'playwright'
import { createServer, transformWithEsbuild } from 'vite'

const root=fileURLToPath(new URL('../../..',import.meta.url))
const cacheDir=await mkdtemp(join(tmpdir(),'document-preparation-vite-'))
const screenshots=await mkdtemp(join(tmpdir(),'document-preparation-screenshots-'))
const server=await createServer({root,cacheDir,configFile:false,envFile:false,resolve:{dedupe:['react','react-dom']},server:{host:'127.0.0.1',port:0},
  plugins:[{name:'document-preparation-fixture',enforce:'pre',resolveId(id){if(id==='/__document-entry.jsx')return '\0document-entry.jsx'},
    transform(code,id){if(id==='\0document-entry.jsx')return transformWithEsbuild(code,'entry.jsx',{loader:'jsx',jsx:'automatic'})},
    load(id){if(id==='\0document-entry.jsx')return `
      import React,{useState} from 'react';import{createRoot}from'react-dom/client';
      import{QueryClient,QueryClientProvider}from'@tanstack/react-query';import{MemoryRouter}from'react-router-dom';
      import{ThemeProvider,createTheme,CssBaseline,Box,Typography}from'@mui/material';
      import{DocumentPreparationPanel}from'/src/components/documents/DocumentPreparationPanel.tsx';
      import{AssistantWorkspace}from'/src/assistant/AssistantWorkspace.tsx';
      const client=new QueryClient({defaultOptions:{queries:{retry:false,refetchOnWindowFocus:false},mutations:{retry:false}}});
      function App(){const[preview,setPreview]=useState();const[mode,setMode]=useState('light');const[edit,setEdit]=useState(true);const[source,setSource]=useState('source-a');
        window.__theme=setMode;window.__edit=setEdit;window.__source=setSource;
        const target={deploymentId:'dep-a',targetProfileId:'stage',releaseId:'release-a'};
        const scope={resourceKind:'DEPLOYMENT_DRAFT',resourceId:'dep-a',task:'KNOWLEDGE',editorContext:{section:'DOCUMENTS',selectedDefinitionIds:[source]}};
        return <ThemeProvider theme={createTheme({palette:{mode}})}><CssBaseline/><Box sx={{height:'100dvh',display:'flex',flexDirection:'column'}}>
          <Box sx={{px:3,py:2,borderBottom:1,borderColor:'divider'}}><Typography variant="overline">Smart Brain / Knowledge / Documents</Typography><Typography variant="h6">Customer policy.pdf</Typography></Box>
          <AssistantWorkspace scope={scope} onSaved={()=>client.invalidateQueries({queryKey:['document-preparation']})}>
            <Box sx={{p:{xs:2,md:3},minWidth:0}}><DocumentPreparationPanel key={source} target={target} sourceId={source} canEdit={edit} preview={preview}
              previewBusy={false} indexBusy={false} onEditorState={()=>{}} onPreview={async()=>setPreview(await(await fetch('/api/preview')).json())}
              onIndex={()=>fetch('/api/index',{method:'POST'})}/></Box>
          </AssistantWorkspace></Box></ThemeProvider>}
      createRoot(document.getElementById('root')).render(<QueryClientProvider client={client}><MemoryRouter><App/></MemoryRouter></QueryClientProvider>);
    `},configureServer(vite){vite.middlewares.use(async(req,res,next)=>{
      if(process.argv.includes('--serve')&&req.url.startsWith('/api/')){
        let body='';for await(const chunk of req)body+=chunk
        try{await apiRoute({request:()=>({url:()=>new URL(req.url,'http://fixture').href,method:()=>req.method,postData:()=>body||null}),
          fulfill:async({status=200,contentType,body})=>{res.statusCode=status;res.setHeader('Content-Type',contentType);res.end(body)}})}
        catch(error){res.statusCode=500;res.end(JSON.stringify({message:error.message}))}
        return
      }
      if(new URL(req.url,'http://fixture').pathname!=='/__document')return next();res.setHeader('Content-Type','text/html');
      res.end(await vite.transformIndexHtml('/__document','<!doctype html><html><head><meta name="viewport" content="width=device-width,initial-scale=1"></head><body><div id="root"></div><script>window.__PLATFORM_RUNTIME_CONFIG__={apiBaseUrl:location.origin}</script><script type="module" src="/__document-entry.jsx"></script></body></html>'))
    })}}]})
await server.listen()
const origin=`http://127.0.0.1:${server.httpServer.address().port}`
const browser=await chromium.launch({headless:true})
const page=await browser.newPage({viewport:{width:1400,height:1000}});page.setDefaultTimeout(20000)
const errors=[],requests=[];page.on('pageerror',error=>errors.push(error.message))
const configuration={processing:{chunkTokens:800,overlapTokens:0,minChunkChars:200,minEmbedChars:5,ocrMode:'AUTO',ocrLanguage:'eng',ocrDpi:150},
  evaluationOptions:{maxQuestions:5,maxDocuments:5,maxContextCharacters:8000,similarityThreshold:0,answerMaxTokens:700},questions:[{id:'q1',question:'How long do I have to return an item?',referenceAnswer:'30 days.',reviewed:false}]}
let snapshot={sourceId:'source-a',revision:1,sourceVersion:1,activeSourceVersion:1,configuration,ocrLanguages:['eng'],ocrAvailable:true,reindexRequired:false}
let runs=[],sessions=new Map(),sequence=0,evaluationSequence=0,evaluationGate=null
const preview={planId:'plan-a',source:{sourceId:'source-a',candidateVersion:1},documentCount:3,chunkCount:3,totalContentLength:1240,chunksTruncated:false,
  extraction:{format:'pdf',textSections:3,characters:1240,ocrSections:1,warnings:[]},chunks:[{chunkId:'chunk-a',chunkIndex:0,chunkCount:3,contentLength:70,
    contentPreview:'Unused items may be returned within 30 days. Contact our team first.',truncated:false}],warnings:[]}
async function apiRoute(route){
  const request=route.request(),path=new URL(request.url()).pathname,method=request.method(),body=request.postData()?JSON.parse(request.postData()):null
  requests.push({path,method,body});const send=(value,status=200)=>route.fulfill({status,contentType:'application/json',body:JSON.stringify(value)})
  if(path==='/api/platform/auth/session')return send({authenticated:true,actorId:'actor-a',customerId:'customer-a',role:'CUSTOMER_ADMIN'})
  if(path==='/api/preview'||path.endsWith('/preview'))return send({...preview,source:{...preview.source,candidateVersion:snapshot.sourceVersion}})
  if(path==='/api/index')return send({outcome:'ACCEPTED'})
  if(path.endsWith('/preparation')){
    if(method==='POST'){assert.equal(body.expectedRevision,snapshot.revision);snapshot={...snapshot,revision:snapshot.revision+1,sourceVersion:snapshot.sourceVersion+1,configuration:body.configuration,reindexRequired:true}}
    return send(snapshot)
  }
  if(path.endsWith('/evaluations')){
    if(method==='POST'){const run={id:`eval-${++evaluationSequence}`,sourceId:'source-a',status:'COMPLETED',createdAt:new Date().toISOString(),releaseId:'release-a',sourceVersion:1,candidateSourceVersion:2,reindexRequired:true,
      providerId:'structured-model-judge',rubricVersion:'document-rag-v1',modelCalls:2,maxModelCalls:2,syntheticReferences:true,configuration:snapshot.configuration,usage:[],cases:[{
        ...snapshot.configuration.questions[0],referenceReviewed:false,status:'COMPLETED',answer:'You have 30 days to return unused items [chunk-a].',
        retrieval:{evidenceCount:1,evidence:[]},evaluatedExcerpts:[{id:'chunk-a',sourceId:'source-a',content:preview.chunks[0].contentPreview}],
        evaluation:{providerId:'structured-model-judge',rubricVersion:'document-rag-v1',metrics:{faithfulness:{status:'SCORED',score:1,explanation:'The return window is stated in the retrieved excerpt.'},relevance:{status:'SCORED',score:.9,explanation:'The answer addresses the requested window.'}}}}]};runs=[run,...runs];if(evaluationGate)await evaluationGate;return send(run,202)}
    return send(runs)
  }
  if(/\/evaluations\/eval-\d+\/cancel$/.test(path))return send({...runs[0],status:'CANCELLED'})
  if(path==='/api/configuration-assistant/status')return send({enabled:true,ready:true,documentFetchEnabled:false,validationFeedback:{defaultEnabled:true,maxCorrections:2}})
  if(path==='/api/configuration-assistant/sessions'&&method==='POST'){
    const session={...body,id:`session-${++sequence}`,status:'IDLE',editable:true,displayName:'Document preparation',expiresAt:new Date(Date.now()+3600000).toISOString(),sources:[],turns:[]};sessions.set(session.id,session);return send(session)
  }
  const match=path.match(/\/configuration-assistant\/sessions\/([^/]+)(.*)/)
  if(match){const session=sessions.get(match[1]);if(!session)return send({message:'Missing session'},404)
    if(match[2]==='/sources'&&method==='POST'){session.sources.push({id:`source-${session.sources.length}`,title:body.title,mediaType:body.mediaType,content:body.content,operations:[],warnings:[],characters:body.content.length});return send(session)}
    if(match[2]==='/turns'&&method==='POST'){
      assert.equal(session.resourceKind,'DOCUMENT_SOURCE');assert.equal(body.command,'PREPARE');assert.equal(body.editorContext.section,'DOCUMENT_PREPARATION');assert.ok(body.sourceIds.length)
      snapshot={...snapshot,revision:snapshot.revision+1,sourceVersion:snapshot.sourceVersion+1,configuration:{...snapshot.configuration,processing:{...snapshot.configuration.processing,chunkTokens:400,overlapTokens:60}},reindexRequired:true}
      session.turns.push({id:`turn-${session.turns.length+1}`,query:'Optimize the selected document',command:'PREPARE',status:'SAVED',message:'Prepared smaller chunks with overlap to retain the return conditions. Review the excerpts, then reindex and evaluate.',receipt:{revision:snapshot.revision,published:false,indexed:false},createdAt:new Date().toISOString()});return send(session)
    }
    if(match[2]==='/reusable-sources')return send([])
    return send(session)
  }
  return send({message:`Unexpected ${method} ${path}`},404)
}
await page.route(`${origin}/api/**`,apiRoute)
if(process.argv.includes('--serve')){
  console.log(`Local document-intelligence UI preview (API/model fixtures, not a live Brain): ${origin}/__document`)
  await new Promise(resolve=>{process.once('SIGINT',resolve);process.once('SIGTERM',resolve)})
  await browser.close();await server.close();await rm(cacheDir,{recursive:true,force:true});process.exit(0)
}
try{
  await page.goto(`${origin}/__document`)
  await page.getByRole('button',{name:'Optimize with AI',exact:true}).waitFor()
  assert.equal(requests.some(request=>request.path.endsWith('/turns')),false,'No model request on page visit')
  await page.getByRole('button',{name:'Advanced document settings',exact:true}).click()
  await page.getByLabel('Overlap (tokens)',{exact:true}).fill('80')
  assert.equal(await page.getByRole('button',{name:'Optimize with AI',exact:true}).isDisabled(),true)
  await page.getByRole('button',{name:'Save settings',exact:true}).click()
  await page.getByText('Settings saved. The active index was not changed.',{exact:true}).waitFor()
  assert.equal(requests.some(request=>request.path==='/api/index'),false)
  await page.getByRole('button',{name:'Inspect extracted chunks',exact:true}).click()
  await page.getByText(preview.chunks[0].contentPreview,{exact:true}).waitFor()
  await page.screenshot({path:join(screenshots,'prepare-desktop.png'),fullPage:true})
  await page.getByRole('button',{name:'Optimize with AI',exact:true}).click()
  await page.getByText(/Prepared smaller chunks with overlap/).waitFor()
  assert.ok(requests.find(request=>request.path.endsWith('/sources')&&request.method==='POST').body.content.includes('within 30 days'))
  await page.getByText('400 tokens per chunk',{exact:true}).waitFor()
  const close=page.getByRole('button',{name:/Close.*assistant/i});if(await close.count())await close.first().click()
  await page.getByRole('tab',{name:'2. Index',exact:true}).click()
  await page.getByRole('button',{name:'Reindex candidate',exact:true}).click()
  assert.equal(requests.filter(request=>request.path==='/api/index').length,1)
  await page.getByRole('tab',{name:'3. Evaluate',exact:true}).click()
  await page.getByRole('button',{name:'Run evaluation',exact:true}).click()
  await page.getByText('You have 30 days to return unused items [chunk-a].',{exact:true}).waitFor()
  await page.getByText('Supported by excerpts: 100%',{exact:true}).waitFor()
  const before=requests.filter(request=>request.path.endsWith('/turns')).length
  const refinement=page.waitForResponse(response=>response.url().endsWith('/turns')&&response.request().method()==='POST')
  await page.getByRole('button',{name:'Evaluate & optimize with AI',exact:true}).click()
  await refinement
  await page.getByText(/Prepared smaller chunks with overlap/).last().waitFor()
  assert.equal(requests.filter(request=>request.path.endsWith('/turns')).length,before+1)
  const context=requests.filter(request=>request.path.endsWith('/sources')&&request.method==='POST').at(-1).body.content
  assert.equal(JSON.parse(context).evaluation.cases[0].evaluation.metrics.faithfulness.score,1,'Actual completed evaluation is supplied to optimization')
  const closeAfter=page.getByRole('button',{name:'Close assistant',exact:true});if(await closeAfter.count())await closeAfter.click()
  await page.screenshot({path:join(screenshots,'evaluate-desktop.png'),fullPage:true})
  await page.evaluate(()=>window.__theme('dark'))
  await page.screenshot({path:join(screenshots,'evaluate-dark.png'),fullPage:true})
  for(const width of[390,360]){
    await page.setViewportSize({width,height:844})
    assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth>innerWidth),false,'No horizontal page overflow')
    await page.screenshot({path:join(screenshots,`evaluate-mobile-${width}.png`),fullPage:true})
  }
  await page.evaluate(()=>window.__edit(false))
  assert.equal(await page.getByRole('button',{name:'Run evaluation',exact:true}).isDisabled(),true)
  await page.setViewportSize({width:1400,height:1000})
  await page.evaluate(()=>window.__edit(true))
  let releaseEvaluation
  evaluationGate=new Promise(resolve=>{releaseEvaluation=resolve})
  const turnsBeforeStop=requests.filter(request=>request.path.endsWith('/turns')).length
  await page.getByRole('button',{name:'Evaluate & optimize with AI',exact:true}).click()
  await page.getByRole('button',{name:'Stop evaluation',exact:true}).click()
  releaseEvaluation()
  await page.getByText('Evaluation cancelled. No optimization request was sent.',{exact:true}).waitFor()
  assert.equal(requests.filter(request=>request.path.endsWith('/turns')).length,turnsBeforeStop,'Stopping before the start receipt prevents an optimization request')
  assert.ok(requests.find(request=>/\/evaluations\/eval-\d+\/cancel$/.test(request.path)),'The run is cancelled once its ID arrives')
  assert.deepEqual(errors,[])
  console.log(`PASS document journey: manual settings, explicit indexing, actual shared assistant transport, evaluation rendering, read-only state, early cancellation, desktop/mobile/dark. API fixtures only. Screenshots: ${screenshots}`)
}catch(error){console.error('Page errors',errors);console.error('Requests',requests.map(r=>`${r.method} ${r.path}`));console.error('Visible page',await page.locator('body').innerText());throw error}
finally{await browser.close();await server.close();await rm(cacheDir,{recursive:true,force:true})}
