-- Historical source identities remain unchanged and unverified; never infer their storage scope from current credentials.
ALTER TABLE loomai_document_source ADD COLUMN connector_scope_digest VARCHAR(64);
