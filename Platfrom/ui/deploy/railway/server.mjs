import { createReadStream, existsSync } from 'node:fs'
import { readFile } from 'node:fs/promises'
import { createServer } from 'node:http'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const __filename = fileURLToPath(import.meta.url)
const __dirname = path.dirname(__filename)
const distDir = path.join(__dirname, 'dist')
const indexPath = path.join(distDir, 'index.html')
const port = Number(process.env.PORT || 3000)
const apiBaseUrl = (process.env.PLATFORM_UI_API_BASE_URL || process.env.VITE_PLATFORM_API_BASE_URL || '').trim()

const contentTypes = new Map([
  ['.html', 'text/html; charset=utf-8'],
  ['.js', 'application/javascript; charset=utf-8'],
  ['.css', 'text/css; charset=utf-8'],
  ['.json', 'application/json; charset=utf-8'],
  ['.svg', 'image/svg+xml'],
  ['.png', 'image/png'],
  ['.jpg', 'image/jpeg'],
  ['.jpeg', 'image/jpeg'],
  ['.gif', 'image/gif'],
  ['.ico', 'image/x-icon'],
  ['.txt', 'text/plain; charset=utf-8'],
  ['.woff', 'font/woff'],
  ['.woff2', 'font/woff2'],
])

function writeJson(response, statusCode, payload) {
  response.writeHead(statusCode, { 'Content-Type': 'application/json; charset=utf-8' })
  response.end(JSON.stringify(payload))
}

function runtimeConfigScript() {
  return `window.__PLATFORM_RUNTIME_CONFIG__ = Object.freeze({ apiBaseUrl: ${JSON.stringify(apiBaseUrl)} });\n`
}

function resolveStaticPath(urlPathname) {
  const normalized = path.normalize(urlPathname).replace(/^(\.\.[/\\])+/, '')
  const candidate = path.join(distDir, normalized)
  if (!candidate.startsWith(distDir)) {
    return null
  }
  return candidate
}

const server = createServer(async (request, response) => {
  const requestUrl = new URL(request.url || '/', 'http://127.0.0.1')

  if (requestUrl.pathname === '/health') {
    writeJson(response, 200, { status: 'UP' })
    return
  }

  if (requestUrl.pathname === '/runtime-config.js') {
    response.writeHead(200, {
      'Content-Type': 'application/javascript; charset=utf-8',
      'Cache-Control': 'no-store',
    })
    response.end(runtimeConfigScript())
    return
  }

  let targetPath = requestUrl.pathname === '/' ? indexPath : resolveStaticPath(requestUrl.pathname)
  if (!targetPath) {
    writeJson(response, 400, { success: false, message: 'Invalid path.' })
    return
  }

  if (existsSync(targetPath) && !targetPath.endsWith(path.sep)) {
    const extension = path.extname(targetPath)
    response.writeHead(200, {
      'Content-Type': contentTypes.get(extension) || 'application/octet-stream',
      'Cache-Control': extension === '.html' ? 'no-cache' : 'public, max-age=31536000, immutable',
    })
    createReadStream(targetPath).pipe(response)
    return
  }

  try {
    const indexHtml = await readFile(indexPath)
    response.writeHead(200, {
      'Content-Type': 'text/html; charset=utf-8',
      'Cache-Control': 'no-cache',
    })
    response.end(indexHtml)
  } catch (error) {
    writeJson(response, 500, {
      success: false,
      message: 'Failed to serve UI index.',
      error: error instanceof Error ? error.message : 'UNKNOWN',
    })
  }
})

server.listen(port, '0.0.0.0', () => {
  // Keep startup output compact but explicit for Railway logs.
  console.log(`Platform UI listening on port ${port}`)
  console.log(`Platform UI API base URL: ${apiBaseUrl || '(empty -> browser fallback)'}`)
})
