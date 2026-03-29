import { ScreenPlaceholder } from '../components/ScreenPlaceholder'

export function SecurityPage() {
  return (
    <ScreenPlaceholder
      eyebrow="Security"
      title="Authorization and runtime protection"
      description="Security settings will control admin auth, connector auth, authz mode, and other bounded platform-managed security knobs."
      bullets={[
        'Configure remote authz mode and upstream profile',
        'Manage admin protection and connector auth settings',
        'Avoid exposing arbitrary raw environment variables',
        'Keep runtime fail-closed with versioned configuration',
      ]}
    />
  )
}

