export type KitPreviewKind = 'adaptive' | 'dealership' | 'support'
export type KitPreviewFormat = 'website' | 'max' | 'standalone'
export type KitPreviewContext = 'details' | 'compare' | 'next-step'

export const kitPreviewFormats: ReadonlyArray<{ id: KitPreviewFormat; label: string; description: string }> = [
  { id: 'website', label: 'Website AI chat', description: 'A helpful assistant on your existing website.' },
  { id: 'max', label: 'Max AI workspace', description: 'More room for answers, actions and the things your customers explore.' },
  { id: 'standalone', label: 'Standalone AI space', description: 'Your branded experience at its own web address.' },
]

// Fictional display fixtures only; never used as customer configuration or AI evidence.
export function kitPreviewContent(kind: Exclude<KitPreviewKind, 'support'>) {
  const dealership = kind === 'dealership'
  return {
    kind, dealership,
    badge: dealership ? 'Built for dealerships' : 'Adapts to your business',
    scope: dealership ? 'Stock discovery. Buyer guidance. Dealer enquiries.' : 'Your products. Your services. Your knowledge.',
    brand: dealership ? 'Northfield AI' : 'Oak & Co assistant',
    website: dealership ? 'Northfield Motors' : 'Oak & Co',
    headline: dealership ? 'Find your next chapter.' : 'Make space for better days.',
    query: dealership ? 'Help me compare cars under GBP 35,000.' : 'Help me choose a chair for working from home.',
    answer: dealership ? 'Here are two options from our demonstration stock.' : 'Here are two options to explore.',
    nextAction: dealership ? 'Test drive' : 'Delivery',
    disclosure: dealership ? 'Fictional demo stock' : 'Example experience',
    agentLabel: dealership ? 'Your dealership team can join' : 'Your team can join',
    agentMessage: dealership ? 'Hi! Which vehicle would you like to see in person?' : 'Hi! Is comfort or a smaller footprint more important to you?',
    products: dealership ? [
      { name: '2025 Aster E1', image: 'demo-vehicle-01.webp', alt: 'Blue car from the fictional dealership inventory', price: 'GBP 31,950',
        details: ['Electric', '4,850 miles', 'Automatic'], comparison: 'Electric SUV' },
      { name: '2024 Northstar S4', image: 'demo-vehicle-02.webp', alt: 'Second vehicle from the fictional dealership inventory', price: 'GBP 28,450',
        details: ['Hybrid', '12,600 miles', 'Automatic'], comparison: 'Hybrid estate' },
    ] : [
      { name: 'Forma Chair', image: 'example-forma-chair.webp', alt: 'Light grey ergonomic office chair in a bright home office', price: 'GBP 249',
        details: ['Adjustable support', 'Upholstered seat', 'Designed for everyday use'], comparison: 'Adjustable office chair' },
      { name: 'Studio Chair', image: 'example-studio-chair.webp', alt: 'Oak wooden desk chair with a light upholstered seat', price: 'GBP 189',
        details: ['Sculpted oak frame', 'Upholstered seat', 'A compact profile'], comparison: 'Wooden desk chair' },
    ],
  }
}

export type KitPreviewContent = ReturnType<typeof kitPreviewContent>
