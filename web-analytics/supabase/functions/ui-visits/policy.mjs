const hosts = new Map([
  ['ai-fabric.dev', 'ai-fabric'], ['www.ai-fabric.dev', 'ai-fabric'],
  ['loomai.pro', 'loomai-site'], ['www.loomai.pro', 'loomai-site'],
  ['console.loomai.pro', 'console'], ['desk.loomai.pro', 'desk'],
  ['engage.loomai.pro', 'engage'], ['engage-canary.loomai.pro', 'engage'],
  ['partners.loomai.pro', 'partners'], ['shopify-bridge.loomai.pro', 'shopify-bridge'],
])
const sections = new Set(['products', 'experiments', 'research', 'about', 'demos', 'docs', 'consultation', 'webinars',
  'course', 'bootcamps', 'maxAI', 'home', 'start', 'onboarding', 'deployments', 'ai-workspaces',
  'document-knowledge', 'integrations', 'human-support', 'human-review', 'notifications', 'users',
  'revisions', 'actions', 'prompts', 'providers', 'plugins', 'login', 'signup', 'sign-in', 'register',
  'auth', 'reset-password', 'forgot-password', 'conversations', 'reviews', 'team', 'settings', 'stores',
  'customers', 'app', 'setup', 'dashboard', 'profile', 'knowledge'])

export function siteForOrigin(origin) {
  try {
    const url = new URL(origin)
    if (url.origin !== origin || url.protocol !== 'https:' || url.port || url.username || url.password) return null
    const site = hosts.get(url.hostname)
    if (site) return { site, hostname: url.hostname }
    const suffix = '.apps.loomai.pro'
    if (url.hostname.endsWith(suffix) && /^[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?$/.test(url.hostname.slice(0, -suffix.length))) {
      return { site: 'engage', hostname: 'apps.loomai.pro' }
    }
  } catch { /* Unknown origins never submit analytics. */ }
  return null
}

// Only fixed page categories leave the browser; resource IDs and arbitrary URL text do not.
export function pageCategory(site, pathname) {
  if (site === 'engage') return '/workspace'
  if (pathname === '/') return '/'
  const section = String(pathname).split('/')[1]
  return sections.has(section) ? '/' + section : '/other'
}

export const viewportCategories = new Set(['mobile', 'tablet', 'desktop'])
