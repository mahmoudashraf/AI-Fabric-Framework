(() => {
  let preference
  try { preference = localStorage.getItem('loomai-site-color-scheme') } catch {}
  const mode = preference === 'light' || preference === 'dark'
    ? preference
    : window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light'
  document.documentElement.dataset.theme = mode
  document.querySelector('meta[name="theme-color"]')?.setAttribute('content', mode === 'dark' ? '#151719' : '#f8faff')
})()
