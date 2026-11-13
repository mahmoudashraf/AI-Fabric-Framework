const form = document.querySelector('[data-profile-access]')
const feedback = document.querySelector('[data-access-feedback]')
const input = document.querySelector('[name="code"]')
const messages = {
  invalid: 'That code is invalid or has expired. Please check your invitation.',
  limited: 'Too many attempts. Please try again later.',
  unavailable: 'Access checking is temporarily unavailable. Please try again shortly.',
}
if (form && feedback && input) {
  const initial = new URL(location.href).searchParams.get('access')
  feedback.textContent = messages[initial] || ''
  form.addEventListener('submit', async event => {
    event.preventDefault()
    const button = form.querySelector('button')
    button.disabled = true
    feedback.textContent = 'Checking your invitation...'
    try {
      const response = await fetch('/founder/access', {
        method: 'POST', credentials: 'same-origin',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ code: input.value.trim() }),
      })
      input.value = ''
      if (response.status === 204) location.replace('/founder')
      else feedback.textContent = messages[response.status === 429 ? 'limited' : response.status === 401 ? 'invalid' : 'unavailable']
    } catch { feedback.textContent = messages.unavailable }
    finally { button.disabled = false }
  })
}
document.querySelector('[data-profile-lock]')?.addEventListener('click', async event => {
  const button = event.currentTarget
  button.disabled = true
  try {
    const response = await fetch('/founder/logout', { method: 'POST', credentials: 'same-origin' })
    if (response.status === 204 || response.status === 401) location.replace('/founder')
    else button.setAttribute('aria-label', 'Could not lock portfolio. Retry')
  } catch { button.setAttribute('aria-label', 'Could not lock portfolio. Retry') }
  finally { button.disabled = false }
})
window.addEventListener('pageshow', event => { if (event.persisted) location.reload() })
