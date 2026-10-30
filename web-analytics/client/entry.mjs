import { pageCategory, siteForOrigin } from '../supabase/functions/ui-visits/policy.mjs'

const endpoint = 'https://xazkenhomhtpejjjqtsy.supabase.co/functions/v1/ui-visits'
const site = siteForOrigin(window.location.origin)
const optedOut = () => navigator.globalPrivacyControl === true || navigator.doNotTrack === '1' || window.doNotTrack === '1'

if (site && !window.__loomaiUiVisitsInstalled) {
  window.__loomaiUiVisitsInstalled = true
  let previousPath
  const track = () => {
    if (optedOut() || document.visibilityState !== 'visible' || previousPath === window.location.pathname) return
    previousPath = window.location.pathname
    const viewport = window.innerWidth < 768 ? 'mobile' : window.innerWidth < 1024 ? 'tablet' : 'desktop'
    try {
      void fetch(endpoint, { method: 'POST', credentials: 'omit', mode: 'cors', cache: 'no-store', keepalive: true,
        referrerPolicy: 'no-referrer', headers: { 'Content-Type': 'text/plain;charset=UTF-8' },
        body: JSON.stringify({ page: pageCategory(site.site, window.location.pathname), viewport }),
      }).catch(() => {})
    } catch { /* Optional telemetry must never interrupt the application. */ }
  }
  for (const name of ['pushState', 'replaceState']) {
    const original = window.history[name]
    window.history[name] = function (...args) {
      const result = original.apply(this, args)
      track()
      return result
    }
  }
  window.addEventListener('popstate', track)
  window.addEventListener('pageshow', track)
  document.addEventListener('visibilitychange', track)
  document.addEventListener('astro:page-load', track)
  track()
}
