export function formatDateTime(value: string | null | undefined) {
  if (!value) {
    return 'Not recorded'
  }
  return new Intl.DateTimeFormat(undefined, {
    year: 'numeric',
    month: 'short',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  }).format(new Date(value))
}

export function formatDate(value: string | null | undefined) {
  if (!value) {
    return 'No due date'
  }
  return new Intl.DateTimeFormat(undefined, {
    year: 'numeric',
    month: 'short',
    day: '2-digit',
  }).format(new Date(value))
}

export function titleize(value: string | null | undefined) {
  if (!value) {
    return 'Unknown'
  }
  return value
    .replace(/[_-]+/g, ' ')
    .toLowerCase()
    .replace(/\b\w/g, (letter) => letter.toUpperCase())
}

export function firstName(value: string | null | undefined) {
  if (!value) {
    return 'partner'
  }
  return value.trim().split(/\s+/)[0] ?? 'partner'
}
