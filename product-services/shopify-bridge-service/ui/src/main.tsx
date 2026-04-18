import React from 'react'
import ReactDOM from 'react-dom/client'
import '@shopify/polaris/build/esm/styles.css'
import App from './App'

installShopifyApiKeyMeta(import.meta.env.VITE_SHOPIFY_API_KEY)

ReactDOM.createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <App />
  </React.StrictMode>,
)

function installShopifyApiKeyMeta(apiKey: string | undefined) {
  if (!apiKey?.trim()) {
    return
  }
  const existing = document.querySelector('meta[name="shopify-api-key"]')
  if (existing instanceof HTMLMetaElement) {
    existing.content = apiKey
    return
  }
  const meta = document.createElement('meta')
  meta.name = 'shopify-api-key'
  meta.content = apiKey
  document.head.appendChild(meta)
}
