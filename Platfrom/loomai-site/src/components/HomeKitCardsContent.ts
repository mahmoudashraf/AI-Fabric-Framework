export type HomeKitKind = 'generic' | 'dealership' | 'support'

export const homeKitCards = [
  { kind: 'generic', code: 'ai-customer-experience', title: 'AI Customer Experience', scope: 'Adapts to your business',
    summary: 'Help customers discover, decide and act.', features: ['Discover', 'Compare', 'Take action'], preview: 'Preview workspace' },
  { kind: 'dealership', code: 'dealership-ai-experience', title: 'Dealership AI', scope: 'Built for dealerships',
    summary: 'Turn live stock into confident enquiries.', features: ['Live stock', 'Compare', 'Enquiries'], preview: 'Preview workspace' },
  { kind: 'support', code: 'ai-support-experience', title: 'AI Support', scope: 'Built for support teams',
    summary: 'Resolve questions. Keep people connected.', features: ['Answers', 'Requests', 'Human handover'], preview: 'Preview support' },
] as const

export const kitDetailUrl = (code: string) => `https://console.loomai.pro/experiences/${code}`

export const homeKitLaunchOffers = {
  generic: { label: 'Customer experience launch offer', price: 'Tailored pricing', value: 'Integration included', benefits: ['Built around your business'] },
  dealership: { label: 'Dealership offer', price: 'Tailored pricing', value: 'Only pay when value starts', benefits: ['First 3 months free', 'Free integration'] },
  support: { label: 'Support launch offer', price: 'Tailored pricing', value: 'Setup included', benefits: ['Knowledge onboarding included'] },
} as const

export const kitOfferTerms = 'Your proposal confirms the agreed integration scope, included AI usage, storage and agent seats, plus any third-party charges. Work outside that scope is quoted separately.'
