(() => {
  let preference
  try { preference = localStorage.getItem('loomai-site-color-scheme') } catch {}
  const mode = preference === 'light' || preference === 'dark'
    ? preference
    : 'light'
  document.documentElement.dataset.theme = mode
  document.querySelector('meta[name="theme-color"]')?.setAttribute('content', mode === 'dark' ? '#151719' : '#f8faff')
})()
