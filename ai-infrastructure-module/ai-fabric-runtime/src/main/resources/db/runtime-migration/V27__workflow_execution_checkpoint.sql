ALTER TABLE ai_specialist_execution
    ADD COLUMN IF NOT EXISTS protected_workflow_checkpoint TEXT;
