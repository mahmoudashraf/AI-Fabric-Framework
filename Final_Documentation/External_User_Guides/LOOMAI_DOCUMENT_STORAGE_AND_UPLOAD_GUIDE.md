# Documents For Your AI Workspace

Implementation guide for 010.59. Hosted availability depends on your Platform
operator's storage-provider setup. Current live qualification belongs in the
dated execution record, not this guide.

## Choose Storage

Open your Kit's **Document storage**, or the deployment's **Document Knowledge**.

- **Use LoomAI storage:** create a named connection, then select **Prepare storage**.
  Source documents stay in a private S3 bucket managed by LoomAI.
- **Connect my storage:** supply your own S3-compatible bucket and read-only
  connection credentials using the existing source-binding form. Console does
  not assume that a customer-supplied reader can upload files.

You can reuse one owned hosted connection across compatible deployments. Its
bucket is shared by customer staging and production; creating or retiring a
target does not require a separate bucket.

## Upload And Test On Staging

1. Select your hosted connection and a purpose folder, for example `public/`.
   Folders are relative to the connection's displayed root, normally `knowledge/`.
2. Choose the staging target and **Bind storage**. The deployment receives a
   separate read-only identity for that bucket and folder, not the provisioning key.
3. Upload a UTF-8 `.txt` or `.json` file, up to 1 MiB. Uploads can happen before
   the first runtime starts. Upload success means **stored**, not **indexed**.
4. Publish/apply the deployment's current document-capable configuration. A new
   source binding or plugin revision requires a normal release; it does not
   silently change the active runtime.
5. In Document Knowledge, select the running target/release and the uploaded
   files. Preview the content. Explicitly approve public answers only for files
   that are appropriate for anonymous visitors.
6. Choose **Index selected files**. If work is pending, use reconciliation/status
   checks until the actual target receipt is Active. Check retrieval and real
   answers before proceeding.

JSON must be valid UTF-8 JSON and use the selected document plugin's supported
content fields. An upload button does not add PDF, Office or OCR support.

## Promote To Production

Bind the same connection to production before reviewing the promotion. Its
purpose folder must include the reviewed source files. It can differ from the
staging folder when those selected files remain inside both read scopes.

Review the document filenames shown with the production candidate. Apply the
normal production release, index the exact selected files in that production
runtime, reconcile them to Active, and run release verification. Activation
remains your explicit choice. Production does not copy staging vectors or
automatically consume every file uploaded during staging.

Completely disjoint stage/production folders contain different source objects;
they are not the same reviewed file selection. Use the appropriate document
setup/review rather than assuming promotion will copy files between folders.

## Privacy, Replacement And Retention

Source uploads remain in S3. Parsing, chunking and indexing use the existing
deployment-local document system. Website scripts, Engage and Desk do not receive
bucket provisioning or upload credentials.

New uploads use new object keys, so replacing a file does not silently overwrite
a production-used source. Index and verify its replacement before removing old
AI knowledge. **Remove from AI knowledge** deletes indexed evidence only, not the
S3 original. Shared source files are retained when a deployment is removed;
contact the integration team for storage retirement/export assistance.

Hosted buckets use private access, encryption, HTTPS and object versioning.
Stored object versions and storage requests have provider costs; storage is not
an unlimited free file manager. Operational upload/connection limits are shown
through explicit errors and can be reviewed with your integration team.

Failures remain visible. A failed or uncertain upload/index command must be
retried/read back with its original operation identity, not treated as success
or repeatedly submitted as new files.
