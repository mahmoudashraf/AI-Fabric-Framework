# Configure Your Smart Brain With AI

Use **Build with AI** inside an existing configuration page. The assistant works
on that selected Smart Brain, private Data plugin or Engage app. It does not
create another runtime for your conversation.

## A Typical Setup

1. Open your Smart Brain's **AI capabilities** page and choose **Build with AI**.
2. Add an OpenAPI/Swagger JSON or YAML file, documentation, a request/response
   example or a text PDF. Public HTTPS documentation URLs are available when
   the environment enables them. Imported references are never executed.
3. Review the sanitized source and select the relevant API operations.
4. Describe the outcome, for example: "Let customers check an order and request
   a callback after confirmation. Keep the other actions."
5. Answer any questions about missing API details or supported connection
   references. Store credentials in the normal secure connection form, not in
   the assistant.
6. The assistant saves requested changes to your editable draft. Review its
   saved diff and the actual action form, then refine the result manually.
7. Check the configuration and use the existing explicit publication/update
   workflow when ready. An assistant save is not a successful API test or a
   live deployment update.

You can configure one action or several related actions. Existing unrelated
actions, routes and unknown supported fields are retained. A READ action does
not need a knowledge collection merely because it retrieves business data.

## Other Configuration Tasks

- **Connections:** prepare supported endpoint mappings and reference existing
  secure bindings. The assistant does not call your business API.
- **My Data plugins:** translate documented HTTP JSON records into the existing
  mapping, pagination, ownership and synchronization contracts, or prepare a
  bound S3 document definition. Creating a reusable version and installing it
  remain separate actions.
- **Knowledge:** prepare source/search configuration. Discovery, approval,
  indexing and source removal remain explicit.
- **AI responses:** select the supported tone, answer breadth, length and next
  steps profiles, or prepare custom instructions.
- **Models:** prepare supported provider settings. This does not allocate
  funding, provision infrastructure or start reindexing.
- **Engage:** prepare branding, starters, tools and other supported presentation
  settings, then inspect the existing preview. An active app stays unchanged;
  the assistant offers a preview until you explicitly use its edit lifecycle.
- **Human support and specialists:** prepare supported configuration using
  installed authority. Agent assignments, review decisions and new worker code
  are outside the assistant's authority.
- **Kit setup:** get guidance and move to the relevant existing resource editor.
  A Kit guide does not silently install products or change live assignments.

## Review, Cancel And Undo

Normal configuration requests authorize draft saves, including replacement of
manual values in the requested scope. Manual editing pauses while AI works and
resumes from the saved result. Use **Preview only** or **Explain only** when you
do not want a draft save. Cancel suppresses late results; a provider request
already sent may still consume usage. Undo restores the selected saved draft
change, not a published release.

Failures and missing information stay visible. The assistant does not substitute
canned configuration, silently retry generation or claim tests passed.

## Privacy And Limits

Your request, selected sanitized documents and scoped configuration are sent to
LoomAI's configured model provider. Share only authorized material. Known
credential fields are redacted, but this is not a guarantee that arbitrary prose
contains no confidential information. Review sources before submitting.

Sources and conversation state are encrypted in Platform's existing database
and expire after seven days of inactivity. A session supports ten documents,
2 MiB per import, 200,000 source characters and thirty turns. Select fewer
operations if a request exceeds the bounded model context. Scanned PDFs need
text extraction before upload; OCR is not provided.

Remove a source or delete the session to remove its temporary authoring state.
Removing a source clears the assistant conversation; legitimate saved draft
configuration and audit records remain. Explicitly reused documents are copies
in the selected session and follow that session's lifecycle.

Manual forms and Advanced JSON remain available if the assistant is unavailable.
