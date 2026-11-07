# 010.64 Smart Brain Workspace Design Images

Status: review-only visual proposals, not production screenshots or an implemented prototype.

Main specification: [010.64 Smart Brain Workspace Redesign](../../010_64_SMART_BRAIN_OUTCOME_LED_WORKSPACE_AND_NAVIGATION_REDESIGN_PLAN.md).

## Images

- [Responses, dark mode](responses-dark.png): natural-language reply controls, illustrative customer preview, one save footer.
- [AI capabilities, light mode](capabilities-light.png): connected-capability list, selected detail pane, technical disclosure.
- [Updates, dark mode](updates-dark.png): saved-draft comparison, actual readiness layout, one target-specific publish/update workflow.

All three are project-owned copies of outputs generated with the built-in image-generation tool. No fallback CLI was used. Responses received one targeted image edit to replace repeated resource context with `Editing shared draft` and make the unapplied state explicit. The other images used the first screen as a layout reference.

## Reusable Prompt Set

The following is a consolidated regeneration brief, not a verbatim tool-call transcript.

### Shared Brief

Use case: ui-mockup. Create a high-fidelity flat desktop LoomAI Console resource workspace. It should feel like a polished AWS-style operational console with the existing LoomAI Kit visual quality, not a landing page. Use fictional Northfield AI example data and mark the output DESIGN PROPOSAL. Include a slim top utility bar with resource breadcrumb; one persistent 200-220 px resource sidebar; a shared-draft/target/status strip; a bounded main task area; and one fixed action footer. Use the same frame and spacing across every view. Sidebar sections are Overview, Knowledge, AI capabilities, Responses, Channels, Human support, Try it, Updates and bottom-aligned Technical settings. No hero section, decorative gradient, orbs, nested cards or oversized type. Use clean readable text, thin dividers, restrained surfaces and recognizable interface icons.

Dark direction: near-black `#111619`, panel `#191f24`, divider `#36414b`, text `#f1f3f5`, muted `#afbdcc`, blue `#4ca5ff`, mint `#48dfbe`. Light direction: background `#f7f9fa`, white surfaces, divider `#cbd5df`, text `#17232f`, muted `#566677`, blue `#116cca`, mint `#117e6d`.

### Responses

Dark mode. Responses selected. Title How your AI answers. Task views Reply style and Instructions. Left pane: Tone = Consultative; Use of general advice = Helpful guidance; Answer length = Balanced; Helpful next steps = When useful; link to profile instructions. Right pane: explicitly labelled illustrative preview, a fictional customer asking about a family car, a concise helpful response referencing current inventory, and a Try with your Brain command. It must say the example is not a live result. Context strip says Editing shared draft, selected Staging, illustrative Running v12 and Changes not applied. Footer says Unsaved changes, with Discard and Save draft.

### AI Capabilities

Light mode using the same resource frame. Title What your AI can do. Connected and Available task views with Add capability. A readable table/list of dealership tasks occupies the left pane: Search stock, Compare vehicles, Request test drive, Request callback and related configured capabilities. Select Request test drive; the right contextual detail pane shows purpose, Settings and Technical, required name/email/phone, optional preferred date/message, customer confirmation and sign-in requirements, and a connected dealership request-system destination. Show the backing integration without overwhelming the customer with raw JSON. Footer says Saved to draft, live unchanged and Review & publish. Keep the UI generic; dealership content is fixture metadata.

### Updates

Dark mode using the same resource frame. Updates selected. Title Review and publish changes. Review changes and History task views. Main pane compares applied and draft values for reply tone, use of general advice and a configured test-drive capability. Beneath it show compact illustrative configuration, connection and access checks. Right pane shows the intended publish, update-existing-target and verify steps; selected Staging and the unchanged customer app address; Production is not changed. Footer says Draft saved and has Back to editing and Publish & update Staging. The actual product must show the real failed step and retry actions only when a failure occurs.

## Implementation Fidelity Notes

Use the real existing LoomAI brand assets rather than adopting the generated mark. Example names, versions, checks, connection states and customer conversations are not verification evidence. Use the specification's actual desktop dimensions/type scale and backend-supported state labels; image-generation details are not an API or an additional product requirement. No design annotation or feature explanation should be copied into the running interface as instructional filler.
