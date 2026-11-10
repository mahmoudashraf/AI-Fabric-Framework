# Customer Keys And Secrets

Open **Smart Brains -> your Brain -> Keys & secrets**. The same page is available
to the customer owner and LoomAI staff with active customer delegation. AI models
and Knowledge -> Search settings also link here. Global Platform Security remains
staff administration, not a customer key directory.

## Add Or Replace A Key

1. Choose **Add key** and its purpose: a supported AI/vector provider, or an
   integration/security credential for your own connection.
2. Enter a short name, such as `ORDER_API_KEY`, and the value. Milvus username /
   password credentials require both fields.
3. **Save key** stores an integration credential. **Save & use key** also selects
   a provider credential through the existing provider-binding system.
4. Copy the reference into the connection field that asks for a secret reference.
   Do not paste the actual value into prompts, documentation or ordinary JSON.
5. Use **Replace** to rotate a value. Saved values are never displayed or returned
   to the browser. Apply the normal deployment update to refresh existing runtime
   environment credentials; saving a key does not restart or deploy anything.

For a technical routing field that already supports environment placeholders,
the existing `${REFERENCE_NAME}` syntax remains available. A normal `secretRef`
field takes the bare reference name. The server verifies Brain ownership when
materializing that reference, not merely when storing it.

## Ownership And Delegation

Keys are customer-owned through their Smart Brain. Other customers and other
Brains cannot read, replace or materialize them. Reusing the same friendly name
on another Brain creates a separate reference, not shared credentials.

Customer owners can grant or revoke LoomAI operator access through
**Account -> LoomAI collaboration**. Operators use the same Brain page; each request
rechecks the current delegation, including after revocation. A legacy deployment
assignment alone does not permit the new credential-management API. Platform
administrators retain their existing administrative authority.

Ordinary Brain-owned keys are shared by its staging and production configuration.
Target-specific document-storage and external-vector credentials still use their
existing target-binding workflows. This feature does not remove those boundaries.
LoomAI infrastructure credentials, signing keys and platform-managed credentials
cannot be changed through this customer page.

## Deletion And Provider Selection

Deletion requires explicit confirmation. A provider-selected key cannot be
deleted: rotate it or save a replacement provider key first. A custom integration
key can be deleted, but connections referencing it will fail when they next load
credentials. Deleting a reference does not revoke it at the external provider.

New provider bindings from this page use `REQUIRE_OVERRIDE`: a missing bound key must not
silently fall back to a LoomAI provider credential. Existing deployments without
a customer override keep their existing provider policy. Saving a provider key
does not change the selected model, provider or vector service.

## Storage And AI Access

This reuses the existing Platform backend database's `platform_secrets` store,
provider bindings, audit system, scoped materializer and hard-delete cleanup.
There is no second vault, customer database or framework service. This change
does not add application-level at-rest encryption to the existing secret store.

The Console receives names, purpose, presence, selection and update time, never
stored values. The Configuration Assistant receives authorized reference names,
not secret values, and has no key-management task. Audits record the operation
and reference without the credential. Credentials are sent only through the
authenticated write form and existing authorized backend materialization paths.
