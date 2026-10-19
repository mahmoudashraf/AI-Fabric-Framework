#!/usr/bin/env node

import {resolve} from "node:path";
import {pathToFileURL} from "node:url";
import {requireCustomerOfferPublicationSupport} from "./simulated-dealership-offer.mjs";

// The previous bundled-business publisher is intentionally retired. New publication
// needs full real V196 customer binding/compiler and target-local SQL rotation proof.
// The local Spring/PostgreSQL bootstrap gate alone is not this workflow proof.
// API presence or caller-supplied success flags cannot authorize publication.
export async function publishSimulatedDealershipOffer() {
  requireCustomerOfferPublicationSupport();
}

if (process.argv[1] && import.meta.url === pathToFileURL(resolve(process.argv[1])).href) {
  publishSimulatedDealershipOffer().catch(error => {
    process.stderr.write(`${error.message}\n`);
    process.exitCode = 1;
  });
}
