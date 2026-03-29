import { ScreenPlaceholder } from '../components/ScreenPlaceholder'

export function DiagnosticsPage() {
  return (
    <ScreenPlaceholder
      eyebrow="Diagnostics"
      title="Operational visibility"
      description="Diagnostics will aggregate deployment metadata, verification failures, config source locations, and service links so the platform remains supportable as a consultancy product."
      bullets={[
        'Show active config version and artifact hashes',
        'Link to Railway services and deployment logs',
        'Expose last verification and rollout errors',
        'Prepare support-ready deployment evidence bundles',
      ]}
    />
  )
}

