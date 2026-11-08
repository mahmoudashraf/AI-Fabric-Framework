# Plugin Library Review Prototype

Owner review artifact for 010.66, 8 November 2026. Not application implementation.

Open `index.html` in a browser. It is self-contained, uses no external assets and
makes no API requests. The top review bar switches between four proposed screens;
it is not a proposed new global Console menu.

1. Choose: recommended/private catalogue, search, demo filter and selected-source drawer.
2. Define: six-step private Data plugin creator and illustrative Source preview.
3. Connect: three-step installation in the Brain's context, with one draft-save command.
4. My plugins: private definitions, explicit versions, saved installations and reuse.

Form values are held in memory only. Sample connection checks/version saves/Brain
saves are labelled simulations. Source preview is an illustrative fragment, not a
complete executable marketplace manifest or a lossless production source editor.
The fictional provider/account/library rows are not live Platform configuration.

## Design Checks

The preview was inspected through the in-app browser using a temporary, loopback-
only single-page server. It exposed no arbitrary filesystem paths, Platform
credentials or provider APIs. Its Content Security Policy disallowed networking.

- All four screens checked in light/dark at 1440x900, 1280x720 and 390x844:
  24 layout checks, no horizontal page overflow; desktop scroll stays in panes.
- All six creator steps checked on desktop without outer page scrolling.
- Name/purpose preview and Source preview retain the changed form values.
- Definition authoring has no misleading environment selector.
- Knowledge-audience selection is distinct from private definition ownership;
  changing it is retained in the illustrative Source preview and final review.
- HTTP/document branches keep their setup inputs separate; document setup has no
  retailer-account or inventory-category requirement.
- Private version -> library -> use in a Brain -> connection/scope/review flow
  inspected. Simulated test/save messages explicitly say no live action occurred.

Screenshots and layout measurements are local generated output in:
`output/plugin-library-proposal-20261108/` at the repository root.

These checks do not qualify backend authorization, manifest execution, numeric
precision, arbitrary customer APIs or a production release. Required real changes
and focused acceptance criteria are in the 010.66 proposal.
