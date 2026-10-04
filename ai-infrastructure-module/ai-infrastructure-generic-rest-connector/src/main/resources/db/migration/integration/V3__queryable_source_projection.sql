ALTER TABLE integration_source_record
    ADD COLUMN IF NOT EXISTS content_text TEXT NOT NULL DEFAULT '',
    ADD COLUMN IF NOT EXISTS entity_data JSONB NOT NULL DEFAULT '{}'::jsonb,
    ADD COLUMN IF NOT EXISTS metadata_data JSONB NOT NULL DEFAULT '{}'::jsonb;

CREATE INDEX IF NOT EXISTS idx_integration_source_record_entity_data
    ON integration_source_record USING GIN (entity_data jsonb_path_ops)
    WHERE active = TRUE;

-- Existing rows predate the safe structured projection. Force a fresh provider
-- reconciliation before projection-backed actions can report inventory facts.
UPDATE integration_sync_state AS state
   SET status = 'RECONCILIATION_REQUIRED',
       last_started_at = NULL,
       last_success_at = NULL,
       error_class = NULL,
       error_message = NULL,
       updated_at = CURRENT_TIMESTAMP
 WHERE EXISTS (
       SELECT 1
         FROM integration_source_record AS record
        WHERE record.source_id = state.source_id
          AND record.active = TRUE
   );
