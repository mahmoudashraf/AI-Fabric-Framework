import assert from 'node:assert/strict'
import { mkdtemp, rm } from 'node:fs/promises'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import { fileURLToPath } from 'node:url'
import { chromium } from 'playwright'
import { createServer, transformWithEsbuild } from 'vite'

const cacheDir = await mkdtemp(join(tmpdir(), 'environment-quota-ui-'))
const server = await createServer({ root: fileURLToPath(new URL('../../..', import.meta.url)), cacheDir,
  configFile: false, envFile: false, server: { host: '127.0.0.1', port: 0 },
  plugins: [{ name: 'quota-fixture', enforce: 'pre',
    resolveId(id) {
      if (id === '/__quota-entry.jsx') return '\0quota-entry.jsx'
      if (id === '/__quota-auth' || id.endsWith('/auth/PlatformAuthProvider')) return '\0quota-auth.js'
    },
    transform(code, id) { if (id === '\0quota-entry.jsx') return transformWithEsbuild(code, 'quota-entry.jsx', { loader: 'jsx', jsx: 'automatic' }) },
    load(id) {
      if (id === '\0quota-auth.js') return `import {createContext,useContext} from 'react'; export const Auth=createContext(null); export const usePlatformAuth=()=>useContext(Auth);`
      if (id === '\0quota-entry.jsx') return `import React,{useState} from 'react';import{createRoot}from'react-dom/client';
        import{QueryClient,QueryClientProvider}from'@tanstack/react-query';import{MemoryRouter}from'react-router-dom';
        import{ThemeProvider,createTheme,CssBaseline}from'@mui/material';import{Auth}from'/__quota-auth';
        import{CustomerEnvironmentQuota}from'/src/components/customer/CustomerEnvironmentQuota.tsx';
        const client=new QueryClient({defaultOptions:{queries:{retry:false,refetchOnWindowFocus:false}}});
        function App(){const[customerId,setCustomer]=useState('owner');window.changeCustomer=setCustomer;
          return <Auth.Provider value={{session:{customerId}}}><CustomerEnvironmentQuota/></Auth.Provider>}
        createRoot(document.getElementById('root')).render(<QueryClientProvider client={client}><MemoryRouter><ThemeProvider theme={createTheme({palette:{mode:'dark'}})}><CssBaseline/><main style={{maxWidth:900,padding:24}}><App/></main></ThemeProvider></MemoryRouter></QueryClientProvider>);`
    },
    configureServer(vite) { vite.middlewares.use(async (req,res,next) => {
      if (new URL(req.url,'http://fixture').pathname !== '/__quota') return next()
      res.setHeader('Content-Type','text/html');res.end(await vite.transformIndexHtml('/__quota', '<!doctype html><html><head><meta name="viewport" content="width=device-width,initial-scale=1"></head><body><div id="root"></div><script>window.__PLATFORM_RUNTIME_CONFIG__={apiBaseUrl:location.origin};</script><script type="module" src="/__quota-entry.jsx"></script></body></html>'))
    }) },
  }],
})
let browser
try {
  await server.listen(); const base = server.resolvedUrls.local[0].replace(/\/$/,'')
  browser = await chromium.launch({headless:true})
  const page = await browser.newPage({viewport:{width:1280,height:900}})
  const errors=[];page.on('pageerror',e=>errors.push(e.message))
  let customerId='owner', used=2, unavailable=false
  await page.route(`${base}/api/**`,route=>{
    assert.equal(new URL(route.request().url()).pathname,'/api/customer-self-service/infrastructure-quota')
    assert.equal(route.request().method(),'GET')
    return route.fulfill({status:unavailable?503:200,contentType:'application/json',body:JSON.stringify(unavailable?{message:'Quota unavailable'}:
      {customerId,used,limit:2,available:Math.max(0,2-used),environments:used?[{deploymentId:'dep',deploymentName:'Customer assistant',targetProfileId:'stage',environment:'staging',ownedResourcesPresent:true},{deploymentId:'dep',deploymentName:'Customer assistant',targetProfileId:'prod',environment:'production',ownedResourcesPresent:true}]:[]})})
  })
  await page.goto(`${base}/__quota`)
  await page.getByText('2 / 2 in use',{exact:true}).waitFor()
  await page.getByText('View environments in use',{exact:true}).click()
  assert.match(await page.getByRole('link',{name:'Customer assistant · production',exact:true}).getAttribute('href'),/targetProfileId=prod/)
  assert.match(await page.locator('main').innerText(),/Stopped infrastructure counts until removed/)
  used=0;await page.getByRole('button',{name:'Refresh infrastructure allowance',exact:true}).click()
  await page.getByText('0 / 2 in use',{exact:true}).waitFor()
  assert.equal(await page.getByRole('link').count(),0)
  unavailable=true;await page.getByRole('button',{name:'Refresh infrastructure allowance',exact:true}).click()
  await page.getByRole('alert').filter({hasText:'Infrastructure allowance could not be loaded'}).waitFor()
  assert.equal(await page.getByText('0 / 2 in use',{exact:true}).count(),0)
  unavailable=false;customerId='other';await page.evaluate(()=>window.changeCustomer('other'))
  await page.getByText('0 / 2 in use',{exact:true}).waitFor()
  customerId='owner';await page.getByRole('button',{name:'Refresh infrastructure allowance',exact:true}).click()
  await page.getByRole('alert').filter({hasText:'does not match this workspace'}).waitFor()
  assert.equal(await page.getByText('0 / 2 in use',{exact:true}).count(),0)
  assert.deepEqual(errors,[])
  console.log('PASS: allowance, scoped links, freed slots, unavailable state and customer-switch isolation; no mutations, no browser errors.')
} finally { await browser?.close();await server.close();await rm(cacheDir,{recursive:true,force:true}) }
