import type { KitPreviewKind } from './kitPreviewContent'

export const kitDetailContent = {
  dealership: {
    title: 'Dealership AI', scope: 'Industry kits / Dealership', image: 'kit-dealership-showroom.webp',
    headline: 'Turn your website into an AI-enabled buying workspace.',
    summary: 'Help buyers find the right car, compare their options and enquire with confidence.',
    transformation: 'Your website, upgraded. Not replaced.',
    introduction: 'Add a branded AI buying experience to the website you already have.',
    outcomesTitle: 'A better journey from interest to enquiry.',
    outcomes: [
      { title: 'Discover the right stock', description: 'Help buyers explore current stock around their needs and budget.' },
      { title: 'Understand the options', description: 'Bring vehicle facts, comparisons and your approved policies together.' },
      { title: 'Take the next step', description: 'Help buyers request a test drive or an enquiry through your connected systems.' },
    ],
    closing: 'Let buyers experience your dealership, not just browse it.',
    knowledge: 'Live stock and dealership knowledge',
    disclosure: 'The available dealership demo uses fictional inventory and a simulated AutoTrader integration.',
  },
  adaptive: {
    title: 'AI Customer Experience', scope: 'Business kits / Adapts to your business', image: 'kit-customer-showroom.webp',
    headline: 'Turn your website into an AI-enabled customer experience.',
    summary: 'Help customers explore your products, understand your services and take the next step with confidence.',
    transformation: 'Your website, upgraded. Your business, connected.',
    introduction: 'Bring useful answers and actions to the website you already have, with your brand and your information.',
    outcomesTitle: 'From a first question to a confident next step.',
    outcomes: [
      { title: 'Discover what fits', description: 'Help customers explore your products or services around what matters to them.' },
      { title: 'Understand the options', description: 'Compare the details and answer questions using your approved knowledge.' },
      { title: 'Move forward together', description: 'Connect useful next steps to your systems, with your team within reach.' },
    ],
    closing: 'Give customers a better way to do business with you.',
    knowledge: 'Your business systems and knowledge',
    disclosure: 'Illustrative products, prices and conversations. Your own branding, information and approved integrations shape the experience.',
  },
  support: {
    title: 'AI Support', scope: 'Business kits / Customer support', image: 'kit-support-team.webp',
    headline: 'Turn your support page into an AI-enabled help workspace.',
    summary: 'Help customers find answers, keep requests connected and reach your team without starting over.',
    transformation: 'One place to ask, resolve and follow up.',
    introduction: 'Add support to your website or offer a dedicated workspace, connected to your knowledge and your processes.',
    outcomesTitle: 'Helpful answers. Connected requests. Your people.',
    outcomes: [
      { title: 'Find a useful answer', description: 'Guide customers with your approved help articles, policies and troubleshooting knowledge.' },
      { title: 'Keep the request connected', description: 'Offer request tracking with the conversation and the details in one place.' },
      { title: 'Continue with your team', description: 'Let your assigned agents join when human support is enabled.' },
    ],
    closing: 'Make getting help feel like moving forward.',
    knowledge: 'Approved support knowledge and systems',
    disclosure: 'Synthetic support examples and fictional people. No request is submitted by the preview. Request tracking and human support are separate choices.',
  },
} as const satisfies Record<KitPreviewKind, {
  title: string; scope: string; image: string; headline: string; summary: string; transformation: string; introduction: string;
  outcomesTitle: string; outcomes: ReadonlyArray<{ title: string; description: string }>; closing: string; knowledge: string; disclosure: string
}>
