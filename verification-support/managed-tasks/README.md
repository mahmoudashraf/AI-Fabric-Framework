# Managed Tasks Hosted Qualification

These runners use real hosted services. They are not fixture-response tests or
deployment tools. Execution is explicit and consumes the selected Brain's AI
provider allowance. No inference retry, provider substitution or semantic answer
replacement is performed.

## API Matrix

`qualify-api.mjs` checks an actually `APPLIED_VERIFIED` release, native OCI source,
private identity, exact task grants, customer/environment isolation, malformed
requests, typed JSON/text output, uncertainty and concurrent independent inputs.
`tasks.json` contains the synthetic input-only qualification definitions.

Required environment variables point to **ignored private files**, not credentials
in command-line arguments:

```sh
MANAGED_TASKS_STATE_FILE=/absolute/private/canary.json \
MANAGED_TASKS_OPERATIONS_MODULE=/absolute/private/operations.mjs \
MANAGED_TASKS_AUTH_REGISTRY=/absolute/private/auth-registry.json \
MANAGED_TASKS_FOREIGN_DEPLOYMENT_ID=dep-1234abcd \
node verification-support/managed-tasks/qualify-api.mjs \
  --run production staging /absolute/private/staging-report
```

The private state identifies the Brain, tenant/customer, published version and
exact staging/production target/release. The operations module exports `ssh`;
the auth registry supplies an explicitly selected dedicated fixture identity.
An independently confirmed, real foreign-owned Brain is used only for expected
ownership-denial requests. The runner requires the server to return 403 or a
resource-concealing 404 before
task lookup/execution; it does not alter that Brain or submit inference to it.
The runner requires the qualified implementation's exact runtime source. It
must be reviewed before use with a different release.

Append `--security-only` to repeat only non-inference boundary checks, for
example after both targets exist to prove environment-key isolation. Diagnostic
numeric correlation IDs may be converted to text; an object is used for the
malformed-correlation rejection test. This does not add a semantic validator.

Reports include real outputs and test inputs. Keep those reports outside Git.
`PASS_PENDING_TEXT_REVIEW` is not final semantic acceptance: inspect each text
answer against its supplied policy and record that review separately. Contract
failures and semantic findings remain distinct. A network failure is not
permission to repeat an inference call automatically.

## Browser Journey

`qualify-ui.mjs --help` describes the hosted browser modes. Read/edit modes do
not invoke AI. Execute mode requires an explicit private input plan, published
version/hash and bounded execution authorization. Mutation interception is a
write guard; permitted requests still reach the real hosted backend. It never
fulfills fixture responses.

Use dedicated accounts. Remove/archive only runner-owned temporary definitions
and Brains, and sign out after each proof. Do not edit a customer Brain simply
to qualify the editor. The shared draft has a pre-save conflict check, not an
atomic compare-and-swap contract.

## Results

Current deployment identities, release receipts, pass/failure counts, indexed
resource proof, cleanup and remaining limitations are recorded in
`doc/Productization/future-work/MarketPlace/Products/Strategy/RoadMaps/Implementation/010_68_MANAGED_TASKS_IMPLEMENTATION_RECORD.md`.

Native image health, successful release verification, actual model quality and
security-denial checks are separate evidence. One does not stand in for another.
