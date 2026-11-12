# 010.69 Public And Business-Authorized MCP Capabilities For Customer AI Experiences

- Status: implemented and deployed; the approved 0.8.50 READ-authentication/answer-contract follow-up is released and verified on both targets. Production's exact-original sign-in/read browser journey passed; staging model-selection failures remain explicit. See the latest section 13 checkpoint for cleanup and qualification limits, not an all-CI-green claim.
- Created: 2026-11-11. Revised: 2026-11-12.
- Initial Platform source inspected: `ceda2468ba108f08d72f94ed728d2f9bbb42e51a`.
- Latest qualified release: framework 0.8.50 / `aaaa5913b2e3879a18756e446e3d51929b535b02`; backend `b334440c04ce85ddd1e335caae89d1ca89a87e98`, runtime `b3197404d692265d815a94fcbb67e66dd6d1962d`. UI/gateway are unchanged. Earlier 0.8.49 proof remains historical evidence in section 13.
- Framework baseline: released `ai-fabric-framework-v0.8.47`, commit `965b2773ca4128e2b139de5bcac5f40c37fba1e6`.
- Related: [010.37 Assistant](010_37_PLATFORM_DEPLOYMENT_CONFIGURATION_ASSISTANT_IMPLEMENTATION_PLAN.md), [009.1 MCP architecture](009_1_MARKETPLACE_CONFIG_DRIVEN_MCP_CAPABILITY_ARCHITECTURE.md), [009.2 gateway extraction](009_2_MCP_EXECUTION_GATEWAY_EXTRACTION_PLAN.md), [010.64 Brain workspace](010_64_SMART_BRAIN_OUTCOME_LED_WORKSPACE_AND_NAVIGATION_REDESIGN_PLAN.md).
- Authority: owner authorized implementation and live verification on 2026-11-11. Personal-account connections remain deferred. Proven framework gaps still require the bounded review/release process in section 3.3; do not infer qualification from source changes alone.

## 1. Owner Decisions And Product Outcome

The owner's clarifications supersede the earlier assistant-first/static-token proposal and any earlier inclusion of personal-account connections:

1. **MCP import is mechanical.** Connect, authenticate, discover, select and import without an LLM, assistant subscription or inference budget.
2. **AI assistance is optional.** Offer explanations, description improvements, input-collection advice and workflow suggestions afterward.
3. **Exactly two connection models are in scope: public/no authentication and business-authorized.** Business credentials may use the supported static or OAuth methods. OAuth discovery, business-owner consent, refresh, reconnect and revocation remain in scope; they do not imply end-user personal-account connections.
4. Reuse ordinary LoomAI actions, current customer/staff permissions and draft -> review -> publish -> apply -> verify. No new plugin type or duplicate action editor.
5. Preserve READ without vectors, confirmed writes, optional human review and explicitly configured no-confirmation writes.
6. **Personal-account connections are deferred.** Do not implement per-visitor upstream grants, account-connect prompts, callback/start APIs or token storage. One business-authorized upstream identity per connection is sufficient for protected services; public services have no upstream identity or credential.
7. **Business-to-customer AI experiences are the primary product.** MCP supplies integrations for user-requested conversations and actions, not a new autonomous-agent builder. Existing Managed Tasks remain compatible; scheduling, self-assigned goals and unattended business workflows are not added by this plan.

Customer outcome:

> Add useful public capabilities or connect my business systems once, choose what my Smart Brain may do, and let customers get answers and complete authorized requests through my branded AI experience.

The configuration outcome is mechanical MCP connection and selected-tool import, without manually translating schemas or writing a prompt. The end-user outcome is a useful conversation through existing Engage/custom-app channels, with existing Desk handover when needed. App visitors should not see MCP setup or be asked to connect the business's integration account.

**Import all** means all selected tools within documented compatibility and size bounds. It does not mean execute all tools, grant anonymous access, publish automatically or expose tools added by the server later.

There is no intentionally incomplete authentication milestone called a first release. The phases below are implementation order. Broad support is not complete until required authentication, schema and isolation journeys are qualified.

### 1.1 Cross-Product Check: What Is Standard

Reviewed against official documentation on 2026-11-11, not inferred from competitor screenshots:

- **OpenAI Responses MCP:** configure a server URL and optional authorization, discover its tools, limit exposed names with `allowed_tools`, and configure tool-call approvals. The application handles upstream credentials; no AI authoring turn is required to connect a toolset. [Official guide](https://developers.openai.com/api/docs/guides/tools-connectors-mcp).
- **Claude Messages MCP connector:** separates server connection/authentication from the enabled toolset; supports all/selected tools and tool configuration. Its API consumers obtain and refresh OAuth tokens. Remote Streamable HTTP and SSE are documented; local stdio is not directly connected by this API feature. [Official guide](https://platform.claude.com/docs/en/agents-and-tools/mcp-connector).
- **n8n MCP Client Tool:** connects a server using stored bearer/header/OAuth credentials and exposes all, selected or all-except tools to an agent. Its MCP OAuth credential extends generic OAuth, including optional dynamic registration and manual client settings. This is the closest comparison to a customer-configured backend connection. [Tool node](https://docs.n8n.io/integrations/builtin/cluster-nodes/sub-nodes/n8n-nodes-langchain.toolmcp), [OAuth credential](https://docs.n8n.io/integrations/builtin/credentials/mcp).

The common architecture is **server connection + credentials where required + discovered tool schemas + enabled-tool policy -> model tool calls**. There is no single mandatory product UI or account-sharing model. Personal assistants may connect the current user's account; that market pattern is not included in LoomAI's current scope. Our two models are public/no-auth and an explicitly configured business integration identity.

**LoomAI-specific, not MCP requirements:** exposing selected tools as our governed action bindings and taking a reviewed snapshot through draft/publish/apply. These reuse the Platform lifecycle; customers must not maintain another handwritten copy of each tool. MCP itself specifies discovery and invocation and leaves UI design to the host. [MCP tools](https://modelcontextprotocol.io/specification/2025-11-25/server/tools).

### 1.2 Simplicity Corrections From This Review

- Connection setup and enabling tools are mechanical. AI enrichment is never on the critical path.
- A public connection has no upstream identity; a business connection has one configured integration identity, not a new kind of LoomAI user account. A Brain may connect multiple MCP servers. No per-visitor account system is included.
- Do not force customers to author or individually classify every tool before initial use. Offer a visible default of **Require confirmation for every tool** plus selected-tool overrides. When effect is unverified, use the existing conservative `READ_WRITE` mode with confirmation and no read-grounding eligibility; label it unverified, not proven write-only or read-only.
- Marking a tool as trusted READ, using it for grounding, disabling confirmation or permitting anonymous use remains an explicit owner policy. Optional AI suggestions and untrusted `readOnlyHint` never establish those permissions.
- A public connection skips credential/consent screens. For a business connection, a single **Connect for this Smart Brain** action with clear account/scope/use disclosure supplies connection consent. Do not add another account-sharing wizard.
- Tool schemas from the server are read-only source metadata. Edit local labels, enabled-tool/approval policy and supported parameter resolvers; use refresh to adopt schema changes rather than editing an independent schema copy.
- Use a maintained MCP client and generic OAuth implementation. No new authorization server, authentication service, per-connection process or generalized credential platform is required.

### 1.3 Product Fit And Supported Application Shapes

MCP is another integration route alongside existing HTTP actions and plugins. It is not a replacement for Smart Brain orchestration, Engage presentation, Desk support or the customer's business systems of record.

Existing API actions remain a valid primary integration path. Companies do not have to build an MCP wrapper around working APIs to use LoomAI. MCP adds a convenient route for existing compatible servers, including ready-made public information tools.

Primary application shapes, when the upstream system provides compatible tools and appropriate authority:

- **Dealership experience:** search stock, compare vehicles, collect an enquiry and request a test drive or lead creation.
- **Customer support:** answer from approved knowledge, inspect an authorized customer's subscription/order, submit a ticket or requested cancellation, and hand over to a person.
- **Commerce/service experience:** find products/services, check availability, explain approved policies and request a booking or quote.
- **Authenticated customer portal:** read the signed-in user's account information and perform explicitly permitted account changes.
- **Internal employee assistance:** retrieve approved business information and perform role-authorized actions. This is compatible, not a new employee-agent product requirement.

The common execution story is **user asks -> Brain resolves information/action -> existing authorization and configured confirmation/review -> business integration executes -> actual outcome returns to the conversation**. A bounded sequence of tool calls to complete that request is compatible; self-directed work outside the request is not an implementation goal.

Keep normal browsing fluid: once the owner verifies and configures a tool as READ, ordinary authorized lookups need not prompt for confirmation. The initial confirmation-for-all import default is not a permanent customer-experience requirement. Consequential writes use the owner's explicit confirmation/human-review/no-confirmation policy.

MCP connectivity does not itself supply missing business APIs, object-ownership rules, account mapping or industry UI renderers. Qualify those integration contracts rather than claiming every discovered tool is automatically suitable for anonymous customers. Do not expand this plan into background scheduling, autonomous goals, a new ticketing system or mandatory per-visitor upstream consent.

## 2. Supported Scope And Honest Boundaries

### 2.1 The Two Supported Connection Models

1. **Public, no authentication:** add a compatible remote server, discover its tools and enable selected capabilities without an upstream account, key or OAuth flow. Documentation/public-information lookup is the reference use case. The owner may allow anonymous app visitors to use verified public READ tools through existing action policy. A no-auth upstream does not automatically grant anonymous app access or establish that every tool is read-only.
2. **Business-authorized connection:** the company/integration team configures its endpoint and credential or completes business-owner OAuth consent once during setup. Runtime manages supported token refresh and uses that connection for the approved capabilities. Public business lookups may permit anonymous use; private operations reuse verified app identity and the company's own object-authorization contract. End users do not connect another MCP account or log in again solely to use the tool.

These are connection models, not new user roles or account types. Upstream authentication, READ/WRITE effect and app-user permissions remain separate settings. A provider that requires every visitor's personal grant is outside this plan, even if its protocol and tools are otherwise compatible.

### Required For Completion

- Remote HTTPS MCP over Streamable HTTP, including JSON/SSE, sessions, request/response correlation, pagination and bounded cancellation/progress handling. Also qualify the maintained client's remote HTTP+SSE adapter for services that still expose that endpoint style; do not implement a second hand-written protocol stack.
- Initial qualified protocol `2025-11-25`, matching the existing gateway. Verify the negotiated version; do not claim every protocol revision.
- Public/no-auth with no upstream credential/grant prerequisite. For business-authorized connections: static bearer, supported custom API-key headers, bounded multiple-header credentials where required, generic OAuth Authorization Code with PKCE for the configuring business owner, and explicitly configured OAuth client credentials where supported upstream.
- Business-connection OAuth protected-resource/authorization-server discovery, preregistered clients, advertised Client ID Metadata Documents and supported Dynamic Client Registration.
- Owned public or business-authorized connections used by the Brain's approved actions. Business-owner OAuth connects the integration account, not a separate account for each app visitor.
- User-requested customer-facing actions, including one qualified signed-in support journey with trusted app identity, business-object ownership and the selected confirmation policy. Existing sign-in can be reused only through a configured verified identity path; MCP import is not automatic SSO.
- Native tool input/output schemas preserved through discovery, planner and execution. Do not reject common nullable/union schemas just because the current parameter form is simplified.
- Text/structured results and safe bounded handling of other result content. Unrenderable content must be reported, not silently changed.
- Imported tools usable in ordinary conversations and eligible existing Managed Tasks under the same action policies.
- Direct UI/API import with zero inference, plus optional existing assistant integration.

### Deferred Or Outside This Plan

- **Personal-account connections are deferred:** no per-visitor OAuth grants, external-account linking, personal-token directory, app consent UI or start/callback APIs. Section 4.4 records the boundary, not a design to implement. A later separately approved plan is required. Business-owner OAuth consent and connection refresh/reconnect/revoke remain required now.
- Executing desktop `stdio` commands, installing arbitrary `npx`/Python packages or creating private-network tunnels. A customer may operate an authenticated compatible remote bridge.
- Sampling, roots/filesystem access, elicitation, MCP Apps rendering and durable/task-required MCP execution. Do not advertise unimplemented client capabilities. Task-optional tools may use normal synchronous calls; task-required tools remain visibly incompatible.
- Automatic resource/prompt indexing. MCP resources/prompts are separate from tool actions and retain their own source/approval requirements.
- Undeclared provider-specific authentication extensions, such as device grants or mTLS. Report a concrete unsupported auth requirement rather than pretending a static token is equivalent.

These boundaries deliberately exclude personal-account connections without postponing business-owner OAuth. No universal MCP-compliance claim is permitted.

## 3. Source-Validated Baseline And Gaps

This is code/existing-test inspection, not a new test run, hosted discovery or deployment proof. Relevant connector sources inspected in the release worktree have no differences from the stated framework baseline.

### 3.1 Platform And UI Reuse

- [ConfigurationAssistantController](../../../../../../../../Platfrom/backend/src/main/java/com/ai/fabric/platform/backend/assistant/ConfigurationAssistantController.java) and [ConfigurationAssistantService](../../../../../../../../Platfrom/backend/src/main/java/com/ai/fabric/platform/backend/assistant/ConfigurationAssistantService.java): scoped sessions, encrypted sources, selected operation IDs, idempotency, cancellation, saved receipts and a 140-second turn deadline. Reuse for optional AI, not as the import prerequisite.
- [AuthoringResourceService](../../../../../../../../Platfrom/backend/src/main/java/com/ai/fabric/platform/backend/assistant/AuthoringResourceService.java): deployment-editor checks and real draft validation/save/undo.
- [AuthoringChanges](../../../../../../../../Platfrom/backend/src/main/java/com/ai/fabric/platform/backend/assistant/AuthoringChanges.java): named merges default unknown collections to `id`. Add `mcpServers -> serverRef`.
- [AuthoringResourceContracts](../../../../../../../../Platfrom/backend/src/main/java/com/ai/fabric/platform/backend/assistant/AuthoringResourceContracts.java): current guidance is HTTP-oriented with 30 changes per turn. Add MCP guidance, not fabricated HTTP routes. Reuse configured bounded validation feedback.
- [CustomerDeploymentSecretService](../../../../../../../../Platfrom/backend/src/main/java/com/ai/fabric/platform/backend/secret/service/CustomerDeploymentSecretService.java): owned integration keys, rotation and delegated access. Keys use `CUSTOMER_<deployment-encoding>_...`, not the framework's current MCP-secret prefix.
- [PlatformSecretEntity](../../../../../../../../Platfrom/backend/src/main/java/com/ai/fabric/platform/backend/secret/entity/PlatformSecretEntity.java) and its inspected service store `secretValue` directly; application-level encryption is not established by those files. Reuse their ownership/reference controls, not an unverified claim of encrypted storage. New OAuth token/PKCE material must have an explicit encrypted-envelope implementation.
- [AssistantSources](../../../../../../../../Platfrom/ui/src/assistant/AssistantSources.tsx), [source labels](../../../../../../../../Platfrom/ui/src/assistant/scope.ts), [ActionDefinitionEditor](../../../../../../../../Platfrom/ui/src/actions/ActionDefinitionEditor.tsx) and [BrainCapabilitiesPage](../../../../../../../../Platfrom/ui/src/brain/BrainCapabilitiesPage.tsx): reusable views but missing broad MCP connect/consent UX. Replace the HTTP-route control with a useful MCP destination view for imported actions.
- [DeploymentSetupCheckService](../../../../../../../../Platfrom/backend/src/main/java/com/ai/fabric/platform/backend/deployment/setup/DeploymentSetupCheckService.java): grouped advisory service checks, but no MCP protocol check.
- Platform already uses `com.networknt:json-schema-validator:2.0.1`. Reuse its offline validation pattern, not a custom schema interpreter.

### 3.2 Gateway And Discovery

- [MarketplaceMcpDiscoveryService](../../../../../../../../Platfrom/backend/src/main/java/com/ai/fabric/platform/backend/marketplace/service/MarketplaceMcpDiscoveryService.java): admin-only discovery and ACTION-plugin drafts. Extract neutral helpers; customer import must not impersonate an admin or require Marketplace publication.
- Its builder sets every tool to `readOnly: true`, omits action parameters and selects `WARN_ONLY` drift. [The compiler](../../../../../../../../Platfrom/backend/src/main/java/com/ai/fabric/platform/backend/marketplace/service/DeploymentMarketplaceDraftCompilerService.java) turns that into actual `accessMode: READ`. Fix this shared path; do not rewrite existing published plugin versions.
- [McpGatewayExecutionService](../../../../../../../../product-services/mcp-execution-gateway-service/src/main/java/com/ai/fabric/product/mcp/gateway/service/McpGatewayExecutionService.java): existing initialize/list/call, schema hashes, parameter checks and result normalization. It supports static bearer, custom API-key headers and `OAUTH2_CLIENT_CREDENTIALS`. Its `CUSTOMER_OAUTH_PKCE` path expects a bound token/broker with Shopify customer-account assumptions, not generic consent/refresh.
- Discovery and action verification inspect one tools-list page. Use one bounded traversal for both.
- [McpGatewayContracts](../../../../../../../../product-services/mcp-execution-gateway-service/src/main/java/com/ai/fabric/product/mcp/gateway/model/McpGatewayContracts.java): retains schemas/hashes but drops annotations and execution requirements.
- [McpStreamableHttpClient](../../../../../../../../product-services/mcp-execution-gateway-service/src/main/java/com/ai/fabric/product/mcp/gateway/client/McpStreamableHttpClient.java): buffers response text, selects the first SSE data payload and does not correlate response IDs. Its provider-specific list arguments are not standard `params.cursor` pagination.
- [InternalApiKeyFilter](../../../../../../../../product-services/mcp-execution-gateway-service/src/main/java/com/ai/fabric/product/mcp/gateway/config/InternalApiKeyFilter.java): checks one shared internal key. That authenticates a service, not a particular customer/Brain's right to obtain an OAuth grant.

### 3.3 Framework Reuse And Compatibility Work

Released `ai-fabric-actions-connector` supports `mcp-tool` dispatch, root `mcpServers`, action access/confirmation and trusted user/session/auth-context fields.

Three constraints require explicit resolution:

1. `ConnectorActionParamDefinition` is not complete JSON Schema: string enums, signed-integer bounds and supported nested structures cannot faithfully represent every common MCP schema. Preserve native schemas through catalogue loading, planner metadata and execution.
2. `ActionConnectorExecutor` forwards secret values matching `^MCP_SECRET_[A-Z0-9_]+$`. Customer keys have a different namespace. Use owned connection handles and a scoped broker for new connections; do not duplicate credentials into global names or treat the regex as authority.
3. Connector retries default to three attempts, and writes may be retried when an internally generated idempotency key exists. Arbitrary MCP servers do not necessarily deduplicate such writes. Explicitly disable write replay unless the actual receiver contract supports it.

**Revised verdict:** the earlier no-framework-change expectation applied only to simple static-token imports. Broad scope may require a bounded framework release for native schema metadata, scoped connection/auth-required dispatch and retry policy. Prove each gap against released 0.8.47, propose only that change, and obtain review before implementation/release. No general orchestration redesign is required.

### 3.4 Existing App Identity Is A Foundation, Not Business Ownership Proof

- [RuntimeExternalIdentityBrokerService](../../../../../../../../ai-infrastructure-module/ai-fabric-runtime/src/main/java/com/ai/fabric/runtime/auth/RuntimeExternalIdentityBrokerService.java) validates configured external JWTs through issuer/JWKS/audience/lifetime checks and issues scoped runtime access. This supports reusing a verifiable app identity, not blindly accepting a browser's account ID or another site's cookie.
- [AIWorkspaceAuthenticationProfileRegistry](../../../../../../../../Platfrom/backend/src/main/java/com/ai/fabric/platform/backend/aiworkspace/service/AIWorkspaceAuthenticationProfileRegistry.java) exposes `none` and Supabase profiles; [AIWorkspaceExternalIdentityProjection](../../../../../../../../Platfrom/backend/src/main/java/com/ai/fabric/platform/backend/aiworkspace/service/AIWorkspaceExternalIdentityProjection.java) projects the configured Supabase identity settings. This is not proof of automatic sign-in reuse for every customer website or identity provider.
- A verified subject is not a billing-customer/subscription ownership check. Each private business action needs an existing customer-backend/MCP tool or a narrow integration adapter that maps that trusted subject and enforces object ownership. The generic signed-in cancellation path is not implemented or qualified by this source review.

Reuse an existing verified JWT path or trusted customer-backend ingress for the selected integration. Do not build a new general SSO/token-exchange product. If the actual identity-to-business-account mapping or receiver authorization is missing, name that integration gap before promising cancellation support.

## 4. Customer Journeys

Root: `/smart-brains/:brainId/capabilities`, retaining the existing customer/delegated-staff Brain shell.

### 4.1 Connect And Import Without AI

1. Select **Connect MCP** beside existing capability controls.
2. Enter name/HTTPS endpoint or select a connection. Offer **Public - no authentication** or **Business-authorized**. Automatic discovery may suggest an auth method; static or machine credentials remain explicit choices. These choices describe the upstream connection, not whether app visitors must sign in.
3. Public mode skips credentials and OAuth. Business mode uses an owned key/machine credential or displays provider/resource/scopes and **Connect for this Smart Brain** for business-owner OAuth. Use a popup with same-tab fallback, not an iframe. Return safely to the selected Brain. If a public connection is challenged, show that business authorization is required; do not silently select another credential.
4. Display **Public - no credentials** or the connected business identity/scopes and explain **Used by this Smart Brain's authorized actions**. Obtain the business's authorization for its integration account. Do not offer a personal-account mode or end-user connection workflow.
5. **Discover tools** performs only protocol metadata calls; no business-tool execution or model request.
6. Show exact tools, schemas, effect hints, compatibility findings and counts. Offer individual selection and **Select all compatible**, retaining visible excluded-tool reasons.
7. Show **Require confirmation for every tool** as the default, with optional selected-tool overrides for verified READ, anonymous access or no-confirmation use. Do not force a classification form for every tool. Hints alone cannot grant authority.
8. **Import to draft** mechanically saves ordinary actions and connection references through existing draft services. Show actual created/updated/unresolved counts and diff/undo.
9. Refine in the existing action editor, then explicitly review/publish/apply/verify through Updates.

Discovery/preview do not edit the Brain draft. Business-owner OAuth can persist a grant but is not action import or deployment. Public mode creates no OAuth attempt or grant. Model/provider availability is irrelevant to this core workflow.

**Import to draft** can be labelled **Add selected tools** in the normal UI. Internally it creates exact MCP-backed action bindings, not generated implementations. `Select all` means all currently discovered compatible tools in this reviewed snapshot, not automatic permission for future tools.

### 4.2 Optional Help Configure

Select **Help configure** on a discovered/imported action to attach its sanitized catalogue to the existing assistant. Offer explanation, descriptions, input-collection and specialist-workflow guidance.

The assistant may save requested draft changes under 010.37, with bounded validation feedback. It cannot replace endpoint/tool identity, grant scopes, choose another user's token or execute business tools. No detailed prompt is required beyond page/task context.

### 4.3 Signed-In Customer: Cancel A Subscription

Here **business owner** means the Console customer configuring the Brain; **end user** means that business's signed-in customer requesting support. They are not the same account or upstream credential.

Default journey when the business authorizes a service connection to manage its own customers' subscriptions:

1. The business connects its subscription/support MCP service during setup and enables only the permitted lookup/cancellation tools. It configures authenticated-only access, trusted actor/account resolution and its write-confirmation policy.
2. The end user is already signed into the business's app and asks **Cancel my subscription**. The app supplies identity through the configured verified token path or its authenticated backend. A browser login cookie, typed email or arbitrary query context alone is insufficient.
3. Runtime establishes the trusted app subject and checks action access. The customer backend/MCP integration maps that subject to the business customer and verifies the selected subscription belongs to that customer. Do not let the LLM invent or replace this authority.
4. Retrieve any actual cancellation terms/status needed for the request. If confirmation is configured, present the proposed cancellation and verified consequences, then obtain the existing explicit action decision. This is business-action confirmation, not a second OAuth account connection. Explicit no-confirmation writes remain supported where the owner has chosen them.
5. The gateway calls the approved MCP tool using the business's existing integration credential and the integration's trusted actor/object binding. Keep provider tokens out of the browser and model. The receiver must enforce object authorization before mutation, not merely trust an arbitrary customer ID in arguments.
6. Show the actual receipt/status or a visible failure/unknown outcome. The upstream system remains the subscription system of record. Offer existing human handover where configured; never report success from model prose alone.

**No extra end-user MCP login/consent is introduced in this business-connection model.** There are three different checks: existing app sign-in identifies the user, the business's MCP credential authorizes the integration, and the configured action/ownership policy authorizes this cancellation.

An ordinary app sign-in/verification prompt may be needed when its session is unavailable/expired or the business requires stronger authentication. A provider requiring a grant from that individual user is outside current scope under section 4.4; do not build its personal OAuth journey or silently bypass it with the business owner's token. A business grant needing renewal is an owner reconnect issue, not a request for every visitor to connect.

Existing app sessions are not automatically visible to an embedded widget, iframe or standalone Engage origin. Configure and qualify the identity handoff. Do not add a login prompt solely because the action happens to use MCP, but do not claim sign-in reuse before that handoff works.

### 4.4 Deferred: Personal-Account Connections

Personal-account connections, such as each visitor connecting their own Google Drive or calendar, are not implemented under this plan. They are neither an optional implementation phase nor a completion gate.

Do not add personal OAuth grants, connection prompts, redirect/start/callback APIs, user-token persistence or a personal-account selector. If a server cannot authorize the intended business-service use without per-user grants, report it as unsupported for that binding. Any future personal-account support requires a separate approved design; existing unrelated Shopify integrations remain regression-protected, not generalized here.

### 4.5 Reconnect And Refresh

Show public/no-auth status or business identity, scopes, auth health and **Reconnect / Disconnect** in one connection panel, never tokens. Public connections have no OAuth refresh/reconnect action; metadata refresh and disabling the binding remain available.

Refreshing an existing grant does not require republishing. Changing endpoint/resource, usage mode or effective authority is a reviewed configuration change. Disconnect invalidates the grant immediately without deleting action definitions.

**Refresh tools** shows added/removed/schema-changed tools. It never silently changes the draft, installs additions or updates a live release.

## 5. Authentication And Authorization

### 5.1 Three Independent Boundaries

- **Console configuration:** existing customer/delegated-staff permissions govern keys, discovery, connections and draft changes.
- **LoomAI to MCP:** public mode sends no upstream credential; business mode uses a key/OAuth grant authorizing the configured resource/account/scope. Both retain authenticated internal caller and owned-connection checks.
- **App/service user to Brain and business object:** existing action authorization and anonymous policy govern public access; private operations require verified identity and actual object ownership. Confirmation and human review apply as configured. Sign-in or confirmation alone does not authorize another user's subscription.

Passing one boundary bypasses neither of the others. Supabase sign-in alone does not authorize a third-party account.

### 5.2 Business-Connection OAuth Lifecycle

Use a maintained MCP/OAuth client compatible with the gateway/runtime stack. The consenting actor is the configuring business owner or an authorized integration administrator, not each visitor. Public connections skip this lifecycle entirely. Qualify the client behaviour; do not grow the hand-written transport into a bespoke authorization implementation.

- Discover the protected resource and its authorization server from the standard challenge/metadata locations. Distinguish resource, issuer and MCP endpoint identities. [MCP authorization](https://modelcontextprotocol.io/specification/2025-11-25/basic/authorization), [RFC 9728](https://datatracker.ietf.org/doc/html/rfc9728).
- Support preregistered clients, advertised Client ID Metadata Documents and supported Dynamic Client Registration. No DCR support is not itself fatal: show the required integrator registration settings.
- Use Authorization Code with PKCE S256, short-lived single-use state, exact registered redirects and actor-bound return locations. Cancelled/expired attempts grant nothing. [PKCE](https://datatracker.ietf.org/doc/html/rfc7636).
- Request the intended resource and bind tokens to it. Do not send credentials to a different audience or along an unapproved redirect. [Resource indicators](https://datatracker.ietf.org/doc/html/rfc8707).
- Display requested scopes and obtain explicit consent for scope step-up. Never expand privileges automatically after a 403 or switch issuer/account silently.
- Refresh only an existing grant. Persist rotated tokens atomically and coordinate concurrent refresh for that grant. Invalid refresh credentials require reconnection, not a staff/owner/shared-account fallback. Revoke upstream where supported and always invalidate locally on disconnect. [OAuth security practices](https://datatracker.ietf.org/doc/html/rfc9700).

Business-owner browser OAuth remains required for compatible business integrations. It is distinct from deferred personal-account OAuth. Client credentials remain a separate explicit machine-account method, not fallback when business-owner consent fails.

### Connection Form Inputs

- Common: name, endpoint, **Public - no authentication / Business-authorized** and, only for business mode, auth method. Prefer the qualified client's automatic transport negotiation, with actual transport displayed and an Advanced explicit override. Explain this Brain's connection. No personal-account selector or second environment picker.
- Public: no key, client ID, token, scope-consent or credential-test fields. Show actual metadata health and an explicit indication that app access and tool-effect policies are configured separately.
- Static bearer/API key: owned saved key; approved header name for API-key mode. Where multiple headers are needed, store bounded name/secret-reference pairs in the same connection; block protocol/routing-header overrides. New values use secure forms, not chat.
- Business OAuth automatic: discovered provider/resource, selected scopes, status and **Connect for this Smart Brain**. Show required business-owner consent before leaving Console; no second account-sharing ceremony or visitor account linking.
- OAuth Advanced: preregistered client ID, optional saved client-secret reference, exact Console-provided callback URI and verified discovery/registration information. Missing DCR results in actionable registration instructions.
- Machine OAuth: authorized token endpoint, client ID, saved client secret and supported scope/resource settings, explicitly labelled as a service identity.
- Readiness: public metadata health or actual connected business identity/scope, target binding and applicable expiry/refresh/reconnect health. Never require public sources to have a grant, display tokens or imply that a successful login validated every tool.

### 5.3 Grant Scope

- **Public connection:** no upstream grant or credential. Connection ownership, target binding, approved tools and app-user policy still apply.
- **Business connection:** the company selects a machine/service account or uses business-owner OAuth to connect its integration identity for this Brain. The Connect action clearly discloses runtime use. Do not silently promote a separate staff-only test/discovery grant, but do not require a second sharing workflow after the business already authorized the connection.
- Current scope executes the approved applied tool through one of those two models. The app user's ordinary Brain/action and object permissions remain enforced. Personal-account grants and their lifecycle are deferred, not represented by a business owner's credential.
- Staff delegation permits configuration, not automatic impersonation of a customer's upstream identity. Recheck delegation at sensitive operations.
- Stage/production have explicit connection bindings. Promotion copies no tokens/grants. Deliberate reuse of an authorized shared resource can be allowed, but discovery on one endpoint/account is not proof of another.

### 5.4 Runtime, Gateway And Token Broker

Retain one managed gateway per environment, but add target-scoped runtime execution credentials. Derive customer/Brain/target from that authenticated binding, not arbitrary trace fields or a supplied connection ID.

Implementation requirement: the runtime caller must be tied to registered scope. Reuse existing target-secret/provisioning primitives and available credential verification before adding anything new. A small opaque target key plus registered-scope resolution is one option, not a mandated standalone introspection system or new network lookup on every call. Control-plane discovery uses a distinct service identity; runtime credentials cannot call admin/grant-management endpoints.

New imported bindings pass a connection handle and the runtime-established caller context; public actions may have an allowed anonymous caller, while private business actions require the configured verified subject. Check applied binding and caller scope for both models. Public upstream requests use no credential or token resolver; business requests resolve only that connection's credential after ownership checks. Refresh tokens stay out of deployment environments. Do not forward Console JWTs as MCP access tokens.

No-auth upstream access does not mean an unauthenticated internal gateway or cross-customer connection access. The target-bound internal caller checks remain in place.

If valid authority cannot be established, fail that action visibly. Unrelated Brain actions need not be disabled.

### 5.5 End-User Identity And Business-Object Authorization

Keep this a per-integration execution contract, not a new identity service or account database:

- Accept identity only from the configured verified runtime ingress. Namespace subjects by the trusted issuer/tenant; identical subject strings in different identity systems are not the same customer.
- Resolve the business-account relationship server-side using the customer's existing account mapping. A selected subscription/order ID may be an input, but it must pass ownership/role checks; an email or ID supplied in chat is not proof.
- Reuse supported trusted parameter resolution/context projection. Transport only the actor/object data required by the actual receiver contract over an authenticated path, with the receiver verifying its trusted origin and ownership. MCP has no generic automatic `run as this app user` guarantee. If a receiver ignores actor authority, do not expose its broad mutation tool to end users.
- Keep runtime/app authentication separate from MCP transport authentication. Use credentials issued for the MCP resource; do not forward the website's login cookie or arbitrary app/Console JWT as its bearer token. [MCP audience and token requirements](https://modelcontextprotocol.io/specification/2025-11-25/basic/authorization).
- Recheck current authority before dispatch under existing contracts; required confirmation, stronger identity verification and OAuth provider consent are distinct. Do not introduce universal MFA, an extra login or a per-user grant store for an ordinary business-service connection.
- An integrator must qualify the chosen identity handoff and account/ownership check. Setup readiness should distinguish connection health from an unqualified private-action binding; successful OAuth/discovery alone is not proof that customer cancellation is safe.

## 6. Architecture And Storage

```text
Console Connect MCP -> owned connection in existing Platform database
  -> Public: no upstream credential, consent or grant
  -> Business: owned key / client credentials / business-owner OAuth
     -> encrypted grant when applicable + connection metadata
Console Discover -> gateway initialize + all tools/list pages
  -> exact schema catalogue -> mechanical draft import -> normal Updates
Optional Help configure -> sanitized catalogue -> current assistant -> draft edits
Engage / customer's app -> permitted anonymous context for public actions
  -> private actions: configured verified identity -> trusted runtime subject
     -> integration's business-account mapping + object-ownership check
Applied Brain / Managed Task -> action auth + confirmation/review
  -> scoped gateway credential + connection handle + runtime-established context
  -> Public: no upstream Authorization header or credential resolution
  -> Business: scoped credential resolver + required trusted actor/object binding
  -> MCP tools/call -> validated result / failure / auth-required
```

### 6.1 Persistent Records

The earlier no-new-schema assumption no longer applies to durable business-connection OAuth. **Use the existing Platform PostgreSQL database and existing secret ownership/reference services, not a new database service.** Add explicit encrypted storage for new OAuth material; do not assume the existing secret-value column already provides it. Public connections require only their owned metadata/bindings, not OAuth storage.

Keep persistence proportional to the supported flow:

- **MCP connection:** customer/Brain/server, canonical endpoint/resource, public/business connection model, auth method, target binding and status/revision; include business registration/scope metadata only where applicable. A public record has no credential reference, grant or token envelope. For business OAuth, store its one configured grant's encrypted token envelope, expiry and refresh revision here, or through a small owned child record if existing persistence patterns require it. No generic multi-subject grant directory.
- **Business OAuth attempt:** expiring single-use state, encrypted PKCE material, initiating authorized Console actor, validated return path, resource/scopes and consumed status. Public/key/client-credentials connections create no browser OAuth attempt. Reuse a suitable existing secure attempt store if available; otherwise add one small expiring record. Delete expired sensitive material.
- **Runtime credential binding:** reuse existing provisioning/secret binding metadata where it can establish customer/Brain/target scope and revocation. Add only the missing scope fields; no separate gateway-identity platform.

Personal-account persistence is deferred entirely: no subject-keyed upstream grant tables, role systems or user-connection UI. Verified app subjects remain ordinary execution context, not personal OAuth-grant records. The business credential resolver/broker is a small internal Platform helper, not a new service deployment.

Store bounded sanitized discovery snapshots under the same Platform boundary, with connection revision/hash and observation time. They must work without assistant sessions. Optional authoring sessions can attach an owned snapshot under their own expiry.

Published versions contain handles, selected tools, schemas and policy snapshots, not plaintext tokens. Auth grants remain live revocable records, separate from immutable release history. No per-connection container, customer business database or separate auth microservice.

Implement token/PKCE encryption with an authenticated-encryption primitive and a purpose-specific key supplied outside the database/source repository, following the existing authoring encryption pattern without reusing its key. Include key versioning and an explicit rotation procedure. Do not hand-roll encryption or silently migrate all unrelated Platform secrets as part of this feature.

### 6.2 Proposed APIs

These names are proposed, not existing endpoints:

- `POST /api/deployments/{brainId}/mcp-connections`: create owned public/no-auth metadata or a business connection using saved key refs/OAuth settings.
- `GET /api/deployments/{brainId}/mcp-connections`, `PATCH .../{connectionId}`: nonsecret summaries and revision-checked configuration.
- `POST .../{connectionId}/authorize`: create a business-owner OAuth attempt and validated browser authorization start; not an app-user/personal-account endpoint.
- `GET /api/mcp/oauth/callback`: validate/consume that business-owner attempt and bind the connection grant; redact codes from logs and browser history.
- `POST .../{connectionId}/discover`: bounded complete/incomplete snapshot.
- `POST .../{connectionId}/import`: selected snapshot/revision/tool IDs and explicit policies; return actual draft/diff/counts with no inference.
- `POST .../{connectionId}/disconnect`: invalidate the authorized grant/binding and attempt upstream revocation.
- `POST /api/configuration-assistant/sessions/{id}/sources/mcp`: attach owned selected metadata for optional help.
- Authenticated internal gateway/broker calls resolve registered runtime scope, approved connection and grants; never a public generic tool proxy.

Recheck actual customer/deployment/subject ownership on each operation. Reuse current CSRF/no-store/delegation controls; a shared controller prefix alone provides no ownership proof. Do not add new personal-account start/callback/status APIs or grant fields to existing app-auth responses under this plan.

## 7. Import, Schemas And Execution

### 7.1 Native Contract Preservation

- Parse names, schemas, annotations and execution requirements structurally. No semantic substring classification.
- Preserve exact case-sensitive tool names. Derive stable action names from formal connection/tool identity and report collisions rather than overwriting unrelated actions.
- Save normal `actionsConfig.actions[]` with `adapterType: mcp-tool`, server/tool/connection references, schema hash and access policy. Reuse `actionsConfig.mcpServers[]` with `serverRef` identity.
- Simple parameter forms are a UI projection; the observed upstream schema is authoritative and read-only. Keep it available to the planner, not only the gateway. Do not ask users to rebuild parameter schemas on import.
- Support JSON Schema 2020-12 and explicitly qualified draft-07 definitions: local references, nullable/union structures, numeric/string enums, fractional bounds, nested arrays/objects and additional-properties semantics.
- Use a proven offline validator, consistent with the validator family already present in Platform/framework. Qualify gateway dependency/Java compatibility; do not write another schema interpreter.
- Disable automatic network/file reference resolution. Unresolved/external refs or unqualified custom vocabularies are visible compatibility findings; an integrator can provide an authorized self-contained schema.
- Use normal forms when representable and a schema-validated JSON argument editor for complex inputs. Do not invent wildcard properties, drop constraints or hide the schema in prose.

Full preservation may require the bounded framework extension described in section 3.3. No unsupported feature is declared complete just because its JSON was retained.

### 7.2 Policy And AI

Tool annotations are hints. Use the visible confirmation-for-all default for initially unverified tools; map this conservative policy to existing `READ_WRITE`, confirmation enabled and grounding disabled. It is an owner-visible host execution policy, not an assertion that the tool definitely writes. This permits initial import without a model or mandatory per-tool classification. A verified READ classification or reduced approval policy is an optional explicit change.

New tools default to no anonymous access. Confirmed access remains the default, with an explicit supported no-confirmation choice. Reimport preserves owner-selected policies unless requested otherwise. Ordinary verified READ tools need no vector space.

Missing wording does not block a tool whose authority the owner explicitly configured. Optional AI may improve it, not grant permission. The model cannot choose another account, fabricate a trusted ID or execute tools to infer behaviour.

### 7.3 Runtime Results And Retry Safety

- Resolve the approved connection and validate exact arguments after trusted parameter resolution. Browser-provided account IDs are not authorization.
- Preserve optional output schema and validate structured results. Invalid output is a visible tool-contract failure, not an invented success.
- `isError: true` remains action failure even with HTTP 200. Keep auth, protocol and tool failures distinct.
- Preserve bounded supported content and safe media/resource references. Do not auto-fetch arbitrary links, render server HTML or disclose credentials in chat.
- Missing grant/expired consent/scope step-up becomes typed auth-required data, not a message-text matcher.
- Route business-connection auth failures to an owner reconnect/setup status, not a personal-account login link in visitor chat. An expired ordinary app session follows existing app authentication. Public tools need no upstream grant; neither public accessibility nor a server `readOnlyHint` alone authorizes READ/anonymous execution.
- Use the existing blocking schema-drift policy for new bindings; description-only changes are not schema changes. Explicit refresh/review controls newly exposed tools.
- Enforce no automatic write replay through framework connector, gateway, SDK and proxy. Only a verified upstream deduplication contract may allow retries; a generated key alone is insufficient.
- Refresh before dispatch is auth housekeeping. After an uncertain write timeout, report unknown outcome, not automatic reconnect/replay.

### 7.4 Bounded Catalogues And Optional AI

Proposed discovery bounds: 500 tools, 100 pages, 60 seconds and 8 MiB sanitized metadata, plus per-page/body limits. These are named finite settings, not an unlimited import promise.

Repeated cursors, duplicate identities and limit/page failures produce explicit incomplete status. For larger services offer explicit paged selection; never silently change credentials or present one page as all tools.

Mechanical import is independent of the assistant's 30-change/120,000-character limits. Save a selected bounded catalogue through the normal draft transaction. If an existing draft-size limit requires batches, expose actual saved/remaining counts and an explicit import receipt; no generic background-workflow engine.

Only optional AI enrichment uses bounded batches, current authoring deadlines and configured correction limits. Preserve usage/cancellation/receipts. A model failure does not undo a successful mechanical import.

## 8. Implementation Phases

### Phase 1: Public And Business-Authorized Connections

- [x] Add the small owned connection/business-OAuth-attempt records and necessary existing binding fields, with scoped APIs, encryption and audit. Public records need no credentials/grants. Do not add a personal-account grant directory or app connection lifecycle.
- [x] Implement public/no-auth discovery and execution without a credential dependency. Qualify business-owner OAuth metadata discovery, preregistration, advertised client metadata/DCR, PKCE callback, refresh/rotation, step-up, reconnect and revocation separately. Local provider-contract tests cover registration variants; hosted proof covers DCR/PKCE and token lifecycle, not a universal third-party-provider claim.
- [x] Qualify static bearer, approved API-key/multiple-header credentials and machine client credentials separately; no identity fallback. The hosted fixture does not require both headers; delivery/enforcement cases are covered by isolated transport tests.
- [x] Require explicit customer consent for runtime use of the selected connection; prevent staff/discovery credentials from being silently shared. Establish per-target runtime caller/resolver authorization through the existing binding mechanism. Hosted target enable/revoke and nine real-runtime authority/ownership checks passed.

Acceptance: a public server connects without any key/login/grant and a non-Shopify business-OAuth service connects without manual token pasting. Cancelled business consent grants nothing; refresh/revoke/reconnect and two-customer isolation pass. App visitors have no separate upstream login or account-connect flow.

### Phase 2: Protocol And Native Schemas

- [x] Qualify a maintained MCP client/parser for bounded JSON/SSE/session/progress/cancellation, response IDs, protocol negotiation and its remote HTTP+SSE adapter. Transport compatibility is protocol handling, not a semantic LLM fallback. Respect resource/host credential boundaries on any discovered message endpoint.
- [x] Paginate through standard `params.cursor` in discovery and schema verification; preserve provider-specific list arguments only where explicitly configured.
- [x] Preserve annotations/task requirements and advertise only implemented capabilities.
- [x] Implement offline native-schema validation and correct the existing admin importer's READ/parameter loss without rewriting published versions.

Acceptance: multiple pages, SSE progress before a result, nullable/union inputs and invalid input/output cases behave correctly.

### Phase 3: Framework And Runtime

- [x] Probe released 0.8.47 against native-schema planning, connection/auth-required dispatch and no-write-retry expectations.
- [x] Propose/review only proven gaps, then implement native action-schema metadata, scoped credential resolution/auth-required projection and explicit retry policy as needed. Published 0.8.48 and approved 0.8.49 corrections passed public-consumer and actual runtime qualification.
- [x] Preserve HTTP actions, no-vector READ, confirmation/review, no-confirmation WRITE and existing Managed Tasks. Focused regressions and real-provider native cases passed; hosted public/business chat and Engage proof is recorded below. Combined MCP Task/Desk workflows are not separately claimed as new live tests.
- [x] Add target-specific gateway/broker provisioning/readback and trusted-subject propagation; do not reuse Shopify-only identity assumptions. Both immutable-runtime targets passed normal apply/readback; the live staging execution key was rejected for the production target. No reverse-direction live-key probe is claimed.
- [x] Qualify one existing app-identity ingress and the selected business integration's subject/account/ownership contract. Close only proven binding gaps; no new generic SSO service or per-visitor OAuth directory. Real Supabase/broker users completed the own-subscription write and foreign-object denial without another MCP login.
- [x] For an actual framework release, follow the framework safety checklist, fresh-cache consumer and real-provider fixtures; record any unrelated failure honestly. Both published patches passed fresh Central-only consumers. 0.8.49's real provider, reactor, packaged and vector-container checks passed; the same 14 Incident Room failures remain explicit, not waived into a green full-CI claim.

Acceptance: imported actions execute through a qualified released runtime, schemas reach planner/gateway unchanged, uncertain writes are not replayed, and a trusted signed-in user cannot change another user's business object.

### Phase 4: Mechanical Import And Existing UI

- [x] Add **Connect MCP** with explicit public/business connection presentation, conditional business-auth fields, scopes and nonsecret health. Public setup skips credentials; no personal-account selector or visitor consent flow.
- [x] Add tool selection, compatibility reasons, confirmation-for-all default/optional overrides, zero-inference import, draft diff/undo and explicit refresh. No mandatory custom form per tool.
- [x] Extend existing action forms with MCP destination/complex-schema inputs, without an irrelevant HTTP-route control.
- [x] Extend consolidated setup checks and apply verification with real metadata/auth evidence grouped by connection revision. Page view performs no credentialed calls. Actual metadata checks and both normal 27-pass/zero-failure release rechecks passed; the disabled-tool expectation mismatch was fixed, not bypassed.
- [x] Keep normal Engage/custom-app/Managed Task access using the Brain's configured connection and existing caller permissions. Engage and normal chat API were qualified live; Task/handover reuse has regression coverage, not a fresh combined live-product claim. Missing upstream consent remains an owner reconnect issue, not visitor MCP signup.
- [x] Keep app users in the business conversation: configured READ, write confirmation/rejection and real receiver receipt passed; existing handover regressions passed. Setup stays in the owner's Brain. Default technical result/confirmation presentation and automatic sign-in prompting have declared follow-ups, not hidden fixes.

Acceptance: with the assistant disabled/provider down, a customer connects/imports/edits/applies a tool; an authorized app user invokes it through that Brain without a new upstream account.

### Phase 5: Optional Assistant And Complete Qualification

- [x] Attach owned catalogue metadata to current assistant sources; add MCP labels and `serverRef` merge identity.
- [x] Provide page-aware help without mandatory prompts or endpoint/schema/permission/credential substitution. One real provider turn saved only name/description; UI Undo restored the exact action configuration.
- [x] Qualify the required matrix paths at their recorded layer, including both target journeys and product regressions. Keep two production automatic-sign-in prompt misses and unrelated CI limits visible; do not claim every model response or every existing product was freshly live-tested.
- [x] Document the two supported models, transport/auth/schema, business provider registration, reconnect/revoke and explicit incompatibilities. Label personal-account connections deferred/out of scope, with no implementation tasks or completion dependencies.
- [x] Clean owned fixtures and update 010.37/working context with actual source, test and deployment evidence. Attached disposable storage required explicit operator disposal before normal record-deletion retry; no automatic retirement claim.

All five phases constitute completion; do not label partial static-token import as broad support.

## 9. Verification Matrix

1. **No AI dependency:** assistant off, provider unavailable or inference allowance exhausted; connect/OAuth/discovery/import still work with no generation calls.
2. **Connection/auth models:** public with no grant/secret/auth request; business bearer, approved custom/multiple headers, business-owner PKCE and explicit client credentials. No alternate credential after rejection. Public does not bypass owner-selected app/action permissions or internal caller authorization.
3. **OAuth interoperability:** challenged/well-known metadata, distinct issuer, preregistered client, client metadata and DCR fixtures; consent denial/cancellation and providers without refresh tokens.
4. **OAuth safety:** wrong/reused/expired state, bad redirect/resource/issuer, token-endpoint/DNS/redirect attacks and secret-safe errors.
5. **Token lifecycle:** expiration, refresh-token rotation/concurrency, invalid grant, revocation/reconnect; tokens absent from exports/runtime environments/browser/model/logs.
6. **Identity:** customers A/B, ordinary app-user action permissions, staff revocation, explicit business-connection consent, target mismatch and a foreign public/business connection handle submitted by a real authenticated runtime. No personal-account grant fixture or UI/API is added.
7. **Protocol:** Streamable HTTP with JSON/SSE plus the qualified remote HTTP+SSE adapter, progress before result, response mismatch, pagination/cursor loop, session expiry, cancellation/timeout, oversize content and unsupported client features.
8. **Schemas:** local refs, nullable unions, oneOf/anyOf, numeric/string enums, decimal bounds, arbitrary keys, nested input, zero params and invalid structured output.
9. **Import:** select-all with no AI/manual schema authoring, conservative confirmation default, incomplete catalogues, identity/collision, unrelated changes preserved, reimport/undo, saved/blocked counts and no automatic new-tool exposure.
10. **Action authority:** READ without vectors, confirmed/rejected WRITE, explicit no-confirmation WRITE, disconnected upstream account, anonymous denial and configured human review.
11. **Write safety:** receiver commits then response is lost; verify framework/client/proxy do not replay. Check actual receiver counts, not just local receipts.
12. **UI:** customer/staff, desktop/basic phone, light/dark, popup-blocked/same-tab return, reconnect/cancel and complex schema editing without duplicate pages.
13. **Optional AI:** empty-hint help, allowed corrections, warnings/no retry, cancellation, source injection and attempted endpoint/credential/authority mutation.
14. **Regressions:** OpenAPI assistant, ordinary HTTP actions, customer secrets, existing Managed Tasks and Shopify MCP fixtures; unrelated serving products unchanged.
15. **Business-to-customer journey:** public configured READ works without upstream visitor consent; an already signed-in user requests cancellation of their own synthetic subscription using a business connection, receives the configured confirmation and actual receiver receipt without another MCP login. Prove forged subject/object IDs, a second user in the same business, another business, missing/expired identity and revoked access cannot authorize the mutation. Existing confirmation rejection causes zero writes; uncertain outcomes are not replayed. A provider requiring personal grants is visibly incompatible with the service-connection binding, not silently bypassed.

### Hosted Proof

Use controlled public/no-auth and non-Shopify business-static/OAuth fixtures with synthetic customer-scoped data: two customers for isolation, a paginated catalogue, complex-schema tool, read, confirmed write, no-confirmation write and an instrumented receiver. Add a compatible official public documentation MCP metadata/read check as a public-capability proof; record the provider/transport/version actually qualified. No personal-account grants or tests are implemented for completion.

The primary product proof is a conversation, not an autonomous-agent demo: an anonymous visitor can perform the configured public lookup, then a signed-in synthetic customer can complete their authorized subscription-cancellation request through the business connection. Include two end users in one business to prove object isolation in addition to cross-business isolation. Qualify the real identity handoff and receiver authorization, not just a mocked `userId` argument. Personal-account OAuth is deferred, not an optional branch to build during qualification.

Prove production Console -> customer staging -> normal production apply for the same disposable qualified Brain. Record immutable source/image/framework, nonsecret connection/grant scope, actual import diff, metadata checks and receiver call counts. Add no mandatory staging restriction to ordinary production upgrades.

Run optional enrichment with the real configured model separately; prove the core journey with zero inference. Source inspection, local passes, deployment parity and live behaviour are different evidence.

## 10. Security, Rollout And Scope Discipline

- Hard checks protect explicit boundaries: customer/subject/secret ownership, callback integrity, resource audience, exact applied tool, schema executability, side effects and finite I/O. Imperfect wording is not a hard failure.
- No lexical semantic classifiers, canned repairs, hidden provider/identity changes or permission expansion. Protocol parsing and exact IDs/credential comparisons are not semantic text matching.
- Validate every outbound discovery/auth/token/resource destination, not only the MCP URL. No credentials along unapproved redirects. Sanitize logs, traces and provider projections.
- Use existing revisions/save/permission semantics. No universal draft conflict, capacity, billing, drain, recovery, deployment or UI redesign is a prerequisite.
- Roll out auth metadata/migrations before UI. Qualify gateway and any required framework/runtime change before enabling dependent apply. Do not present partial support as working.
- Keep one current Platform/framework contract and immutable releases; no silent rewrite of installed customer/plugin versions.
- Local execution, optional interactive features and unqualified extensions stay explicitly unsupported, with separately reviewed adapters if a concrete use case requires them.

## 11. Completion Checklist

- [x] Mechanical import works without AI.
- [x] Public/no-auth discovery, import and permitted anonymous READ work with no credentials, grants or upstream login.
- [x] Business-authorized credentials and business-owner OAuth with refresh/reconnect/revoke are qualified; end users need no separate MCP login.
- [x] Customer-owned connections and customer/target isolation are proved; no unnecessary visitor account or grant-management system.
- [x] Conversation-driven public/private business journeys pass, including verified app identity, own-subscription cancellation and same-business end-user isolation. No autonomous-agent subsystem was added. Automatic sign-in prompting is not guaranteed; explicit sign-in passed in production.
- [x] Raw schemas survive discovery, planning and execution. Local/native real-provider cases, public official-provider read and hosted business READ/WRITE passed. The no-vector INFO gate was fixed in approved 0.8.49 and proven against the exact live reproduction.
- [x] Scoped gateway/broker access and nonreplayed writes are proved. Hosted authority checks passed 9/9; controlled transport receiver tests proved single dispatch after uncertain writes.
- [x] Required UI/protocol/apply journeys pass on the actual released runtime, with auth-entry modes and semantic misses explicitly distinguished in evidence.
- [x] Optional assistant adds help without auth/business execution authority.
- [x] Personal-account connections remain deferred: no visitor upstream grants, connection UI/APIs or grant store were added.
- [x] Customer guide, evidence and owned-fixture cleanup are complete; exact operator storage disposal and retained failure audit are documented.

## 12. Primary References

- [MCP tools](https://modelcontextprotocol.io/specification/2025-11-25/server/tools): schemas, pagination, results and tool execution declarations.
- [MCP transports](https://modelcontextprotocol.io/specification/2025-11-25/basic/transports): Streamable HTTP, JSON/SSE and sessions.
- [MCP authorization](https://modelcontextprotocol.io/specification/2025-11-25/basic/authorization): metadata, registration and resource-bound authorization.
- [Microsoft Learn MCP overview](https://learn.microsoft.com/en-us/training/support/mcp): a real public documentation/code-sample service with no authentication requirement, illustrating the public capability model. Documentation review is not a live compatibility test.
- [Resource metadata, RFC 9728](https://datatracker.ietf.org/doc/html/rfc9728).
- [PKCE, RFC 7636](https://datatracker.ietf.org/doc/html/rfc7636).
- [Resource indicators, RFC 8707](https://datatracker.ietf.org/doc/html/rfc8707).
- [OAuth security practices, RFC 9700](https://datatracker.ietf.org/doc/html/rfc9700).
- [MCP security guidance](https://modelcontextprotocol.io/docs/2025-11-25/tutorials/security/security_best_practices).
- [MCP task negotiation](https://modelcontextprotocol.io/specification/2025-11-25/basic/utilities/tasks): why task-required tools cannot be treated as synchronous results.

References inform this planned design; they are not proof of current LoomAI support.

## 13. Implementation And Verification Record

### 2026-11-11: Implementation Started

- Work remains limited to public/no-auth and business-authorized connections. Existing API actions, Shopify integrations and ordinary customer sign-in remain supported.
- Connection/OAuth/import backend, gateway protocol/schema handling and the existing Brain capabilities UI are independent work streams; the main session owns contracts, runtime/target integration, release review and hosted verification.
- New managed connections use the existing framework MCP executor extension to call a target-authenticated Platform execution path. Platform reconstructs the exact applied action and resolves owned credentials; runtimes receive no upstream grant/refresh token or shared gateway administration key for this path. Legacy Shopify dispatch is preserved.
- Local focused runtime bridge tests passed (7): anonymous permitted READ, verified context projection, no supplied action/secret forwarding, one-attempt uncertain WRITE, foreign-context denial, legacy dispatch preservation, business reconnect failure and invalid list handling. This is local evidence, not a hosted customer-journey result.
- Platform target-authority, import, assistant and gateway/UI qualification are in progress. No live deployment or successful external MCP execution is claimed at this checkpoint.

### 2026-11-11: Local Integration And Hosted Preparation

- Platform's scoped regression run passed **307 tests**, zero failures/errors/skips: runtime proxy, target authority, legacy admin import, assistant import/merge/policy, aggregate setup checks, Coolify provisioning and existing runtime environment generation. The earlier two test-harness mistakes (JSON Pointer syntax and a missing progress-tracker mock) were corrected; no production check was weakened.
- Customer authentication tests passed **21/21**, including a narrowly scoped browser-cookie policy for business OAuth authorization. Normal customer API calls still omit cookies and never fall back to staff credentials after session expiry. MCP discovery has an explicit longer bounded request timeout, not a global timeout change.
- The maintained gateway successfully discovered Microsoft Learn's three real public tools and executed one documentation search with native input/output validation. Actual negotiated protocol was `2025-06-18`; see the gateway README for artifact/payload hashes and cleanup evidence. No AI inference or business mutation was used for that protocol proof.
- Gateway local Java 17/21 checks passed **80 tests** before three additional ProdUS hash regressions; the expanded hash suite passed **8/8**. Streamable JSON HTTP negotiation of the existing ProdUS `2024-11-05` contract is being qualified separately, not replaced by an HTTP+SSE fallback. The shared production gateway has not been changed.
- Active staging/production bindings were inspected. Ten ProdUS pins use the existing staging receiver, currently intentionally stopped; legacy Shopify bindings carry no tool-schema pin. No published schema hash or release was rewritten. The currently stopped ProdUS receiver is not a fresh live integration pass.
- Framework native-schema work passed **1,619 scoped tests** and **171 overlapping focused tests** before the explicit-null follow-up. Null-preservation through collection/confirmation is required to avoid losing valid native arguments and remains under qualification. Framework publication, clean published-artifact consumption and real-provider checks are not complete.
- A disposable customer draft (`dep-3623c3f2`) and a synthetic external MCP/OAuth receiver are prepared for hosted verification. The receiver has no business database or customer data; its initial mount configuration failed visibly and was corrected through Coolify's existing storage API. Do not infer runtime deployment or conversation success from fixture preparation.
- Separate MCP encryption keys and callback/client-metadata/Console origins are configured privately for each control plane's next deployment. No upstream tokens, application keys or envelope keys are recorded in this plan.
- Remaining release gates: backend OAuth/connection/application tests, explicit target reuse and import undo, packaged boot/PostgreSQL migration, final framework qualification/publication, immutable CI images, hosted customer UI and conversation/identity/ownership/confirmation proof, optional AI enrichment, production rollout and owned-fixture cleanup.

### 2026-11-11: Control-Plane Rollout And Release Qualification

- Source commit `611e64ed62675c690921ac36ccd7b7abb846ed91` contains owned connections, business OAuth, native catalogue import/Undo, explicit target reuse, assistant metadata and the Brain-scoped Console flow. No runtime/framework release is inferred from that commit.
- Final integrated Platform run passed **408 tests across 24 suites**, zero failures/errors/skips. Runtime bridge **9/9**, customer authentication **21/21**, and focused UI **80/80** passed separately; overlapping counts are not summed. UI machine-OAuth browser qualification and production build passed.
- CI published immutable backend/UI images for that source. Backend digest `sha256:032e63077b6a4d061c9978fb942bb5f4538bffad79df9af9faced22effcac5ec`; UI digest `sha256:291dd08cf52cb4e4d0de639252504f7df4fff6373db14fe03c46a593119cba41`. Backend is healthy on both control planes; staging UI is healthy. Production UI deployment is in progress at this checkpoint.
- Actual staging migration `241` succeeded. Staging health and OAuth client metadata returned HTTP 200; an unauthenticated runtime execution returned HTTP 403 / `MCP_ACCESS_DENIED`. Production customer's scoped connection catalogue returned HTTP 200 with no connections before hosted qualification.
- The separate quota/Desk CI run failed because its runner could not provide a Docker environment. The two affected PostgreSQL suites were rerun locally: **19/19 passed**, including all production migrations. This is local evidence, not a green CI result. The pre-existing dealership PostgreSQL fixture errors are unrelated and remain recorded separately.
- Both databases' published bindings were inspected: the same ten ProdUS tool pins are present; no hashes were rewritten. Complete corrected-hash comparison is being added to gateway qualification. The historical ProdUS receiver remains intentionally stopped, so source/hash compatibility is not a fresh live ProdUS pass.
- Framework candidate `0.8.48` passed **2,372 tests across 30 modules**, with no failures/errors/skips, after native-null preservation and trusted READ-context resolution. The first new real-provider fixture failed before inference because it lacked its application-owned chat policy; correction is confined to that fixture. Container/external-consumer checks and publication remain pending.
- Gateway local qualification passed **104/104** on Java 17 and 21. Review identified that destination DNS preflight alone does not pin the SDK's subsequent connection. A bounded connection-time destination protection is required before arbitrary customer endpoints are enabled; no host-wide firewall or unrelated service redesign is being introduced.
- Remaining completion proof is still hosted UI/OAuth/import, actual released-runtime conversations with verified end-user ownership and confirmation, production-target apply, optional assistant and fixture cleanup. These are not marked complete by source tests or a healthy control plane.

### 2026-11-11: Hosted Connections Complete; Released Runtime In Qualification

- Gateway source `41e43e6186a7b8c4dbf955662cb954c0517ff981` is healthy on both hosts at digest `sha256:9467fa70e704643d69f8cd99ab3cfcb3f5f2f246a829384ef551b01bc0865195`. Its 162-test Java 17/21 suite covers connection-time DNS pinning, TLS/SNI, protocol/native schemas and no replay of uncertain writes. The production gateway executed a real Microsoft Learn documentation search with the discovered schema pin; no inference was needed.
- Deployed connection API qualification passed **13/13**, with separate harness safety **4/4**. Static credentials, client credentials, business-owner DCR/PKCE, consent cancellation, natural token expiry, rotating refresh, revoke/reconnect and a genuinely different Console customer were checked. All nine API-only connections and seven temporary keys were disconnected/deleted afterward.
- Actual production browser qualification passed public and business consent/discovery/import, native-schema display, actual diff and guarded Undo, explicit production binding/revocation, desktop/basic phone and light/dark. A fixture scope mismatch correctly failed closed; correcting its requested scopes made the same records pass, without product defaults or weaker validation.
- Optional assistant qualification used one real GPT-5.4-mini call, 9,000 input / 220 output tokens. Only requested name/description changed; native schemas, connection authority, permissions and other actions stayed unchanged. UI Undo restored the exact prior draft.
- Backend source `1e3e7441f03de75fdc8f3ea787be12aef6f6de8e` is healthy on both control planes at digest `sha256:b4bf068e3b6d0e6f1c9f460264b9045fc21b6a1248fcf7e5546dec43c0b5ef11`. The existing UI remains at `611e64ed62675c690921ac36ccd7b7abb846ed91`. Header-auth guidance and MCP-specific route validation passed 104 focused tests before deployment.
- Framework **0.8.48** was published from `9413d3b38d807a8e23d754021b50eb39c03df634`. Corrected fresh reactor: **2,373 passed**; real-provider native-schema cases **5/5**, eight turns; reference server **6/6** plus packaged/authenticated discovery. A separate empty-cache, Central-only external consumer passed **6/6**, packaged and booted, resolving only framework 0.8.48 / NetworkNT 3.0.0. Known Incident Room 14 policy failures remain separate and unchanged.
- The initial private-runtime image CI ran 814 tests with two obsolete 0.8.47 readback expectations. Test-only commit `3ff037b73eb0ab9b8fe58f90b99193b65497a5be` updates these and checks the managed-MCP capability; both local suites passed 8/8. Its full CI/image run is pending at this checkpoint.
- Disposable Brain `dep-3623c3f2` published version `ver-50cab2c9`, explicitly reporting framework 0.8.48. It uses no-vector READs, configured verified Supabase identity and imported public/private/cancel tools; cancellation requires confirmation. No runtime apply, successful chat mutation or activated Engage app is claimed yet.

### 2026-11-11: Both Targets Verified; Business Journey Passed

- Runtime CI `43073267779` passed all **814 runtime tests** and published source `3ff037b73eb0ab9b8fe58f90b99193b65497a5be`, digest `sha256:36138150f2c7ebf0ec8d87990b4406ce1fea84c95f908e3a179e60352174876d`. Registered normal runtime artifact `dsa-ac5fa3fd`; staging `rel-42fc159e` and production `rel-4c472bac` use the same version/artifact. Production connection reuse was explicitly enabled. No vectors, business data or upstream credentials were copied between targets.
- Both applies passed live MCP authorization/schema checks and reached healthy runtimes. Initial post-apply checks exposed a Platform mismatch: the runtime correctly excluded the disabled schema example, while the verifier expected all four source definitions. Fix `ca33555693b135ed15e70eff9c5fa66453917e99` compares enabled action names/per-action metadata only, preserving global confirmation/webhook/routing contracts. Its suite passed **44/44**, including rejection of an unexpectedly active disabled tool.
- That backend image is healthy on both control planes, digest `sha256:07c6ed49f2cc79fbda2d7cfdb7c1883ac330fe1a7a577ae44b6db75491469869`. Normal release rechecks passed **27 checks, zero failures, seven legitimate skips** each. No tool was enabled, published version rewritten or verification result overridden.
- Actual runtime authority/ownership probes passed **9/9** against both live targets. Missing/wrong/cross-target keys, foreign Console Bearer, altered customer scope and expired-context fields were denied; two real Supabase/broker identities read only their own objects despite spoofed actor arguments. The explicit foreign-object call reached the business receiver and returned `SUBSCRIPTION_ACCESS_DENIED`, with zero additional writes. Scope/expiry perturbations are not claimed as natural JWT-expiry tests.
- Normal stage chat passed the separate **eight-turn business journey**: own READ; cancellation proposal/rejection with zero writes; a new proposal/confirmation with exactly one write and real receiver receipt; second-user own READ; foreign-object proposal/confirmation rejected with zero writes. A normal production chat read the resulting `CANCELLATION_SCHEDULED` state. No visitor MCP login was introduced.
- The disposable Engage hostname passed normal Verify domain and HTTPS; all **16 readiness checks** passed. The app activated on staging, and actual desktop/basic-phone bootstrap reached the recorded runtime for session/auth/shell/history with no browser errors or overflow, zero inference and zero writes. It was then explicitly switched through the existing consumer-binding API to the verified production release, keeping the same app identity/URL and passing readiness again. Actual production desktop/basic-phone bootstrap also passed, with observed HTTP 200 requests to the recorded production runtime, no overflow/browser errors and zero AI queries/sign-ins/writes. This is not a full browser AI-conversation pass.

### Historical Gate: Generic No-Vector Information Routing

The implementation was **not fully closed at this checkpoint**. Two natural public INFORMATION questions failed before any MCP call, with `Retrieval requires a configured vectorSpace allowlist in this mode.` The same released runtime successfully planned and executed a normal private INFORMATION read, so this was not an upstream authentication, native-schema or trusted-identity failure. The subsequent approved 0.8.49 correction and closure evidence follow below.

Root cause: extraction unconditionally requires a vector for INFORMATION/requiresRetrieval, even when eligible READ tools can provide the evidence; information handling then checks indexed-RAG permission before READ planning. `vectorSpaceSelectionRequired=false` and READ `ragCooperationMode=NONE` do not clear the original retrieval decision. The first extraction repair invented a source name; this is retained as a failure, not concealed by a fabricated allowlist, text matcher, search-all or altered test result.

Owner approval was requested for this bounded generic framework correction before any new source/release work:

1. Let trusted, authorized READ planning provide evidence before the RAG-specific rejection. Keep the final authorization/allowlist/budget gate immediately before actual indexed retrieval.
2. Make missing-vector validation consistent with the effective READ-resolution path across extraction strategies, without granting unauthorized tools or vector access.
3. Clarify managed prompts: live READ evidence is not indexed vector retrieval; never invent a source to satisfy a field check.
4. Prove no-vector public/private INFORMATION with zero RAG calls, denied actual RAG without permission, disabled/private tool exclusion, trusted fields and existing indexed-RAG/confirmation/write regressions. Use a real provider and the framework release checklist for any approved release.

No second framework patch has been implemented or published at this checkpoint. Existing configuration can disable indexed retrieval without disabling authorized READs, but it does not correct premature rejection when a model still requests retrieval; do not present that setting as a complete framework fix.

Remaining closure: approved routing correction if authorized, full public/browser conversation qualification on its actual released runtime, final owned fixture/canary/second-user cleanup, and completion evidence. Preserve the current canary records for that proof; do not delete existing customer accounts or shared services.

Separate presentation finding for review: business confirmation plain text currently includes the machine action name and trusted actor argument; the plain write answer is generic despite its real structured receipt. This is not a foreign-object authorization bypass and is not silently folded into the requested routing patch.

### 2026-11-11: Bounded Routing Correction Approved

- Owner approved the generic READ-before-RAG ordering, missing-vector validation
  and prompt correction, with a verified framework patch. This supersedes the
  preceding pending-approval gate only; it is not evidence of a finished fix.
- Framework work targets 0.8.49. Preserve indexed-retrieval authorization,
  allowlists and budgets, READ permissions, trusted identity and write policy.
  No lexical routing, invented vector, automatic fanout or presentation change.
- Main prepares the existing canary's ordinary version/image upgrade while
  the framework patch and focused real-provider regressions are qualified.
  Publish only after review; immutable public consumption, runtime CI, normal
  stage/prod apply, complete browser chat proof and owned cleanup remain gates.

### 2026-11-11: Released 0.8.49 And Final Supported-Path Qualification

- **Current source:** both backends fbb7622b6, both UIs unchanged at 611e64ed,
  both gateways unchanged at 41e43e61. Public framework 0.8.49 is source
  abecb067 / immutable tag ai-fabric-framework-v0.8.49; Central publication
  43090421221 succeeded. Runtime image CI 43093161948 passed 814 tests and
  published digest d24fd7c0370ef740679d3d803fcbd08135b9deba5191205b10c0b79767fe3b96.
  Backend image CI 43093161980 succeeded, digest
  2f14cc0283878deafed25efb90a82e17a2f495bc6901861a04ac3c878f8ec058.
- Approved generic correction lets qualified READ planning precede RAG-only
  rejection, carries trusted context through extraction and defers missing
  vector validation only for that path. Final actual indexed-search permission,
  allowlist and budget checks remain. No keyword routing, forced intent,
  invented vector, automatic fanout or new fallback was added.
- Framework local reactor 2,394; focused routing 190; guards 7; vector unit 18
  and real container 8 passed. Actual Java 21/OpenAI native 7/7 (ten turns) and
  OpenAI/ONNX-384/Lucene core 11/11 passed. Normal core runner clamps minimum
  20 to its actual 11-case chunk; no threshold override. Earlier Java-selector,
  invalid-dimension setup and coverage-question attempts remain documented.
- New empty-cache Central-only consumer passed 6/6, packaged and booted on
  Java 21. All four packaged framework JARs matched the actual public-cache
  bytes. Private runtime 814 and ordinary REST connector 102 passed locally;
  counts overlap and are not summed. Broad framework CI 43090314922 retained
  exactly the known 14 Incident Room failures, not an all-CI-green claim.
- Same disposable Brain dep-3623c3f2 published ver-d5ab80c3 and used artifact
  dsa-c02ede18: stage rel-aacd3c66 and production rel-b014f654 both reached
  APPLIED_VERIFIED, fresh rechecks each 27 passed / 0 failed / 7 legitimate
  skips. Same infrastructure/app identity and explicit authorized bindings;
  no business-data, credential or vector copying. Existing customer Brains
  were not automatically upgraded.
- Full nine-turn staging chat passed the exact original public INFO query
  with one public READ, private reads, reject/no write, confirm/exactly one
  real synthetic cancellation, second-user own read and foreign-object denial.
  Production public INFO and private read of that external cancellation state
  passed. Actual customer/broker identities were used, no visitor MCP login.
- Same Engage app passed all 16 readiness checks and normal target switching.
  Staging browser passed public -> typed protected challenge -> real sign-in
  -> private read; production browser passed public -> actual Sign in button
  -> real sign-in -> private read. Desktop/basic phone passed with no browser
  errors or state changes. **Two production automatic-challenge expectations
  failed:** one acknowledgement, one public-only answer admitting no private
  status. Both remain failures in evidence; explicit sign-in success does not
  relabel them. No hidden retry, matcher or additional framework patch.
- Final authority probes passed 9/9, 46 requests, zero extra writes. Scope
  clarification: the actual staging execution key was tested, including its
  rejection for a verified production target; not live keys in both directions.
  Two real end users and a real other Console customer were used. Altered
  expiry is not a natural JWT-expiration test. All new local sessions closed.
- **Cleanup complete:** normal deletion del-b64d0d44 initially hit attached-
  storage retirement protection. Storage-free connectors were removed normally;
  explicit operator disposal removed only the two test runtimes, two AI-state
  databases and four verified owned volumes. No global prune/network deletion,
  SQL mutation or guard weakening. Normal retry created del-ac4c1582 and
  SUCCEEDED; old failure remains in audit. Exact fixture/mount/source and only
  the second synthetic Supabase identity were removed. Read-only DB/native
  checks prove Brain, app, consumer, MCP/child rows, all seven native resources,
  all containers and four volumes absent. First owner and shared services kept.
- Final Console/API HTTP 200 and both backends healthy. Host point samples:
  staging load1m 3.01 / available RAM 3,533 MiB; production 1.66 / 8,598 MiB.
  These are not load tests or capacity/admission guarantees.
- Evidence: verification-support/mcp-public-business/README.md; private raw
  receipts under ~/.codex/private/loomai-mcp-01069/. 010.37, customer guide and
  working context updated. No pending verification worker or canary resource.

**Declared follow-ups, not silently implemented:** automatic sign-in prompt
selection quality; clearer MCP confirmation/result cards (currently technical
IDs/hash diagnostics can appear); optional future automated owned-storage
retirement; unrelated Incident Room baseline. The first acknowledgement's raw
model-versus-normalizer cause was not fully traced and is not asserted from its
receipt alone. Personal-account OAuth, local executable servers and asynchronous
MCP tasks remain outside this plan's approved scope.

### 2026-11-12: Released 0.8.50 READ Authentication And Answer Contract

- Owner approved the bounded fix and extensive chat-query debugging. A missing
  final direct answer now returns `MODEL_DIRECT_ANSWER_MISSING`, not invented
  successful `Okay.` text. Actual model-authored text remains unchanged.
- Enabled, allowlisted protected READs may be visible to INFORMATION planning.
  Selection returns the existing typed app-authentication requirement before
  parameter resolution or handler invocation. Planning visibility never grants
  anonymous execution, another user's identity or vector-search permission.
- Safe intent-decision/answer-source diagnostics were added. No phrase routing,
  hidden response replacement, automatic fanout or new retry loop was added.
  Full HTTP/metadata capture and genuine native SDK `features.debug=true`
  inspector were qualified. The saved generic Engage pack still forces its
  inspector off; there is no new backend query-debug field or global public
  debug enablement.
- Framework reactor2,413, focused77, guards7, vector containers8 plus18 unit,
  empty-cache Central-only consumer15 and packaged/booted Java21 consumption
  passed. Runtime CI43131305652 passed accounting78+103 and runtime814 with
  zero failures/errors/skips. Ordinary connector102 and Platform compiler/
  manifest/verifier75 passed locally. Test totals overlap; do not sum them.
- Native real OpenAI qualification finished9/10: one public question selected
  acknowledgement/context instead of READ evidence. General indexed matrix
  passed11/11. Central43128536259 succeeded with two runner-Docker-unavailable
  lease tests skipped; both executed locally. Broad CI43128510006 retains the
  same14 Incident Room failures, not a green full-workflow claim.
- Both backends now use b334440c0. New disposable Brain dep-f52626dc published
  ver-b213721b using artifact dsa-67fc22ca, runtime digest
  `sha256:0d45bc52742744a9f4dc13c61b136d35cb3fb18c207fb95a1e8dce57304c5d62`.
  Stage rel-4f64c49f and production rel-d46f38a1 reached APPLIED_VERIFIED/ACTIVE;
  fresh post-apply checks each passed27/failed0/not-applicable7. Existing
  customer Brains were not automatically upgraded. No business/vector copying.
- The first hosted public-guide question failed source-use quality: USE_RAG_ONLY
  with no available vector source, zero tool calls and an honest missing-evidence
  answer. Explicit continuation did not repeat/rephrase it. Nine subsequent
  auth/read/write/foreign turns passed: private anonymous INFORMATION caused
  sign-in with no handler call; real own READ, reject0writes, confirm1write,
  second-user own READ and actual foreign-object denial passed.
- Frozen natural/verbose questions ran once in each browser mode. Staging normal
  Engage and native debug: automatic sign-in4/4, signed-in reads2/4; both natural
  signed-in attempts incorrectly selected REQUEST_AUTHENTICATION despite trusted
  authenticated=true. The server rejected the choice without execution and
  returned the explicit missing-answer error. Production normal Engage passed
  all4queries: automatic sign-in2/2, signed-in own-subscription reads2/2. Full12
  browser responses, real dialogs and inspector screenshots retained. No browser
  errors, repeated/rephrased cases or extra mutations; local sessions closed.
- These are separate model-selection quality findings, not concealed successes.
  Production success does not erase staging failures. Further framework changes
  require owner review. A simpler candidate is model READ selection with
  server-derived auth state, removing authentication as a separate model choice;
  this is not implemented. Do not silently turn a rejected decision into a call.
- Exact new-fixture cleanup completed: normal deletion del-d16f3a62 hit the
  attached-storage safeguard; operator disposal removed only the verified owned
  runtime/AI-state services and four volumes. Normal retry del-9da33ff6 SUCCEEDED.
  Final read-only proof passed eleven database counts0, customer/API/native
  absence, fixture/source removal and second-identity deletion. Original owner,
  shared services/artifact/image and audit history retained. First absence probe
  observed asynchronous fixture deletion still pending; that receipt is kept,
  followed by native404/container absence and final successful proof. No replay
  of model queries or deletion, SQL write, global prune or guard weakening.
  No verification worker or disposable resource remains. Full evidence:
  `verification-support/mcp-public-business/README.md`; raw receipts remain in
  private `loomai-framework-0.8.50` and `loomai-mcp-read-auth-20261112` directories.
