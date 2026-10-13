import { resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import { dirname } from 'node:path'

import {
  clickWithPointer,
  createRecording,
  hidePointer,
  installPresentationLayer,
  pointAt,
  showStage,
} from './demo-video-support.mjs'

const scriptDir = dirname(fileURLToPath(import.meta.url))
const repoRoot = resolve(scriptDir, '../../..')
const outputPath = resolve(
  process.argv[2]
    || resolve(repoRoot, 'Final_Documentation/Demo_Videos/AI_FABRIC_FRAMEWORK_CONVERSATION_HANDOVER_DEMO.mp4'),
)
const appUrl = process.env.AI_FABRIC_HANDOVER_DEMO_URL?.trim()
  || 'https://ai-fabric-conversation-handover.46.224.145.148.sslip.io'
const operatorCode = process.env.APP_DEMO_OPERATOR_ACCESS_CODE?.trim()

if (!operatorCode) {
  throw new Error('APP_DEMO_OPERATOR_ACCESS_CODE is required for the hosted framework demo.')
}

const recording = await createRecording(outputPath)
const { page, evidenceDir } = recording

try {
  await page.goto(appUrl, { waitUntil: 'networkidle' })
  await page.getByText('AI-selected handover, visible end to end.').waitFor({ timeout: 30_000 })
  await installPresentationLayer(page)

  await showStage(
    page,
    'AI Fabric framework-only proof',
    'This live real app uses AI Fabric orchestration, a governed @AIAction and the released conversation-control module. LoomAI services are not in this request path.',
    4200,
  )
  await page.screenshot({ path: resolve(evidenceDir, '01-framework-app-ready.png') })

  await showStage(
    page,
    '1. The host application authenticates its operator',
    'AI Fabric owns conversation authority and ordering. The small operator login and UI remain application-owned.',
  )
  const operatorInput = page.locator('#operator-code')
  await pointAt(page, operatorInput, 'Host-owned operator login')
  await operatorInput.fill(operatorCode)
  await clickWithPointer(page, page.getByRole('button', { name: 'Open inbox' }), 'Open the operator view')
  await page.locator('#operator-workspace:not([hidden])').waitFor({ timeout: 15_000 })

  await showStage(
    page,
    '2. AI initially owns the conversation',
    'The visitor sends a normal message through a real model provider. AI Fabric records the response under an AI-turn lease.',
  )
  const visitorMessage = page.locator('#visitor-message')
  await pointAt(page, visitorMessage, 'Visitor sends an AI message')
  await visitorMessage.fill('Can the AI help me before I ask for a person?')
  await clickWithPointer(page, page.locator('#visitor-form button[type="submit"]'), 'Send while AI is active')
  await page.locator('#visitor-messages .message[data-role="AI"]').first().waitFor({ timeout: 75_000 })
  await page.screenshot({ path: resolve(evidenceDir, '02-ai-active.png') })

  await showStage(
    page,
    '3. The visitor asks AI to transfer the conversation',
    'The model selects request_conversation_handover. The application action commits AI_ACTIVE to HUMAN_PENDING without phrase matching or a hidden fallback.',
  )
  await visitorMessage.fill('Please transfer this conversation to a human operator now.')
  await clickWithPointer(page, page.locator('#visitor-form button[type="submit"]'), 'Ask AI for a human operator')
  await page.locator('#visitor-state').getByText('HUMAN PENDING', { exact: true }).waitFor({ timeout: 75_000 })
  await page.getByText('AI action · request_conversation_handover', { exact: true }).waitFor({ timeout: 20_000 })
  await page.locator('#refresh-queue').click()
  const queueItem = page.locator('.queue-item').first()
  await queueItem.waitFor({ timeout: 15_000 })
  await page.screenshot({ path: resolve(evidenceDir, '03-human-pending.png') })

  await showStage(
    page,
    '4. A human operator claims and replies',
    'Claim is explicit. While HUMAN_ACTIVE, participant messages are ordered and AI orchestration remains paused.',
  )
  await clickWithPointer(page, queueItem, 'Open the waiting conversation')
  await clickWithPointer(page, page.locator('#claim'), 'Claim response authority')
  await page.locator('#operator-state').getByText('HUMAN ACTIVE', { exact: true }).waitFor({ timeout: 15_000 })
  const operatorMessage = page.locator('#operator-message')
  await operatorMessage.fill('Hello, I am the human operator. I now own replies in this conversation.')
  await clickWithPointer(page, page.locator('#operator-form button[type="submit"]'), 'Send a human reply')
  await page.locator('#visitor-messages').getByText('Hello, I am the human operator.', { exact: false }).waitFor({ timeout: 15_000 })
  await page.screenshot({ path: resolve(evidenceDir, '04-human-active.png') })

  await showStage(
    page,
    '5. Visitor messages stay in the human conversation',
    'The same durable conversation continues, but no AI message is created while the human owns response authority.',
  )
  await visitorMessage.fill('Thank you. Please return me to the AI when we are finished.')
  await clickWithPointer(page, page.locator('#visitor-form button[type="submit"]'), 'Reply to the human operator')
  await page.locator('#operator-messages').getByText('Please return me to the AI', { exact: false }).waitFor({ timeout: 15_000 })

  await showStage(
    page,
    '6. Return to AI is an explicit authorized transition',
    'The operator releases control. AI Fabric completes AI_RESUME_PENDING before allowing another AI turn.',
  )
  await clickWithPointer(page, page.locator('#return-ai'), 'Return response authority to AI')
  await page.locator('#visitor-state').getByText('AI ACTIVE', { exact: true }).waitFor({ timeout: 15_000 })
  await visitorMessage.fill('Are you active again?')
  await clickWithPointer(page, page.locator('#visitor-form button[type="submit"]'), 'Verify AI can answer again')
  await page.locator('#visitor-messages').getByText('Are you active again?', { exact: true }).waitFor({ timeout: 15_000 })
  await page.locator('#visitor-messages .message[data-role="AI"]').nth(1).waitFor({ timeout: 75_000 })
  await hidePointer(page)
  await page.screenshot({ path: resolve(evidenceDir, '05-ai-resumed.png') })

  await showStage(
    page,
    'Framework proof passed',
    'Real model-selected action · durable authority · human-only replies · AI-turn blocking · explicit return to AI',
    5200,
  )

  const result = await recording.finish()
  process.stdout.write(`${JSON.stringify(result)}\n`)
} catch (error) {
  await page.screenshot({ path: resolve(evidenceDir, 'FAILED.png'), fullPage: true }).catch(() => undefined)
  await recording.abort()
  throw error
}
