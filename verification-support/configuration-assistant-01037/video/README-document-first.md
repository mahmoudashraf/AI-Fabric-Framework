# Document-First Configuration Assistant

Recorded against the actual production Console on 10 November 2026. This is a
real configuration demonstration, not a mock UI or a deployment qualification.
The earlier [configuration walkthrough](README.md) is preserved separately.

Generated media stays out of Git:

`Final_Documentation/Demo_Videos/configuration-assistant-20261110/loomai-document-first-assistant.mp4`

Duration: 3 minutes 10 seconds. H.264/AAC, 1600 x 1120, approximately 12.6 MB.
The real browser capture has chapter captions, pointer/click highlights, edited
pauses and synthetic Daniel narration. It is not the founder's voice.

## Recorded Journey

1. Start with a new, empty, undeployed Brain draft. It was created through the
   ordinary authorized API before recording; the creation wizard is not shown.
2. Open the Actions assistant and inspect the existing bounded correction option.
3. Upload the fictional dealership OpenAPI file and review its operations.
4. With the message box empty, click **Prepare actions**. Three READ definitions
   and their documented GET routes appear in the ordinary configuration editor.
5. Use a short follow-up to rename stock search, then Undo the saved change.
6. Navigate to Responses and explicitly reuse the same Brain's API document.
7. Paste a plain-language response guide and add the short optional hint
   **Use the Northfield response guide.** Then select **Prepare responses**.
8. Review the draft through the existing Updates page. No version is published.

All three recorded turns saved on their first model attempt with zero formal
configuration errors. Existing no-entities and missing remote-authorization
setup warnings remain visible. Opening, importing, navigating and reusing
sources did not invoke inference. This take does not demonstrate an actual
validation-feedback correction because none was required.

## Honest Quality Limits

The response turn saved custom warm, brief and grounded instructions, preserving
actions, routes, provider settings and security. **Next-step recommendations
remained Off despite the guide.** Both documents were visibly selected. The
closing narration and caption explicitly flag that preference for human review;
formal validation success is not proof of complete semantic intent fulfillment.

The earlier recording attempt is also retained privately. Its no-hint Actions
preparation, rename and Undo worked, but the no-hint Responses preparation kept
the existing defaults. Its demonstration assertion failed rather than silently
retrying or fabricating a success. The second take deliberately shows the short
response-guide hint. No product, prompt or framework code was changed to improve
the recording, and the cause of that semantic miss was not established here.

The API describes the fictional dealership demo, not Auto Trader's partner API.
No endpoint was executed. No publication, provisioning, indexing, assignment or
business write occurred. This is not a production-ready integration claim.

## Evidence And Cleanup

- Final take: three explicit turns, three provider attempts, 26,646 reported tokens.
- Both takes together: six attempts, 53,629 tokens, USD 0.053744250 ledger increment.
- Production cumulative ledger afterward: 21 attempts, 181,330 tokens,
  USD 0.177787500. Existing allocation, expiry and limits unchanged.
- Zero browser runtime errors in the selected take.
- Both recording-only Brains archived; four temporary sessions deleted/404.
- Both normal customer recording sessions signed out after capture.
- Zero versions/releases on the selected draft before cleanup.
- Full MP4 decode, chapter contact-sheet inspection and public HTTP 206 seeking pass.
- Public player tested at 390 x 844: playback advances, chapter seek works,
  nonblank video pixels, no horizontal overflow or page errors; old video remains.

Private captures, receipts and snapshots:

`~/.codex/private/loomai-document-first-video-20261110/`

`~/.codex/private/loomai-document-first-video-20261110-take2/`

Read-only accounting evidence:

`~/.codex/private/loomai-authoring-01037/read-only-evidence-546da00/production-after-document-first-video-20261110092045230.private.json`

## Reproduce

The recorder requires an external private credential registry and explicit
`--run`. It creates only a disposable configuration draft and makes three paid
turns; inspect failed takes rather than automatically retrying them.

```sh
AUTHORING_RECORDING_AUTH_REGISTRY=/absolute/private/auth-registry.private.json \
node verification-support/configuration-assistant-01037/video/record-document-first.mjs \
  --run /absolute/new/private/capture-directory
```

Inspect the actual result and adapt chapter timings/narration for each new take.
The checked-in edit plan belongs to the retained second take only:

```sh
node verification-support/configuration-assistant-01037/video/render-video.mjs \
  /absolute/private/loomai-document-first-video-20261110-take2 \
  verification-support/configuration-assistant-01037/video/document-first-edit-plan.json \
  /absolute/new/output/loomai-document-first-assistant.mp4
```

Copy `player-document-first.html` to `player.html` beside both MP4s and their
generated chapter JSON/posters. The shared video-gallery skill serves that
directory without copying large media into Git. Its temporary public tunnel is
unlisted, not authenticated, and requires the host Mac to remain awake/online.
