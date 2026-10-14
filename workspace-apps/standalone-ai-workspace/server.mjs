import express from 'express'
import path from 'node:path'
import process from 'node:process'
import { fileURLToPath } from 'node:url'

const root = path.dirname(fileURLToPath(import.meta.url))
const app = express()
const port = positiveInteger(process.env.PORT, 8080)
const platformApiBaseUrl = requiredHttpsUrl(process.env.PLATFORM_API_BASE_URL, 'PLATFORM_API_BASE_URL')

app.disable('x-powered-by')
app.use((_request, response, next) => {
  response.set({
    'Cross-Origin-Opener-Policy': 'same-origin',
    'Permissions-Policy': 'camera=(), geolocation=(), microphone=(), payment=(), usb=()',
    'Referrer-Policy': 'no-referrer',
    'X-Content-Type-Options': 'nosniff',
    'X-Frame-Options': 'DENY',
  })
  next()
})
app.get('/health', (_request, response) => {
  response.json({ status: 'UP', service: 'loomai-engage-standalone-workspace', version: '1.0.0' })
})
app.get('/runtime-config.js', (_request, response) => {
  response.type('application/javascript')
  response.set('Cache-Control', 'no-store')
  response.send(`window.__LOOMAI_ENGAGE_CONFIG__=${JSON.stringify({ platformApiBaseUrl })};\n`)
})
app.use(express.static(path.join(root, 'dist'), {
  etag: true,
  maxAge: '1h',
  setHeaders(response, file) {
    if (/\.[A-Za-z0-9]{8,}\.(?:js|css)$/.test(file)) response.set('Cache-Control', 'public, max-age=31536000, immutable')
  },
}))
app.get(/.*/, (_request, response) => response.sendFile(path.join(root, 'dist/index.html')))

app.listen(port, '0.0.0.0', () => {
  process.stdout.write(`LoomAI Engage standalone workspace listening on ${port}\n`)
})

function positiveInteger(value, fallback) {
  const parsed = Number.parseInt(value || '', 10)
  return Number.isInteger(parsed) && parsed > 0 ? parsed : fallback
}

function requiredHttpsUrl(value, name) {
  if (!value) throw new Error(`${name} is required.`)
  const url = new URL(value)
  const local = ['localhost', '127.0.0.1'].includes(url.hostname)
  if (url.protocol !== 'https:' && !(local && url.protocol === 'http:')) throw new Error(`${name} must use HTTPS.`)
  return url.toString().replace(/\/$/, '')
}
