# Check A Smart Brain's Connections

In **Smart Brain -> Updates**, choose **Check setup**. This checks the saved
draft's configuration and summarizes supported service connection checks in one
place. Open **Details** for a side panel with each service's result, affected capabilities and
time. **Retry failed checks** does not rerun successful services.

Saving an action connection, plugin installation or model/search configuration
also checks new or changed supported connections. Saving remains separate from
checking: a failed check does not undo your saved draft. Neither operation
publishes, deploys, indexes documents or executes a business action.
Configuration-assistant preparation and API-only draft saves do not initiate
network checks. Review those changes and run **Check setup** explicitly.

## Results

- **Verified:** the configured safe GET endpoint accepted this request at the
  recorded time. This is not proof that every API operation is permitted, that
  a model can perform inference, or that a document has been indexed.
- **Failed:** a credential, endpoint or HTTPS request could not be checked
  successfully. Details distinguish authorization rejection and HTTP failure.
- **Not tested:** no safe check is declared, the connection needs its existing
  runtime/binding workflow, or previous evidence is no longer current.

Checks are advisory; the existing publish/apply and runtime-verification rules
remain authoritative. Results expire after five minutes and are lost when the
Platform backend restarts. Endpoint and credential changes invalidate previous
evidence. Configuration-only edits do not require repeating an unchanged service
check. Results apply to the **saved draft**, not the running deployment or an
unsaved editor buffer.
Checks originate from the Platform backend; the runtime's network access is
still verified through the existing apply/runtime workflow.

## Safe API Checks

In an action's **HTTP routes & connector** settings, configure an optional
**Safe connection check path**, for example `/health`. An absolute action route
can declare its own path. The same option is available when installing an HTTP
plugin; Data plugin authors can also declare it in the reusable source profile.

Use a documented read-only endpoint that actually requires the credentials you
want to verify. A public health endpoint only proves reachability; a successful
response cannot prove that the server enforced authorization. The check reuses
the configured header and credential reference, not a second test-only key.
Actions sharing that connection share one result.

Supported checks use public HTTPS on port 443. They do not follow redirects,
perform token exchanges, infer a health route, supply user/session parameters,
or execute lead/order/update endpoints. Leave the path empty when no safe probe
exists; use the existing explicit runtime test instead. Internal-network,
target-bound and unsupported authentication configurations retain their normal
binding/apply verification rather than being reported as successful.

OpenAI connections can use authenticated model metadata without generating paid
answers. Supported Pinecone/Qdrant/Weaviate connections use read-only metadata
endpoints. Other providers and Milvus use their existing verification workflow.
Document storage continues through source preview/approval/indexing; this check
does not read or upload customer files. No source vectors are copied.
Do not broaden a least-privilege key solely to permit a metadata probe. If that
endpoint is outside its intended permissions, use the actual runtime verification.

## Security

Only a Brain editor can initiate checks; viewing results never initiates network
requests. The server rechecks ownership and delegation each time. Generic API
checks accept only this Brain's non-target-bound credential references, never
global or foreign keys. Shared provider credentials are sent only to their fixed
official vendor hosts. Target-specific credentials are not guessed from a draft.

The probe pins validated public DNS addresses, verifies TLS hostnames and bounds
requests. Response bodies, credential values and raw upstream exceptions are not
returned or retained. No AI inference, write operation or automatic retry occurs.
