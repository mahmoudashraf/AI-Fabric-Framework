import { ScreenPlaceholder } from '../components/ScreenPlaceholder'

export function KnowledgePage() {
  return (
    <ScreenPlaceholder
      eyebrow="Knowledge"
      title="Entities and vector spaces"
      description="Entity schemas and vector-space definitions are managed as versioned configuration. V1 applies these through redeploy, with future reindex signaling for schema-like changes."
      bullets={[
        'Configure searchable, embeddable, and metadata fields',
        'Enable or disable indexing per entity type',
        'Track schema changes that require reindex',
        'Compile entity config into runtime artifacts',
      ]}
    />
  )
}

