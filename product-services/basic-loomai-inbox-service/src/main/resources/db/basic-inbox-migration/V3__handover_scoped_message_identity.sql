ALTER TABLE inbox_message
  ADD COLUMN source_message_id VARCHAR(180);

UPDATE inbox_message
   SET source_message_id = message_id
 WHERE source_message_id IS NULL;

ALTER TABLE inbox_message
  ALTER COLUMN source_message_id SET NOT NULL;

CREATE UNIQUE INDEX idx_inbox_message_handover_source
  ON inbox_message (handover_id, source_message_id);
