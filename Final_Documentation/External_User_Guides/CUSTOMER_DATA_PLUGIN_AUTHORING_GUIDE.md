# Create And Reuse A Data Plugin

## Two Different Jobs

A **plugin definition** describes a reusable source contract. A **Brain
installation** fills in that contract's connection inputs for one Smart Brain.
Creating either does not update a running environment. Existing Brain Updates
still publishes, applies and verifies a release.

These are the existing Platform `DATA` plugins. The private library does not
introduce a new plugin type, connector execution engine or customer database.

## Connect An Existing Source

1. Open the Smart Brain, then Knowledge and Add knowledge.
2. Choose a recommended source, your private plugin, or another catalogue entry.
   Demo sources are hidden until Show demo sources is selected.
3. Select Set up for this Brain. Choose the exact reusable version, enter its
   connection references, and complete Information & access.
4. Review the version, inputs and scope, then Add to Brain draft. This reuses the
   existing installation and draft compiler; it is not a provider test.
5. Review Brain changes, publish and apply to the intended environment, then
   use the existing source discovery, document approval, sync and indexing tools.

Document storage uses the existing hosted or customer-owned S3-compatible
connection. Its runtime binding is environment-specific. Ordinary installation
configuration is Brain-wide; do not assume it becomes a separate staging or
production credential merely by switching the environment selector.

## Define A Reusable Source

Open My plugins from Add knowledge or AI capabilities. Select Create a data
plugin. This library belongs to your customer workspace, not to an environment.

The editor has six tasks:

1. **Name & purpose:** give the source a clear name and explain what it adds.
2. **Source contract:** use supported HTTP JSON or bound document storage. SQL is
   shown as an unavailable advanced contract until a qualified customer-owned
   adapter exists. New authentication/API protocols still need connector
   implementation and qualification; the editor cannot make them executable.
3. **Records & content:** map stable IDs, searchable text and metadata using the
   existing structured mappings. Ownership is stamped by trusted installation
   context, not by an arbitrary source field.
4. **Freshness & access:** define supported sync behavior and the intended
   knowledge audience. Private definition visibility is different from who may
   read the indexed content. Public documents still require approval.
5. **Setup inputs:** define the small form future installers complete. Secrets
   are references, never raw credentials embedded in a reusable manifest.
6. **Review & version:** check the configuration and save an immutable reusable
   version. A schema check does not claim that the provider is reachable, its
   credentials work, or the records have been indexed.

Save draft retains work without changing an installed Brain. Source provides
the complete manifest for advanced edits, import and export. Guided edits retain
unrelated supported fields, exact numbers, explicit nulls and array order.
Invalid source remains visible until corrected; it is not silently replaced.

## Reuse And Update

Select a usable version in My plugins, then Use in a Brain. Install it separately
with that Brain's authorized connections. Updating a reusable definition does
not silently upgrade installations. Select the new version in the Brain's
connection, review the draft and use the normal release flow.

The library distinguishes saved installations from recorded applied releases.
Neither is evidence that today's source sync or AI answer quality passed.
Archive prevents new selection; pinned versions and release history remain.

## Access And Conflicts

Only the current customer workspace and explicitly authorized staff can manage
its private definitions. Advanced view is not a permission grant. Shared public
marketplace publishing retains its existing reviewed publisher workflow.

If a save reports a revision conflict, your local edits are retained. Compare
or export them before loading the current server draft. A stale tab cannot
overwrite a newer draft or installation without the server's revision check.
