// CLI planning regressions must never reach a transport, even if ambient auth variables exist.
globalThis.fetch = () => { throw new Error("OFFLINE_PUBLICATION_TEST_FORBIDS_NETWORK"); };
