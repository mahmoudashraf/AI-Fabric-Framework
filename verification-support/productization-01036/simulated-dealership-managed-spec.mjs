import {readFileSync} from "node:fs";
import {dirname, resolve} from "node:path";
import {fileURLToPath} from "node:url";

const HERE = dirname(fileURLToPath(import.meta.url));
const REVIEWED_TEMPLATE_PATH = resolve(
  HERE,
  "../../Platfrom/backend/src/test/resources/managed-dependency/simulated-dealership-experience-v1.template.json",
);
const REVIEWED_TEMPLATE = JSON.parse(readFileSync(REVIEWED_TEMPLATE_PATH, "utf8"));

export const TEMPLATE_VERSION_ID = "mkv-template-simulated-dealership-experience-v1-1";
export const SPEC_ID = "mds-simulated-dealership-experience-v1-1";
export const SOURCE_COMMIT = "fda8085d0c345e62f750f9041b8fd0760598651c";
export const POSTGRES_IMAGE =
  "postgres@sha256:721873c34ceb9f8d8fc265984940dc982404c105f19ad51be9fdc5970a6080ea";

export const DEPENDENCY_IMAGES = Object.freeze({
  simulator: Object.freeze({
    serviceName: "simulated-dealership-provider",
    repository: "ghcr.io/mahmoudashraf/loomai-external-vehicle-provider-simulator",
    digest: "sha256:6db239c5c4663dee79719e9716ff6429e1f76fbcfd4efc662d5d39e577e52725",
    commit: SOURCE_COMMIT,
  }),
  inbox: Object.freeze({
    serviceName: "simulated-dealership-basic-inbox",
    repository: "ghcr.io/mahmoudashraf/loomai-basic-inbox-service",
    digest: "sha256:10f830dc1e4b1bb5e24722c663b40dac83b07793ab944f3978ac8a6c21e23788",
    commit: SOURCE_COMMIT,
  }),
  receiver: Object.freeze({
    serviceName: "simulated-dealership-engagement-receiver",
    repository: "ghcr.io/mahmoudashraf/loomai-dealership-demo-backend",
    digest: "sha256:84ab2be0c963671d67db1791dfb75187124f059adad1831f6e4985c20b46aa0d",
    commit: SOURCE_COMMIT,
  }),
});

function serviceArtifact(slot, artifacts) {
  const artifact = artifacts[slot];
  if (!artifact?.id || !artifact?.image || !artifact?.commit) {
    throw new Error(`Missing reviewed ${slot} artifact.`);
  }
  return artifact;
}

export function buildManagedDependencySpec(artifacts) {
  const spec = structuredClone(REVIEWED_TEMPLATE);
  for (const service of spec.services) {
    const artifact = serviceArtifact(service.slot, artifacts);
    const expected = DEPENDENCY_IMAGES[service.slot];
    if (!expected || artifact.image !== `${expected.repository}@${expected.digest}`
      || artifact.commit !== expected.commit) {
      throw new Error(`Reviewed ${service.slot} artifact does not match the pinned topology.`);
    }
    service.image = artifact.image;
    service.sourceArtifactId = artifact.id;
    service.sourceCommit = artifact.commit;
  }
  return spec;
}
