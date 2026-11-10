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

## Optional Validation Corrections

**Hosted status (10 November 2026):** bounded validation feedback is deployed
and qualified on staging and production Console. Availability and the allowed
correction limit still come from the configured service.

When available, **Let AI fix validation errors** lets the assistant use the
actual configuration findings to correct its candidate within the same turn.
The initial setting comes from the service's declared default. Choose **Max
corrections** before sending: one or two, up to the service's allowed limit.
Two corrections means at most three attempts, including the original attempt.
Turn the toggle off for a single attempt with no automatic correction.

The controls pause while the assistant is busy. Corrections are disabled for
**Explain only** and Kit guidance. **Preview only** still does not save the draft;
allowing corrections does not grant permission to publish or change live state.
Your unsent message, document selections and correction/preview preferences stay
intact when the panel changes between desktop and mobile or is dismissed and
reopened. Changing the selected resource/task starts its own scoped inputs.
Each model attempt can consume usage. Provider, network and budget failures are
not automatically retried by this feedback policy.

While work runs, **Attempt N of M** shows the active attempt and the maximum
allowed for that turn; it does not promise that every attempt will be used.
Expand **Validation feedback** to review the turn's policy and each attempt's
status, timing, message, findings, candidate changes and runtime details. Failed
or unfinished attempts remain in that history; intermediate candidates are not
saved as separate draft versions.

The result explains why correction stopped: available checks passed, the
correction limit was reached, no further correction was proposed, a check was
unavailable, more information is needed, or a failure occurred. Cancellation
and the shared turn deadline can also stop work. An unavailable check is not a
passed check, and reaching the limit is not success. Review the retained
findings, answer any questions, continue in another explicit turn or edit
manually.

Only the final candidate may be saved through the existing editor save rules.
When enabled feedback stops to ask for essential information, its partial
proposal is retained without saving a draft change.
Those rules may permit an editable draft with remaining validation findings,
or reject a candidate that cannot be saved. **Draft saved. Needs attention.**
means there is still work to review; it does not mean the errors were fixed.
Inspect the actual validation outcome and saved diff before publishing.

## Review, Cancel And Undo

Normal configuration requests authorize draft saves, including replacement of
manual values in the requested scope. Manual editing pauses while AI works and
resumes from the saved result. Use **Preview only** or **Explain only** when you
do not want a draft save. Cancel suppresses late results; a provider request
already sent may still consume usage. Undo restores the selected saved draft
change, not a published release.

Failures and missing information stay visible. The assistant does not substitute
canned configuration, silently retry generation or claim tests passed.

**Saved is not the same as valid or live.** A draft may be saved with validation
findings so you can correct it in another turn or in the normal form. Review the
actual check result, not only the model's explanation. The saved diff and receipt
show what Platform really changed; the model's wording can be imperfect. A
successful configuration check does not prove that an external API is reachable.

For example, if an imported API did not document an ownership field, ask the
assistant to remove the invented mapping and keep the trusted ownership metadata.
Omitting a field from an object update preserves it; removing an existing field
requires an explicit removal. The assistant can prepare that change without
deleting and recreating the whole Data plugin.

The current bound-S3 document plugin supports TXT and JSON indexing. Uploading a
PDF or Markdown API guide into this assistant does not add those formats to your
customer's indexing pipeline. Existing storage binding, discovery, approval and
indexing controls remain the authority.

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

At the turn limit, finish in the normal editor or delete the temporary assistant
session and start another. Deleting that session does not undo saved draft work.

The first qualified model is OpenAI GPT-5.4-mini through LoomAI's shared private
authoring service. It does not run inside every customer deployment, and this
feature does not change your deployed chat model. Other providers are not silently
substituted when this service is unavailable. Its internal usage allowance is
finite; an unavailable allowance leaves the manual editors usable.

Remove a source or delete the session to remove its temporary authoring state.
Removing a source clears the assistant conversation; legitimate saved draft
configuration and audit records remain. Explicitly reused documents are copies
in the selected session and follow that session's lifecycle.

Manual forms and Advanced JSON remain available if the assistant is unavailable.

## Proposed Next UX (Not Implemented)

The configuration-assistant implementation plan, section 19, proposes page-aware
welcome guidance and document-first commands such as **Prepare actions**, with
an optional focus hint. This is a proposed next enhancement, not an implemented
workflow. Today, import and review your documents, select any relevant
operations, then type and send a request. Opening a page or importing a document
alone does not start generation or save configuration.
