import { copyFile, mkdir, mkdtemp, rm } from 'node:fs/promises'
import { tmpdir } from 'node:os'
import { dirname, join } from 'node:path'
import { spawnSync } from 'node:child_process'
import { chromium } from 'playwright'

export async function createRecording(outputPath, options = {}) {
  const sourcePath = outputPath.replace(/\.mp4$/i, '.webm')
  const evidenceDir = outputPath.replace(/\.mp4$/i, '-evidence')
  const recordingDir = await mkdtemp(join(tmpdir(), 'loomai-demo-video-'))

  await mkdir(dirname(outputPath), { recursive: true })
  await rm(evidenceDir, { recursive: true, force: true })
  await mkdir(evidenceDir, { recursive: true })

  const browser = await chromium.launch({ headless: true })
  const context = await browser.newContext({
    viewport: options.viewport || { width: 1920, height: 1080 },
    recordVideo: {
      dir: recordingDir,
      size: options.videoSize || options.viewport || { width: 1920, height: 1080 },
    },
    colorScheme: 'light',
    locale: 'en-GB',
    storageState: options.storageState,
  })
  const page = await context.newPage()
  const pageErrors = []
  page.on('pageerror', (error) => pageErrors.push(error.message))

  return {
    browser,
    context,
    page,
    evidenceDir,
    async finish() {
      if (pageErrors.length > 0) {
        throw new Error(`Browser page errors: ${pageErrors.join(' | ')}`)
      }
      const video = page.video()
      await page.close()
      const videoPath = await video.path()
      await context.close()
      await browser.close()
      await copyFile(videoPath, sourcePath)

      const encoded = spawnSync('ffmpeg', [
        '-y',
        '-i', sourcePath,
        '-c:v', 'libx264',
        '-preset', 'medium',
        '-crf', '19',
        '-pix_fmt', 'yuv420p',
        '-movflags', '+faststart',
        '-an',
        outputPath,
      ], { encoding: 'utf8' })
      if (encoded.status !== 0) {
        throw new Error(`ffmpeg failed: ${encoded.stderr}`)
      }
      await rm(recordingDir, { recursive: true, force: true })
      return { outputPath, sourcePath, evidenceDir }
    },
    async abort() {
      if (!page.isClosed()) await page.close().catch(() => undefined)
      await context.close().catch(() => undefined)
      await browser.close().catch(() => undefined)
      await rm(recordingDir, { recursive: true, force: true })
    },
  }
}

export async function installPresentationLayer(page) {
  await page.addStyleTag({ content: `
    #loomai-demo-stage {
      position: fixed;
      z-index: 2147483647;
      top: 18px;
      left: 50%;
      width: min(920px, calc(100vw - 48px));
      transform: translateX(-50%);
      border: 1px solid rgba(168, 187, 218, 0.86);
      border-radius: 10px;
      background: rgba(255, 255, 255, 0.97);
      box-shadow: 0 16px 44px rgba(12, 35, 74, 0.2);
      color: #10294b;
      font-family: Inter, ui-sans-serif, system-ui, sans-serif;
      padding: 14px 18px;
      pointer-events: none;
    }
    #loomai-demo-stage strong { display: block; font-size: 18px; line-height: 1.25; }
    #loomai-demo-stage span { color: #526985; display: block; font-size: 13px; line-height: 1.45; margin-top: 4px; }
    #loomai-demo-pointer {
      position: fixed;
      z-index: 2147483647;
      width: 22px;
      height: 22px;
      border: 3px solid #0b68ff;
      border-radius: 50%;
      background: rgba(255,255,255,.82);
      box-shadow: 0 0 0 8px rgba(11,104,255,.16);
      transform: translate(-50%, -50%);
      transition: left .32s ease, top .32s ease, transform .18s ease;
      pointer-events: none;
    }
    #loomai-demo-pointer[data-click='true'] { transform: translate(-50%, -50%) scale(.7); }
    #loomai-demo-pointer-label {
      position: fixed;
      z-index: 2147483647;
      border-radius: 6px;
      background: #0d3975;
      color: #fff;
      font: 700 12px/1.3 Inter, ui-sans-serif, system-ui, sans-serif;
      padding: 7px 10px;
      transform: translate(18px, 14px);
      white-space: nowrap;
      pointer-events: none;
    }
  ` })
  await page.evaluate(() => {
    const stage = document.createElement('div')
    stage.id = 'loomai-demo-stage'
    stage.innerHTML = '<strong>Live validation</strong><span>Preparing the verified demonstration.</span>'
    const pointer = document.createElement('div')
    pointer.id = 'loomai-demo-pointer'
    pointer.hidden = true
    const label = document.createElement('div')
    label.id = 'loomai-demo-pointer-label'
    label.hidden = true
    document.body.append(stage, pointer, label)
  })
}

export async function showStage(page, title, description, waitMs = 2600) {
  await page.evaluate(({ titleText, descriptionText }) => {
    const stage = document.querySelector('#loomai-demo-stage')
    stage.innerHTML = ''
    const heading = document.createElement('strong')
    heading.textContent = titleText
    const copy = document.createElement('span')
    copy.textContent = descriptionText
    stage.append(heading, copy)
  }, { titleText: title, descriptionText: description })
  await page.waitForTimeout(waitMs)
}

export async function pointAt(page, locator, label, waitMs = 850) {
  await locator.scrollIntoViewIfNeeded()
  const box = await locator.boundingBox()
  if (!box) throw new Error(`Unable to point at action: ${label}`)
  await page.evaluate(({ x, y, labelText }) => {
    const pointer = document.querySelector('#loomai-demo-pointer')
    const pointerLabel = document.querySelector('#loomai-demo-pointer-label')
    pointer.hidden = false
    pointer.style.left = `${x}px`
    pointer.style.top = `${y}px`
    pointerLabel.hidden = false
    pointerLabel.style.left = `${x}px`
    pointerLabel.style.top = `${y}px`
    pointerLabel.textContent = labelText
  }, { x: box.x + box.width / 2, y: box.y + box.height / 2, labelText: label })
  await page.waitForTimeout(waitMs)
}

export async function clickWithPointer(page, locator, label) {
  await pointAt(page, locator, label, 650)
  await page.evaluate(() => {
    const pointer = document.querySelector('#loomai-demo-pointer')
    pointer.dataset.click = 'true'
    window.setTimeout(() => { pointer.dataset.click = 'false' }, 220)
  })
  await page.waitForTimeout(260)
  await locator.click()
}

export async function hidePointer(page) {
  await page.evaluate(() => {
    document.querySelector('#loomai-demo-pointer').hidden = true
    document.querySelector('#loomai-demo-pointer-label').hidden = true
  })
}
