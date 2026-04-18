import React from 'react'
import ReactDOM from 'react-dom/client'
import '@shopify/polaris/build/esm/styles.css'
import App from './App'

ReactDOM.createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <App />
  </React.StrictMode>,
)
