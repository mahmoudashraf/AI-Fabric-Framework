import { isLosslessNumber, LosslessNumber, parse, stringify } from 'lossless-json'

// Keep configuration numbers exact without changing ordinary revision counters.
export function parseExactJson(source: string): unknown {
  return parse(source, undefined, { parseNumber: value => {
    const numeric = Number(value)
    return /^-?(0|[1-9]\d*)$/.test(value) && Number.isSafeInteger(numeric) && !Object.is(numeric, -0)
      ? numeric : new LosslessNumber(value)
  } })
}

export function stringifyExactJson(value: unknown, space?: number): string {
  const result = stringify(value, undefined, space)
  if (result === undefined) throw new Error('A JSON value is required.')
  return result
}

export function parseExactObject(source: string, label = 'Configuration'): Record<string, unknown> {
  const value = parseExactJson(source)
  if (value === null || typeof value !== 'object' || Array.isArray(value) || isLosslessNumber(value)) {
    throw new Error(`${label} must be a JSON object.`)
  }
  return value as Record<string, unknown>
}

export function cloneExactJson<T>(value: T): T {
  return parseExactJson(stringifyExactJson(value)) as T
}

export function exactNumber(value: string): unknown {
  const parsed = parseExactJson(value)
  if (typeof parsed !== 'number' && !isLosslessNumber(parsed)) throw new Error('Enter a JSON number.')
  return parsed
}

export const isExactNumber = isLosslessNumber
