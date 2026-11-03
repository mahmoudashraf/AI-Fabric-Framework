export type CatalogueVariant = 'generic' | 'dealership'
export type CatalogueFormat = 'website' | 'workspace' | 'standalone'

export const catalogueFormats = [
  { id: 'website', label: 'Website AI chat' },
  { id: 'workspace', label: 'Max AI workspace' },
  { id: 'standalone', label: 'Standalone AI space' },
] as const

export const catalogueExperiences = {
  generic: {
    title: 'AI Customer Experience',
    badge: 'Adapts to your business',
    description: 'Your products. Your services. Your knowledge.',
    assistant: 'Oak & Co assistant',
    business: 'Oak & Co',
    address: 'chat.oakandco.example',
    detailUrl: '/experiences/ai-customer-experience',
    question: 'Help me choose a chair for working from home.',
    answer: 'Here are two options to explore.',
    prompt: 'Ask about these options...',
    websiteHeading: 'Furniture for everyday living',
    products: [
      { name: 'Forma Chair', image: '/assets/experiences/example-forma-chair.webp', alt: 'Illustrative upholstered Forma office chair', type: 'Adjustable office chair', detail: 'Adjustable height, soft upholstery and a supportive back.', price: 'GBP 249' },
      { name: 'Studio Chair', image: '/assets/experiences/example-studio-chair.webp', alt: 'Illustrative wooden Studio chair', type: 'Wooden studio chair', detail: 'A compact wooden frame with an upholstered seat.', price: 'GBP 189' },
    ],
    nextStep: 'Delivery',
    toolCopy: 'Delivery options come from your approved information.',
    handover: 'Your team can join',
    handoverCopy: 'A customer asks about fabric samples. Your team can continue with the conversation context.',
  },
  dealership: {
    title: 'Dealership AI Experience',
    badge: 'Built for dealerships',
    description: 'Stock discovery. Buyer guidance. Dealer enquiries.',
    assistant: 'Northfield AI',
    business: 'Northfield Motors',
    address: 'ai.northfieldmotors.example',
    detailUrl: '/experiences/dealership-ai-experience',
    question: 'Help me compare cars under GBP 35,000.',
    answer: 'Here are two options from the simulator.',
    prompt: 'Ask about these cars...',
    websiteHeading: 'Find your next car',
    products: [
      { name: '2025 Aster E1', image: '/assets/demos/dealership/vehicle-01.webp', alt: 'Blue vehicle representing the fictional Aster E1', type: 'Electric SUV', detail: 'Explore the specification and compare your shortlist.', price: 'GBP 31,950' },
      { name: '2024 Northstar S4', image: '/assets/demos/dealership/vehicle-02.webp', alt: 'Vehicle representing the fictional Northstar S4', type: 'Hybrid estate', detail: 'Explore the specification and discuss your requirements.', price: 'GBP 28,450' },
    ],
    nextStep: 'Test drive',
    toolCopy: 'A test-drive enquiry can go to your dealership team.',
    handover: 'Your dealership team can join',
    handoverCopy: 'A buyer asks to discuss a test drive. Your team can continue with the vehicle and conversation context.',
  },
} as const
