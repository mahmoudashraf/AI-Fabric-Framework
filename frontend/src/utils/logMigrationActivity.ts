// Simple no-op logger to satisfy legacy references during migration
export function logMigrationActivity(event: string, tag?: string, meta?: unknown) {
  if (process.env.NODE_ENV !== 'production') {
    // eslint-disable-next-line no-console
    console.debug('[migration]', tag ?? 'MIGRATION', event, meta ?? {});
  }
}

export default logMigrationActivity;

// Make available globally to satisfy legacy call sites without imports
// eslint-disable-next-line @typescript-eslint/no-explicit-any
(globalThis as any).logMigrationActivity = logMigrationActivity;

