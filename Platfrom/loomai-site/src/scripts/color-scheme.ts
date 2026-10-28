const key = 'loomai-site-color-scheme'
type Theme = 'light' | 'dark'

export function initializeThemeControls() {
  let preference: Theme | null = null
  try {
    const stored = localStorage.getItem(key)
    if (stored === 'light' || stored === 'dark') preference = stored
  } catch { /* Theme controls still work when storage is unavailable. */ }

  const apply = () => {
    const mode = preference ?? 'light'
    document.documentElement.dataset.theme = mode
    document.querySelector('meta[name="theme-color"]')?.setAttribute('content', mode === 'dark' ? '#151719' : '#f8faff')
    document.querySelectorAll<HTMLButtonElement>('[data-theme-toggle]').forEach(button => {
      const label = mode === 'dark' ? 'Switch to light mode' : 'Switch to dark mode'
      button.setAttribute('aria-label', label)
      button.title = label
    })
  }

  document.querySelectorAll<HTMLButtonElement>('[data-theme-toggle]').forEach(button => {
    button.addEventListener('click', () => {
      preference = document.documentElement.dataset.theme === 'dark' ? 'light' : 'dark'
      try { localStorage.setItem(key, preference) } catch { /* A device preference is optional. */ }
      apply()
    })
  })
  window.addEventListener('storage', event => {
    if (event.key !== key && event.key !== null) return
    preference = event.newValue === 'light' || event.newValue === 'dark' ? event.newValue : null
    apply()
  })
  apply()
}
