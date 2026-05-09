const config = window.__LOOMAI_LANDING_CONFIG__ || {}

for (const link of document.querySelectorAll('[data-config-href]')) {
  const key = link.getAttribute('data-config-href')
  if (key && config[key]) {
    link.setAttribute('href', config[key])
  }
}

function formPayload(form) {
  const formData = new FormData(form)
  return {
    kind: form.dataset.kind || '',
    name: formData.get('name') || '',
    email: formData.get('email') || '',
    company: formData.get('company') || '',
    website: formData.get('website') || '',
    shopDomain: formData.get('shopDomain') || '',
    storeCount: formData.get('storeCount') || '',
    goal: formData.get('goal') || '',
    consent: formData.get('consent') === 'on',
    sourceUrl: window.location.href
  }
}

for (const form of document.querySelectorAll('[data-lead-form]')) {
  const status = form.querySelector('[data-form-status]')
  form.addEventListener('submit', async (event) => {
    event.preventDefault()
    const submit = form.querySelector('button[type="submit"]')
    if (status) {
      status.textContent = 'Sending request...'
      status.className = 'form-status'
    }
    if (submit) {
      submit.disabled = true
    }

    try {
      const response = await fetch('/api/leads', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(formPayload(form))
      })
      const body = await response.json().catch(() => ({}))
      if (!response.ok || body.success !== true) {
        throw new Error(body.message || 'Request could not be sent.')
      }
      form.reset()
      if (status) {
        status.textContent = 'Request received. We will follow up by email.'
        status.className = 'form-status form-status-success'
      }
    } catch (error) {
      if (status) {
        status.textContent = error instanceof Error ? error.message : 'Request could not be sent.'
        status.className = 'form-status form-status-error'
      }
    } finally {
      if (submit) {
        submit.disabled = false
      }
    }
  })
}
