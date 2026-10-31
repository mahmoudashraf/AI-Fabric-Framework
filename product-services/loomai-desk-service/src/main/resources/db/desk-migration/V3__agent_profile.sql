ALTER TABLE desk_operator ADD COLUMN profile_name_customized BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE desk_operator ADD COLUMN avatar_bytes BYTEA;
ALTER TABLE desk_operator ADD COLUMN avatar_revision VARCHAR(64);
UPDATE desk_operator SET profile_name_customized=TRUE WHERE registered=TRUE;
