# Development Guidance

Before changing AI validation, prompts, routing, retrieval, or actions, read:

- `Final_Documentation/Development_Guides/LLM-guides/PLATFORM_LLM_SESSION_OPERATING_CONTEXT.md`
- `Final_Documentation/Development_Guides/LLM-guides/AI_FABRIC_PLATFORM_PRODUCT_PHILOSOPHY.md`
- `Final_Documentation/Development_Guides/LLM-guides/AI_FABRIC_FRAMEWORK_PHILOSOPHY.md`

Apply the shared rule: hard trust boundaries, minimal execution contracts,
guided LLM decisions, visible uncertainty. Do not interpret fail-closed security
as a requirement to reject every imperfect model response. New hard validators
must name the trust boundary or necessary execution requirement they protect.

Use `CODEX_WORKING_CONTEXT.md` in that guide directory for current execution
status; historical entries are not proof of the current deployed version.

For UI presentation profiles, read
`Final_Documentation/Development_Guides/PLATFORM_UI_PROFILES.md`.
Keep the staging developer profile independent of customer page layout. The
010.65 customer-wide redesign was rejected; do not reapply it without approval.
