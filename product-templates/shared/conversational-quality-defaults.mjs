import { readFileSync } from 'node:fs'

const bootstrap = new URL('../../Platfrom/backend/src/main/resources/bootstrap/', import.meta.url)
const read = path => JSON.parse(readFileSync(new URL(path, bootstrap), 'utf8'))

export function readConversationalQualityDefaults() {
  const policy = read('brain-quality-defaults.json')
  const prompts = read('prompts/default-prompt-config.json')
  return {
    ...policy,
    intentExtractionPrompt: `${prompts.intentExtractionPrompt}\n\n${policy.intentGuidance}`,
  }
}
