# Connect MCP Capabilities To A Smart Brain

MCP is an optional connection method for existing services. Your ordinary API actions and API-document assistant remain supported; you do not need to wrap an existing API in MCP.

Release and live qualification evidence is recorded in implementation plan 010.69. A saved connection, discovery receipt or draft import is not evidence that a live action has executed successfully.

## Supported Connections

- **Public - no authentication:** documentation and other public tools. No upstream account, key or OAuth login is needed. Configure the selected action's permissions separately before permitting anonymous visitors.
- **Business-authorized:** your integration team connects the company service using an owned key or business-owner OAuth. Your app customers use the approved capabilities without connecting personal MCP accounts. Private operations still require verified app identity and business-system ownership checks.

Personal-account connections, such as each visitor connecting their own external calendar or drive, are deferred. Business-owner OAuth during integration setup is not a personal-account flow for your app visitors.

## Connect And Add Capabilities

1. Open your Smart Brain, select its environment and go to **AI capabilities -> MCP connections** or **Connect MCP**.
2. Enter a name and the remote HTTPS MCP endpoint. Select **Public - no authentication** or **Business-authorized**.
3. For a business connection, choose an owned reference from **Keys & secrets**, or connect the company account through OAuth. Never paste credentials into AI assistance. OAuth providers may require your integrator to register a client using the displayed callback address.
4. Select **Discover tools**. This retrieves metadata; it does not execute business tools or spend an AI-generation allowance.
5. Choose compatible tools. Review the input/output schemas and any incompatibility findings. Server hints do not automatically grant read-only or anonymous access.
6. Review execution policies, then **Add selected tools**. Newly unverified tools default to confirmation required, no anonymous access and no read grounding. Explicitly trusted READ tools may be configured for seamless permitted lookups. The owner may explicitly allow supported no-confirmation writes.
7. Use the normal **Updates** workflow to check the saved draft, review changes, publish, apply and verify. Adding tools to the draft does not update your live app.

The import receipt shows the saved changes and offers **Undo this import** while that action configuration is still unchanged. Later edits are not overwritten by Undo. Refreshing a catalogue identifies new, changed and no-longer-returned tools; it does not remove or replace live capabilities automatically.

Updates offers compatible runtime images for managed MCP connections. An older image without the managed connection bridge is not a valid deployment target for these actions. Legacy MCP and ordinary HTTP actions keep their existing compatibility paths.

Imported tools are ordinary action bindings. Their upstream names and native schemas remain source metadata; refresh and review them when the provider changes its contract. Existing HTTP actions retain their normal routing editor. Optional **Help configure** uses selected nonsecret catalogue metadata and cannot connect another account or execute business tools.

## Signed-In Customer Actions

For a request such as cancelling a subscription:

1. Your website/app supplies the customer's identity through the configured verified identity handoff or authenticated backend.
2. The Smart Brain checks action access and the configured confirmation/review policy.
3. The integration resolves the trusted user context. Your MCP server/backend checks that the subscription belongs to that user before changing it.
4. LoomAI calls using the company connection and returns the actual business-system result.

The website's login cookie is not automatically shared with an embedded or standalone Engage origin. Qualify the identity handoff during integration setup. A typed email, an arbitrary customer ID in chat or the LLM's choice is not proof of account ownership. Do not forward a website/Console login token as an MCP credential for a different resource.

An ordinary app session may need renewal. A company integration needing OAuth renewal is an administrator reconnect issue, not a request for every visitor to connect an external account. A cancellation confirmation, when configured, is separate from authentication.

## Environments And Connection Changes

Staging and production require explicit authorized connection bindings. Promotion does not copy OAuth tokens, business data or vectors. Reusing the same business account/endpoint across environments must be an explicit permitted binding, not an assumed consequence of staging discovery.

Select the destination environment in the Brain header, open the saved connection and enable **Use this connection in this environment**. This authorizes that same endpoint/account for the selected environment; it does not deploy, import tools or duplicate credentials. A key restricted to staging cannot be reused in production. Use an appropriately scoped connection instead.

Credential refresh is connection maintenance and does not require republishing. Changing connection authority or tool schemas requires the corresponding reviewed draft/apply workflow. Disconnecting a connection makes its tools unavailable without deleting their draft definitions. Public sources have no OAuth reconnect or token-refresh step.

## Troubleshooting

- **No tools / incomplete catalogue:** check the endpoint and actual transport. Pagination/deadline/size failures are not a complete catalogue. Refresh explicitly after correcting the connection.
- **Authentication required:** ask the business administrator to inspect the owned key, scopes or company OAuth connection. No downgrade to another account or public access is attempted.
- **Private action denied:** check the verified app identity, selected action policy, trusted argument mapping and business-server ownership checks. Confirmation does not grant access to another customer's record.
- **Connection/schema changed:** refresh the tools, review the real changes and apply an updated version. Existing releases are not silently rewritten.
- **Unknown write outcome:** inspect the business system or contact human support before retrying. LoomAI does not automatically replay an uncertain MCP write.
- **Personal consent required by the provider:** that connection model is outside the current supported scope. Use an authorized company-service integration or the existing API action route where available; do not substitute an owner's personal token.

MCP tool calls may send their selected arguments to the external provider. Review the provider and business-data disclosure before enabling a capability, even if it is public and read-only.
