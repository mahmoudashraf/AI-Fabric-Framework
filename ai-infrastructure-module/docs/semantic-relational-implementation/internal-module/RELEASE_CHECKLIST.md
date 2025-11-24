# Release Checklist

Use this checklist when preparing a tagged release of the relationship query module.

## 1. Versioning
- [ ] Update `<version>` in `ai-infrastructure-relationship-query/pom.xml`.
- [ ] Update the parent BOM if necessary.

## 2. Changelog
- [ ] Add an entry to `CHANGELOG.md` summarizing features, fixes, and tests.
- [ ] Note any breaking changes (currently none).

## 3. Verification
- [ ] `mvn -pl ai-infrastructure-relationship-query test`
- [ ] `mvn -pl ai-infrastructure-relationship-query -Dtest=...PerformanceTest test` (enable when perf harness is reinstated).
- [ ] `mvn -pl ai-infrastructure-relationship-query javadoc:javadoc`

## 4. Documentation
- [ ] Ensure Phase 5 docs (`QUICK_START.md`, `CONFIGURATION_GUIDE.md`, etc.) reflect latest features.
- [ ] Update `USE_CASE_EXAMPLES.md` if new scenarios were added.
- [ ] Publish generated JavaDoc artifacts or link them in the internal docs portal.

## 5. Release Notes
Capture at minimum:
- Overview / highlights
- Notable configuration changes
- Testing summary
- Known issues & mitigations

## 6. Tag & Distribution
- [ ] Tag repo: `git tag v1.0.0` (adjust semver).
- [ ] Push tag to origin.
- [ ] Publish artifacts to the Maven repository (internal Nexus/Artifactory).

## 7. Post-Release
- [ ] Monitor metrics dashboards (query latency, fallback counts).
- [ ] Keep Lucene index health alerts configured.
- [ ] Schedule follow-up review for performance harness re-enablement if it was skipped.
