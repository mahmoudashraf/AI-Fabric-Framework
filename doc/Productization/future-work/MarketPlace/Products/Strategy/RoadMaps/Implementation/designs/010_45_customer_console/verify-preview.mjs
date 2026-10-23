import { createRequire } from 'node:module'
import { mkdir, writeFile } from 'node:fs/promises'
import { fileURLToPath } from 'node:url'
import { resolve } from 'node:path'
import assert from 'node:assert/strict'

const require = createRequire(resolve('Platfrom/ui/package.json'))
const { chromium } = require('playwright')
const base = new URL('index.html', import.meta.url)
const output = new URL('screenshots/', import.meta.url)
await mkdir(output, { recursive: true })
const browser = await chromium.launch({ headless: true })
const report = { scope:'OFFLINE_DESIGN_PREVIEW_NOT_LIVE_ACCEPTANCE', status:'RUNNING', pages:[], checks:[], errors:[], networkRequests:[] }
try {
  const page = await browser.newPage({ viewport:{ width:1440, height:960 }, deviceScaleFactor:1 })
  page.on('pageerror', e => report.errors.push(e.message))
  page.on('request', request => {
    if (/^https?:/.test(request.url())) report.networkRequests.push(request.url())
  })
  async function open(id, stage='operating') {
    await page.goto(`${base.href}#page=${id}&stage=${stage}`)
    await page.locator('h1').waitFor()
  }
  async function capture(name) {
    await page.evaluate(()=>Promise.all(document.getAnimations().map(a=>a.finished.catch(()=>{}))))
    await page.screenshot({ path:fileURLToPath(new URL(`${name}.png`,output)), fullPage:false })
  }
  async function fit() {
    await page.evaluate(()=>Promise.all(document.getAnimations().map(a=>a.finished.catch(()=>{}))))
    const bounds=await page.evaluate(()=>({viewport:innerWidth,width:document.documentElement.scrollWidth}))
    assert(bounds.width<=bounds.viewport,`Page width ${bounds.width} exceeds viewport ${bounds.viewport}`)
  }
  const layouts=[
    ['home','home-operating',''], ['deployments','deployments-inspector','runtime'],
    ['apps','engage-app-inspector','app'], ['knowledge','knowledge-inspector','delivery'],
    ['connections','connections-inspector','provider'], ['support','human-support-inspector','conversation'],
    ['reviews','action-review-inspector','review'], ['behaviors','behaviours-inspector','brain'],
    ['activity','activity-alerts',''], ['team','account-team',''],
  ]
  for(const [id,name,selected] of layouts) {
    await open(id)
    if(selected) await page.locator(`[data-resource="${selected}"]`).first().click()
    await fit()
    if(selected) {
      await page.getByRole('dialog').waitFor()
      const position=await page.evaluate(()=>{
        const main=document.querySelector('main').getBoundingClientRect()
        const inspector=document.querySelector('.inspector').getBoundingClientRect()
        return {mainRight:main.right,panelLeft:inspector.left,panelRight:inspector.right,width:innerWidth}
      })
      assert(position.mainRight<=position.panelLeft+1,`${id}: inspector overlaps desktop content`)
      assert(position.panelRight<=position.width,`${id}: inspector outside viewport`)
    }
    await capture(name)
    report.pages.push({page:id,desktop:'PASS',screenshot:`screenshots/${name}.png`,inspector:selected||null})
    console.log(`PASS desktop ${id}${selected?' + inspector':''}`)
  }
  await open('support')
  await page.locator('[data-resource="handover"]').first().click()
  await page.locator('[data-tab="configuration"]').click()
  assert.equal(await page.getByLabel('Email',{exact:true}).inputValue(),'Required before transfer')
  await capture('handover-configuration')
  await open('behaviors')
  await page.locator('[data-resource="brain"]').first().click()
  await page.locator('[data-tab="configuration"]').click()
  await capture('behaviour-configuration')
  await open('reviews')
  await page.locator('[data-resource="review"]').first().click()
  await page.locator('[data-action="decision"]').click()
  await capture('action-decision')
  await page.keyboard.press('Escape')
  report.checks.push('Handover contact configuration, approved behaviour plan and explicit action-decision preview are represented separately.')
  await open('home','first')
  assert.equal(await page.locator('.nav-item').count(),4)
  await capture('home-first-setup')
  await page.locator('[data-action="create-dealership"]').click()
  await page.getByRole('dialog').waitFor()
  await capture('create-template')
  await page.locator('[data-action="step-next"]').click()
  await capture('create-connections')
  await page.locator('[data-action="step-next"]').click()
  await capture('create-review')
  await page.keyboard.press('Escape')
  assert.equal(await page.locator('.modal').count(),0)
  report.checks.push('First setup has four navigation items and a three-step creation preview; Escape closes dialog.')
  await open('home','staging')
  assert.equal(await page.locator('.nav-item').count(),8)
  await capture('home-staging-ready')
  await page.locator('[data-action="promote"]').click()
  assert(await page.getByRole('button',{name:'Activate production'}).isDisabled())
  await capture('promotion-blocked')
  report.checks.push('Production promotion keeps a failed prerequisite blocking activation; no simulated success bypass.')
  await open('apps')
  await page.locator('[data-resource="app"]').first().click()
  assert((await page.locator('.inspector .issue').innerText()).includes('Custom domain is not verified'))
  await page.locator('[data-tab="configuration"]').click()
  await page.getByLabel('App name',{exact:true}).fill('Preview local edit')
  await capture('engage-configuration')
  await page.getByRole('button',{name:'Save preview',exact:true}).click()
  assert((await page.getByRole('status').innerText()).includes('No configuration'))
  await page.locator('[data-action="expand"]').click()
  assert(await page.locator('.inspector').evaluate(el=>el.classList.contains('expanded')))
  await page.keyboard.press('Escape')
  assert.equal(await page.locator('.inspector').count(),0)
  await page.locator('#resource-search').fill('preview')
  assert.equal(await page.locator('#filtered-resources [data-resource]').count(),1)
  report.checks.push('App warning, configuration tab, local-only save, expanded inspector, Escape and search work.')
  await open('deployments')
  await page.locator('[data-env="staging"]').click()
  await page.locator('[data-resource="staging"]').first().click()
  await page.reload()
  assert.equal(await page.locator('[data-env="staging"]').getAttribute('class'),'active')
  assert((await page.getByRole('dialog').innerText()).includes('Customer staging'))
  await page.getByRole('button',{name:'Close details',exact:true}).click()
  await page.goBack()
  await page.getByRole('dialog').waitFor()
  report.checks.push('Target and resource survive reload, closing and browser Back without selecting another environment.')
  await open('reviews')
  await page.locator('[data-action="toggle-review"]').click()
  assert.equal(await page.locator('.nav-item[data-page="reviews"]').count(),0)
  assert((await page.locator('main').innerText()).includes('Action reviews are not installed'))
  await capture('action-reviews-not-enabled')
  report.checks.push('Disabled review capability is absent from navigation and has an honest direct-link state.')
  await page.setViewportSize({width:1280,height:800})
  await open('deployments')
  await page.locator('[data-resource="runtime"]').first().click()
  await fit()
  await capture('laptop-deployment-inspector')
  report.checks.push('Compact laptop1280x800 resource/inspector layout fits.')
  await page.setViewportSize({width:390,height:844})
  for(const [id] of layouts) {
    await open(id)
    await fit()
    const row=report.pages.find(p=>p.page===id)
    row.basicMobile='PASS'
  }
  await open('home')
  await page.getByRole('button',{name:'Open navigation'}).click()
  await capture('mobile-navigation')
  await page.locator('.sidebar [data-page="apps"]').click()
  await page.locator('[data-resource="app"]').first().click()
  const panel=await page.locator('.inspector').boundingBox()
  assert(Math.abs(panel.width-390)<1,'Mobile inspector must fill the390px viewport')
  await capture('mobile-app-inspector')
  await page.keyboard.press('Escape')
  assert.equal(await page.locator('.inspector').count(),0)
  report.checks.push('Basic mobile390px navigation and full-width inspector work; all ten page widths fit. No full mobile/accessibility certification.')
  await page.setViewportSize({width:1440,height:960})
  await open('home')
  await page.getByRole('combobox',{name:'Color theme'}).selectOption('dark')
  for(const [id] of layouts){
    await open(id)
    assert.equal(await page.locator('html').getAttribute('data-theme'),'dark')
    await fit()
    if(['home','apps','behaviors'].includes(id)){
      const resource=id==='apps'?'app':id==='behaviors'?'brain':''
      if(resource)await page.locator(`[data-resource="${resource}"]`).first().click()
      await capture(`${id}-dark`)
    }
  }
  report.checks.push('All ten desktop layouts also fit dark mode; choice survives navigation. Three dark screenshots exported.')
  await page.goto(new URL('gallery.html',import.meta.url).href)
  const images=await page.locator('img').evaluateAll(images=>images.map(img=>({src:img.getAttribute('src'),loaded:img.complete&&img.naturalWidth>0})))
  assert(images.length>=20)
  assert(images.every(image=>image.loaded),'Every gallery screenshot must load from disk')
  report.checks.push(`${images.length} gallery screenshots load from disk without external requests.`)
  assert.equal(report.errors.length,0)
  assert.equal(report.networkRequests.length,0)
  report.status='PASS'
} catch(error) {
  report.status='FAIL'
  report.errors.push(error.stack)
  process.exitCode=1
} finally {
  await browser.close()
  report.finishedAt=new Date().toISOString()
  await writeFile(new URL('preview-verification.actual.json',import.meta.url),JSON.stringify(report,null,2)+'\n')
  console.log(`${report.status}: offline design preview; no live operations.`)
}
