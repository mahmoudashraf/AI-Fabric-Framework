import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import { fileURLToPath } from 'node:url'
import test from 'node:test'
import { build } from 'vite'
import ts from 'typescript'
import { chromium } from '../../../max-mode-widget/node_modules/playwright/index.mjs'

const bundled = await build({ configFile: false, publicDir: false, logLevel: 'error', build: { write: false, minify: false,
  lib: { entry: fileURLToPath(new URL('../src/shell.ts', import.meta.url)), formats: ['es'] } } })
const shellCode = (Array.isArray(bundled) ? bundled[0] : bundled).output.find(item => item.type === 'chunk').code
const packCode = await readFile(new URL('../dist/support-experience.esm.js', import.meta.url), 'utf8')
const presetSource = await readFile(new URL('../../../max-mode-widget/src/stylePresets.ts', import.meta.url), 'utf8')
const presetCode = ts.transpileModule(presetSource, { compilerOptions: { target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.ESNext } }).outputText
const { WORKSPACE_STYLE_PRESETS } = await import(`data:text/javascript;base64,${Buffer.from(presetCode).toString('base64')}`)

async function setup(t) {
  const browser = await chromium.launch({ headless: true })
  t.after(() => browser.close())
  const page = await browser.newPage({ viewport: { width: 1360, height: 900 } })
  const errors = []
  page.on('pageerror', error => errors.push(error.message))
  await page.route('https://support-style.test/**', route => {
    const path = new URL(route.request().url()).pathname
    return route.fulfill({ contentType: path.endsWith('.js') ? 'text/javascript' : 'text/html',
      body: path === '/shell.js' ? shellCode : path === '/pack.js' ? packCode : `<!doctype html><html><head><meta name="viewport" content="width=device-width,initial-scale=1"></head>
      <body style="margin:0"><main style="height:100vh"></main><script type="module">
      import { SupportShell } from '/shell.js';
      import { mountInstallation } from '/pack.js';
      window.calls = [];
      window.state = { view:'chat', presentation:'page', requests:[], request:null, nextCursor:null, team:[], busy:false, error:null, notice:null,
        session:{authenticated:true,capabilities:[],space:{capabilities:[]}} };
      window.createShell = theme => {
        window.shell?.destroy();
        const config = {schemaVersion:'loomai-support-experience-config-v1',app:{assistantLabel:'Example Support'},
          support:{teamVisible:true,topics:[{code:'guidance',label:'Guidance'}]},capabilities:{tickets:true},theme};
        const update = change => { Object.assign(window.state, change); window.shell.render(window.state); };
        const callbacks = {chat:()=>update({view:'chat'}),submit:input=>window.calls.push(input),list:()=>update({view:'requests'}),detail:()=>{},
          openEpisode:()=>{},transition:()=>{},start:()=>{},canContinue:()=>false,continueConversation:()=>{},signIn:()=>{},notifications:()=>{},
          currentConversation:()=>null,layout:()=>{}};
        window.shell = new SupportShell(config,{update,getState:()=>window.state},callbacks,{});
        document.querySelector('main').replaceChildren(window.shell.element);
        window.shell.setMode('page'); window.shell.render(window.state);
      };
      window.mountPack = async theme => {
        window.controller?.destroy(); window.engineCalls=[];
        const state = {status:'ready',ready:true,isOpen:true,busy:false,conversationId:'existing-chat',controlState:'AI_ACTIVE',controlVersion:4,error:null};
        const maxMode = {init:config=>window.engineCalls.push({method:'init',config}),open:()=>{},close:()=>{},destroy:()=>{},getState:()=>state,
          onStateChange:()=>()=>{},setTheme:theme=>window.engineCalls.push({method:'theme',theme})};
        window.controller=await mountInstallation({installationId:'same-installation',assignmentRevision:'same-revision',deliveryMode:'standalone',
          configuration:{schemaVersion:'loomai-support-experience-config-v1',app:{assistantLabel:'Example Support'},
            capabilities:{tickets:false,handover:false},toolGroups:{default:{label:'Help',tools:[]}},toolRail:{items:[]},theme},
          widgetConfig:{apiConfig:{chatBaseUrl:'https://runtime.example',runtimeAuth:{marker:'unchanged'}},host:{authentication:{enabled:true}}},
          workspaceFrame:{setTarget:()=>{},destroy:()=>{document.querySelector('[data-loomai-support]')?.remove()}},maxMode,refreshAssignment:async()=>{}});
      };
      window.fixtureReady=true;
      </script></body></html>` })
  })
  await page.goto('https://support-style.test')
  await page.waitForFunction(() => window.fixtureReady)
  return { page, errors }
}

const rgb = hex => `rgb(${hex.slice(1).match(/../g).map(value => parseInt(value, 16)).join(', ')})`

test('Current support appearance is identical when omitted or explicit, with original semantic notices/statuses', async t => {
  const { page, errors } = await setup(t)
  const snapshot = async theme => {
    await page.evaluate(theme => { window.state.error='A visible error'; window.state.notice='A saved update'; window.createShell(theme) }, theme)
    return page.locator('[data-loomai-support]').evaluate(host => {
      const values = selector => { const style = getComputedStyle(host.shadowRoot.querySelector(selector));
        return {color:style.color,background:style.backgroundColor,font:style.fontFamily,radius:style.borderRadius} }
      return {hostColor:getComputedStyle(host).color,shell:values('.shell'),primary:values('.primary'),notice:values('.message:not(.error)'),error:values('.error')}
    })
  }
  const baseline = await snapshot(undefined)
  assert.deepEqual(await snapshot({ stylePreset: 'current' }), baseline)
  assert.equal(baseline.shell.background, 'rgb(255, 255, 255)')
  assert.equal(baseline.shell.radius, '6px')
  assert.equal(baseline.primary.background, 'rgb(8, 105, 92)')
  assert.deepEqual(errors, [])
})

test('all enhanced support shells/forms inherit light/dark tokens across modes without resetting drafts or request data', async t => {
  const { page, errors } = await setup(t)
  for (const width of [1360, 390]) {
    await page.setViewportSize({ width, height: 900 })
    for (const preset of WORKSPACE_STYLE_PRESETS.filter(style => style.id !== 'current')) {
      for (const darkMode of [false, true]) {
        await page.evaluate(theme => window.createShell(theme), { stylePreset: preset.id, darkMode })
        const expected = darkMode ? preset.dark : preset.light
        for (const mode of ['page', 'widget', 'standalone']) {
          await page.evaluate(mode => { window.shell.setMode(mode); window.shell.setExpanded(true) }, mode)
          const actual = await page.locator('[data-loomai-support]').evaluate(host => {
            const root=host.shadowRoot, style = getComputedStyle(root.querySelector('.shell')),
              input=getComputedStyle(root.querySelector('input')), primary=getComputedStyle(root.querySelector('.primary')),
              heading=getComputedStyle(root.querySelector('h1')), error=getComputedStyle(root.querySelector('.error'));
            return {preset:host.dataset.mxwStyle,dark:host.classList.contains('dark'),background:style.backgroundColor,
              inputText:input.color,inputBorder:input.borderTopColor,primaryText:primary.color,primaryBackground:primary.backgroundColor,
              heading:heading.fontFamily,error:error.color,radius:style.borderRadius}
          })
          assert.equal(actual.preset, preset.id)
          assert.equal(actual.dark, darkMode, 'Presentation switches retain the shared color-scheme class')
          assert.equal(actual.background, rgb(expected.surface), `${preset.id}/${mode}/${darkMode} surface`)
          assert.equal(actual.inputText, rgb(expected.text))
          assert.equal(actual.inputBorder, rgb(expected.border))
          assert.equal(actual.primaryBackground, rgb(expected.accent))
          assert.equal(actual.heading.includes(preset.displayFont.split(',')[0]), true)
          assert.equal(actual.error, 'rgb(146, 47, 42)', 'Errors do not become theme accents')
          assert.equal(actual.radius, mode === 'standalone' ? '0px' : preset.radius)
        }
        await page.getByRole('button', { name: 'Request help', exact: true }).click()
        await page.getByLabel('Subject', { exact: true }).fill('My retained draft')
        await page.getByLabel('Details', { exact: true }).fill('Details to retain during a color-scheme change.')
        await page.evaluate(() => {
          window.originalForm = window.shell.element.shadowRoot.querySelector('form');
          window.shell.setTheme({...window.shell.config.theme,darkMode:'auto'});
        })
        await page.emulateMedia({ colorScheme: darkMode ? 'light' : 'dark' })
        await page.waitForFunction(dark => document.querySelector('[data-loomai-support]').classList.contains('dark') === dark, !darkMode)
        assert.equal(await page.getByLabel('Subject', { exact: true }).inputValue(), 'My retained draft')
        assert.equal(await page.evaluate(() => window.shell.element.shadowRoot.querySelector('form') === window.originalForm), true)
        assert.equal(await page.locator('[data-loomai-support]').evaluate(host => host.classList.contains('dark')), !darkMode)
        assert.equal(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth), true)
        await page.getByRole('button', { name: 'Cancel', exact: true }).click()
      }
    }
  }
  assert.deepEqual(await page.evaluate(() => window.calls), [], 'Theme and layout changes must not submit requests')
  assert.deepEqual(errors, [])
})

test('Support live mounting and color-scheme updates propagate every style without reinitializing chat or changing auth/tools', async t => {
  const { page, errors } = await setup(t)
  let baseline
  for (const preset of WORKSPACE_STYLE_PRESETS) {
    await page.evaluate(theme => window.mountPack(theme), {stylePreset:preset.id,darkMode:false})
    const calls = await page.evaluate(() => window.engineCalls)
    assert.equal(calls.length, 1)
    assert.equal(calls[0].config.theme.stylePreset, preset.id)
    const {theme:_theme,...behaviour}=calls[0].config
    if (!baseline) baseline = behaviour
    else assert.deepEqual(behaviour, baseline)
    await page.evaluate(() => window.controller.setTheme('dark'))
    const changed = await page.evaluate(() => window.engineCalls)
    assert.equal(changed.filter(call=>call.method==='init').length, 1)
    assert.deepEqual(changed.at(-1), {method:'theme',theme:{stylePreset:preset.id,darkMode:true}})
    assert.equal(await page.evaluate(() => window.controller.getState().view), 'chat')
  }
  await page.evaluate(() => window.controller.destroy())
  assert.deepEqual(errors, [])
})
