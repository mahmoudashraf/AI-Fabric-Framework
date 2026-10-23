import { chromium } from 'playwright'
import { createServer } from 'node:http'
import { createReadStream } from 'node:fs'
import { readFile, writeFile, mkdir } from 'node:fs/promises'
import { createHash } from 'node:crypto'
import { fileURLToPath } from 'node:url'
import { resolve, sep } from 'node:path'
import assert from 'node:assert/strict'

const root=fileURLToPath(new URL('../../../doc/Productization/future-work/MarketPlace/Products/Strategy/RoadMaps/Implementation/designs/010_45_customer_console/phase-u1/',import.meta.url))
const output=process.env.CUSTOMER_U1_OUTPUT ?? '/private/tmp/01045-u1-'+Date.now()
await mkdir(output,{recursive:true})
const server=createServer(async(req,res)=>{
  try{
    const path=new URL(req.url,'http://127.0.0.1').pathname
    const file=resolve(root,'.'+(path==='/'?'/index.html':path))
    if(!file.startsWith(root.endsWith(sep)?root:root+sep)){res.writeHead(404).end();return}
    const content=await readFile(file)
    res.setHeader('Content-Type',file.endsWith('.js')?'text/javascript':file.endsWith('.css')?'text/css':'text/html')
    res.end(content)
  }catch{res.writeHead(404).end()}
})
await new Promise(resolve=>server.listen(0,'127.0.0.1',resolve))
const base=`http://127.0.0.1:${server.address().port}`
const browser=await chromium.launch()
const report={status:'RUNNING',scope:'U1_ACTUAL_CUSTOMER_SHELL_OFFLINE_FIXTURES_NOT_HOSTED_ACCEPTANCE',cases:[],errors:[],apiRequests:[]}
try{
  for(const product of ['dealership','shopify']){
    const context=await browser.newContext({viewport:{width:1440,height:960},colorScheme:'light'})
    const page=await context.newPage()
    page.on('pageerror',e=>report.errors.push(e.message))
    await context.route('**/*',route=>{
      const url=new URL(route.request().url())
      if(url.origin!==base||url.pathname.startsWith('/api/')){report.apiRequests.push(url.pathname);return route.abort()}
      return route.continue()
    })
    await page.goto(base+`/?product=${product}`)
    await page.getByRole('heading',{name:'Deployments',exact:true}).waitFor()
    const links=page.getByRole('navigation',{name:'Customer navigation'}).getByRole('link')
    assert.equal(await links.count(),7)
    assert((await links.allTextContents()).includes('Engage apps'))
    const field=page.getByLabel('Unsaved name preview')
    await field.fill('Retain this draft across themes')
    const chooser=page.getByRole('combobox',{name:'Customer color theme'})
    for(const mode of ['dark','light']){
      await chooser.click();await page.getByRole('option',{name:mode==='dark'?'Dark':'Light',exact:true}).click()
      assert.equal(await page.locator('[data-customer-theme]').getAttribute('data-customer-theme'),mode)
      assert.equal(await field.inputValue(),'Retain this draft across themes')
      await page.screenshot({path:output+`/${product}-${mode}.png`,fullPage:false})
      await page.getByRole('button',{name:'Inspect preview',exact:true}).first().click()
      const paper=page.getByRole('dialog')
      assert((await paper.evaluate(e=>getComputedStyle(e).backgroundColor))!=='rgba(0, 0, 0, 0)')
      await page.getByRole('button',{name:'Close preview',exact:true}).click()
    }
    await page.reload()
    assert.equal(await page.locator('[data-customer-theme]').getAttribute('data-customer-theme'),'light')
    await chooser.click();await page.getByRole('option',{name:'System',exact:true}).click()
    await page.emulateMedia({colorScheme:'dark'})
    await page.waitForFunction(()=>document.querySelector('[data-customer-theme]')?.getAttribute('data-customer-theme')==='dark')
    await page.setViewportSize({width:390,height:844})
    await page.getByRole('button',{name:'Open customer navigation'}).click()
    await page.getByRole('link',{name:'Knowledge & data',exact:true}).click()
    await page.getByRole('heading',{name:'Knowledge & data',exact:true}).waitFor()
    assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth+1),true)
    await page.screenshot({path:output+`/${product}-mobile.png`,fullPage:false})
    report.cases.push({product,status:'PASS',navigationEntries:7,themeDraftPreserved:true,themeReloadAndSystem:true,basicMobile:true})
    await context.close()
  }
  for(const query of ['role=PLATFORM_ADMIN','auth=API_KEY']){
    const p=await browser.newPage();await p.goto(base+'/?'+query)
    await p.getByRole('alert').filter({hasText:'customer administrator session is required'}).waitFor()
    assert.equal(await p.getByRole('navigation',{name:'Customer navigation'}).count(),0)
    await p.close()
  }
  for(const [path,expected] of [['src/layout/AppShell.tsx','8dd2be7615bfc7b7e888c4b429b2f4044e99c96e7a0d1281557be1ad8753f638'],['src/theme/platformTheme.ts','e2a7f631324e78d424bd022a19f5631f9cdece2a52f573d36632dbe62d7637a5']]){
    const bytes=await readFile(new URL('../'+path,import.meta.url))
    assert.equal(createHash('sha256').update(bytes).digest('hex'),expected,path+' staff source changed')
  }
  const shell=await readFile(new URL('../src/layout/CustomerConsoleShell.tsx',import.meta.url),'utf8')
  assert(!/dealership|shopify|vehicle|northfield/i.test(shell),'Shared shell must not contain product fixture logic')
  assert.deepEqual(report.errors,[]);assert.deepEqual(report.apiRequests,[])
  report.status='PASS'
}catch(error){report.status='FAIL';report.errors.push(String(error.stack));process.exitCode=1}
finally{await browser.close();await new Promise(resolve=>server.close(resolve));report.finishedAt=new Date().toISOString();await writeFile(output+'/report.actual.json',JSON.stringify(report,null,2));console.log(JSON.stringify(report))}
