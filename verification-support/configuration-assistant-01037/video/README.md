# Build A Smart Brain With AI

The newer [document-first recording](README-document-first.md) demonstrates
page-aware preparation, source reuse and Undo. This earlier video is retained
as a separate walkthrough, not overwritten or presented as the latest UX.

Recorded against the actual production Console on 10 November 2026. This is a
configuration demonstration, not a mock UI or a deployment demonstration.

The rendered MP4 is kept out of Git:

`Final_Documentation/Demo_Videos/configuration-assistant-20261110/loomai-build-brain-with-ai.mp4`

Duration: 5 minutes 23 seconds. H.264 video, AAC narration, 1600 x 1120. The source
is a continuous real browser recording with edited pauses, chapter captions,
pointer highlights and synthetic Daniel narration. It is not the founder's voice.

## What It Shows

1. An empty, undeployed Smart Brain, created through the ordinary authorized API
   before recording. The template wizard is not represented as working: its
   existing `REVIEWED_COMPOSITION_CHANGED` warning was observed separately.
2. Upload and review a dealership demo OpenAPI document and its three operations.
3. Real AI generation of anonymous-enabled READ actions for stock search,
   vehicle details and comparison.
4. A subsequent routing-format failure, visible validation findings and an
   explicit user follow-up that corrects it. This recording predates the newly
   approved automatic bounded-feedback policy; it does not claim an agent loop.
5. Manual refinement of an AI-created action using the existing forms.
6. AI changes to response profiles and the response model, with existing routes,
   the manual action label and security settings preserved.
7. The existing Updates review and draft check, without publishing or applying.

Five actual hosted model requests consumed 41,289 reported provider tokens in
total. Every attempt was retained; the second request saved a draft with three
route errors, and the third corrected them. Final check: zero errors and two
warnings (no indexed entity types; remote authorization endpoint not configured).
This is not proof of a live business integration or a production-ready deployment.

The API reference is derived from `InventoryController` in the fictional
dealership demo, **not** the Auto Trader partner API. Its server hostname is
deliberately non-routable. No business API request, enquiry, write, publication,
provisioning or assignment was executed. No framework or product source was
changed to obtain the recording.

## Verification And Cleanup

- Browser runtime errors: zero.
- Three READ definitions and three documented GET routes in the final draft.
- Warm / Grounded / Brief / Contextual profiles, response model `gpt-5.4-mini`.
- Security and inbound connector protection unchanged.
- Only the response-model provider field changed; embeddings/storage unchanged.
- Zero published versions and zero releases.
- Full MP4 decode passed; chapter contact sheet inspected.
- Three temporary authoring sessions deleted, recording-only Brain archived.
- Normal customer signed out; no credentials appear in the recording.

Raw capture, receipts and final/initial draft snapshots remain private under
`~/.codex/private/loomai-assistant-video-20261110/`.

## Reproduce The Render

Given a new private `take.private.json` with the real capture path, duration and
chapter timestamps, adapt `edit-plan.json` to that capture; never apply these
timestamps to a different take without reviewing it.

```sh
node verification-support/configuration-assistant-01037/video/render-video.mjs \
  /absolute/private/capture-directory \
  verification-support/configuration-assistant-01037/video/edit-plan.json \
  /absolute/output/loomai-build-brain-with-ai.mp4
```

The renderer requires Playwright from the existing UI dependencies, FFmpeg,
FFprobe and the macOS `say` Daniel voice. It never invokes AI or production APIs.
