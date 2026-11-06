# Proposal Visual Assets

The founder cover and electric hatchback were generated with the built-in image
tool for the owner-approved November 2026 proposal design. They are illustrative,
not a real recorded video, customer vehicle, showroom visit or endorsement.
The existing blue SUV and support-agent assets are also illustrative.

The founder cover prompt requested a wide photographic showroom scene using
the owner's portrait as the identity reference, with no text, logos or player
controls. The hatchback prompt requested an unbranded fictional white electric
car in a modern dealership setting. Generated PNG originals remain in the
owner's local Codex generated-image directory; these JPEG copies are web assets.

`icons.svg` is mechanically extracted from the installed `@lucide/astro` icon
nodes, with no handwritten icon paths. Its license is `lucide-LICENSE.txt`.
`icons-v2.svg` includes the integration-journey symbols. The renderer references
this versioned filename because the public asset server uses long-lived caching;
adding symbols to an already-cached sprite must not leave returning visitors
with blank icons. Keep old asset names available for older open page versions.
`inter-latin.woff2` is from the installed `@fontsource-variable/inter` package;
its license is `inter-LICENSE.txt`.

These generic assets contain no recipient-specific proposal text or access
codes. Presentation content is delivered only after proposal authorization.
