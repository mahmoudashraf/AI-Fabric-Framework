import { ScreenPlaceholder } from '../components/ScreenPlaceholder'

export function ProvidersPage() {
  return (
    <ScreenPlaceholder
      eyebrow="Providers"
      title="Provider profiles"
      description="Provider profiles abstract LLM, embedding, retrieval, and vector database settings into reusable deployment choices."
      bullets={[
        'Select LLM and embedding provider profiles',
        'Choose vector DB strategy per environment',
        'Validate dimension and capability compatibility',
        'Apply provider profiles through versioned releases',
      ]}
    />
  )
}

