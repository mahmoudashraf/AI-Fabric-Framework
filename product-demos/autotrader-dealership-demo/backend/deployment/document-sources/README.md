# Northfield Public Document Knowledge

These files are fictional, public-safe dealership documents for the Northfield
Motor House demonstration. They exercise LoomAI Document Knowledge Operations;
they are not legal terms, real dealership commitments, or Auto Trader content.

When this fixture set is installed, it is indexed into the `document` vector
space with:

- dataset `document-knowledge`;
- visibility `tenant`;
- `publicationStatus=PUBLIC_APPROVED`; and
- a source-specific `sourceCategory`.

Production dealerships must replace these fixtures with their own approved
documents in customer-owned S3-compatible storage. Internal staff procedures,
credentials, personal data, commercial secrets, and unapproved drafts must not
be registered in the public package.

This folder is not evidence of the current hosted source contents. The current
S3-backed canary originally used three shorter documents instead. Its proposed
replacement is the separate [customer-storage policy pack](../customer-storage-policy-pack/README.md).
The sets differ in demonstration terms; do not index them together.

