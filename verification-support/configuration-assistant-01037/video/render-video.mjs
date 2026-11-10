import assert from 'node:assert/strict'
import { mkdirSync, readFileSync, writeFileSync, existsSync } from 'node:fs'
import { createRequire } from 'node:module'
import { dirname, resolve } from 'node:path'
import { spawnSync } from 'node:child_process'

const [rootArgument, planArgument, outputArgument] = process.argv.slice(2)
assert.ok(rootArgument && planArgument && outputArgument, 'Provide private capture root, edit plan and output MP4')
const root = resolve(rootArgument), output = resolve(outputArgument)
const take = JSON.parse(readFileSync(resolve(root, 'take.private.json'), 'utf8'))
assert.notEqual(take.status, 'FAIL', 'Review and retain failed takes; do not render them as a successful demonstration')
const plan = JSON.parse(readFileSync(resolve(planArgument), 'utf8'))
assert.ok(!existsSync(output), 'Preserve earlier recordings; select a new output name')
mkdirSync(dirname(output), { recursive: true })
const work = resolve(root, 'render')
mkdirSync(work, { recursive: true, mode: 0o700 })
const { chromium } = createRequire(new URL('../../../Platfrom/ui/package.json', import.meta.url))('playwright')
const run = (command, args) => {
  const result = spawnSync(command, args, { encoding: 'utf8', maxBuffer: 12 * 1024 * 1024 })
  assert.equal(result.status, 0, `${command}: ${result.stderr?.slice(-4000)}`)
  return result.stdout
}
const duration = file => Number(run('ffprobe', ['-v', 'error', '-show_entries', 'format=duration', '-of', 'csv=p=0', file]).trim())
const browser = await chromium.launch()
const context = await browser.newContext({ viewport: { width: 1600, height: 120 } })
const page = await context.newPage()
const parts = [], chapters = []
let offset = 0
try {
  for (const [index, segment] of plan.segments.entries()) {
    const prefix = resolve(work, String(index).padStart(2, '0'))
    const sourceDuration = segment.end - segment.start
    assert.ok(sourceDuration > 0 && segment.start >= 0 && segment.end <= take.durationSeconds + .1)
    const speed = segment.speed ?? 1
    assert.ok(speed >= .5 && speed <= 8)
    const text = prefix + '.txt', audio = prefix + '.aiff', caption = prefix + '.png', part = prefix + '.mp4'
    writeFileSync(text, segment.narration + '\n', { mode: 0o600 })
    run('say', ['-v', 'Daniel', '-r', '165', '-f', text, '-o', audio])
    const voiceDuration = duration(audio)
    const seconds = Math.max(sourceDuration / speed, voiceDuration + 1.5, segment.minimumSeconds ?? 0)
    const freeze = Math.max(0, seconds - sourceDuration / speed)
    await page.setContent(`<!doctype html><html><head><style>
      *{box-sizing:border-box}body{margin:0;width:1600px;height:120px;background:#111c22;color:#f1f6f8;font-family:Arial,Helvetica,sans-serif}
      main{height:120px;border-top:2px solid #58ceb6;padding:15px 30px;display:flex;gap:28px;align-items:center}
      .brand{width:150px;flex:none;font-size:15px;line-height:1.55;color:#8ccecf;border-right:1px solid #40545a}
      .copy{min-width:0;flex:1}h1{margin:0 0 8px;font-size:24px;line-height:1.15;letter-spacing:0}p{margin:0;font-size:18px;line-height:1.35;letter-spacing:0;color:#b8ccd3}
      </style></head><body><main><div class="brand">LoomAI Labs<br>Build with AI</div><div class="copy"><h1></h1><p></p></div></main></body></html>`)
    await page.locator('h1').evaluate((node, text) => { node.textContent = text }, segment.title)
    await page.locator('p').evaluate((node, text) => { node.textContent = text }, segment.detail)
    assert.ok(await page.locator('.copy').evaluate(node => node.scrollHeight <= 88 && node.scrollWidth <= node.clientWidth))
    await page.screenshot({ path: caption })
    const videoFilter = `[0:v]setpts=(PTS-STARTPTS)/${speed},fps=25,tpad=stop_mode=clone:stop_duration=${freeze.toFixed(3)},pad=1600:1120:0:0:color=0x111c22[ui];[ui][1:v]overlay=0:1000[v];[2:a]apad,atrim=duration=${seconds.toFixed(3)},asetpts=PTS-STARTPTS[a]`
    run('ffmpeg', ['-v', 'error', '-y', '-ss', String(segment.start), '-t', String(sourceDuration), '-i', take.video,
      '-loop', '1', '-i', caption, '-i', audio, '-filter_complex', videoFilter,
      '-map', '[v]', '-map', '[a]', '-t', seconds.toFixed(3), '-c:v', 'libx264', '-threads', '2',
      '-preset', 'fast', '-crf', '20', '-pix_fmt', 'yuv420p', '-c:a', 'aac', '-b:a', '128k', '-ar', '48000',
      '-movflags', '+faststart', part])
    chapters.push({ seconds: Number(offset.toFixed(2)), title: segment.title, detail: segment.detail,
      narration: segment.narration, rawStart: segment.start, rawEnd: segment.end, speed,
      finalFrameHoldSeconds: Number(freeze.toFixed(3)) })
    parts.push(part); offset += duration(part)
    console.log(`Rendered ${index + 1}/${plan.segments.length}: ${segment.title}`)
  }
} finally { await context.close(); await browser.close() }
const list = resolve(work, 'parts.ffconcat')
writeFileSync(list, 'ffconcat version 1.0\n' + parts.map(file => `file '${file.replaceAll("'", "'\\''")}'`).join('\n') + '\n')
run('ffmpeg', ['-v', 'error', '-y', '-f', 'concat', '-safe', '0', '-i', list, '-c', 'copy', '-movflags', '+faststart', output])
const metadata = { recordedAt: take.startedAt, renderedAt: new Date().toISOString(), output,
  durationSeconds: duration(output), format: 'Actual hosted Console recording, edited pauses, synthetic Daniel narration',
  scope: plan.scope ?? take.scope ?? 'Fresh draft only. Real model responses, visible validation failure and explicit correction. No publication, provisioning or business API execution.',
  chapters }
writeFileSync(output.replace(/\.mp4$/i, '.chapters.json'), JSON.stringify(metadata, null, 2) + '\n')
run('ffmpeg', ['-v', 'error', '-y', '-ss', String(Math.min(18, metadata.durationSeconds / 2)), '-i', output,
  '-frames:v', '1', '-q:v', '2', output.replace(/\.mp4$/i, '.jpg')])
console.log(JSON.stringify({ output, durationSeconds: metadata.durationSeconds, chapters: chapters.length }))
