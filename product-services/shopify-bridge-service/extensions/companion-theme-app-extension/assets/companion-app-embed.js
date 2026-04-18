(function () {
  function bootstrap() {
    var root = document.getElementById('loom-companion-embed-root')
    if (!root) {
      return
    }

    var bridgeBaseUrl = (root.dataset.bridgeBaseUrl || '').trim()
    var consumerId = (root.dataset.consumerId || '').trim()
    var launcherLabel = (root.dataset.launcherLabel || 'Ask the store assistant').trim()

    if (!bridgeBaseUrl || !consumerId) {
      root.dataset.status = 'configuration-required'
      return
    }

    root.dataset.status = 'ready'

    var button = document.createElement('button')
    button.type = 'button'
    button.className = 'loom-companion-launcher'
    button.setAttribute('aria-haspopup', 'dialog')
    button.textContent = launcherLabel

    var panel = document.createElement('aside')
    panel.className = 'loom-companion-panel'
    panel.hidden = true
    panel.innerHTML =
      '<header class="loom-companion-panel__header"><strong>Store assistant</strong></header>' +
      '<div class="loom-companion-panel__body">' +
      '<p>Bridge: ' + escapeHtml(bridgeBaseUrl) + '</p>' +
      '<p>Consumer: ' + escapeHtml(consumerId) + '</p>' +
      '<p>This scaffold reserves the embed surface for the Companion shopper experience. Runtime chat bootstrap is wired later through the bridge-backed storefront flow.</p>' +
      '</div>'

    button.addEventListener('click', function () {
      panel.hidden = !panel.hidden
      root.dataset.open = panel.hidden ? 'false' : 'true'
    })

    root.appendChild(button)
    root.appendChild(panel)
  }

  function escapeHtml(value) {
    return value
      .replaceAll('&', '&amp;')
      .replaceAll('<', '&lt;')
      .replaceAll('>', '&gt;')
      .replaceAll('"', '&quot;')
      .replaceAll("'", '&#39;')
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', bootstrap, { once: true })
  } else {
    bootstrap()
  }
})()
