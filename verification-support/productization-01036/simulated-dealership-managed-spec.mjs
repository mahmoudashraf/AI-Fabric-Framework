import {readFileSync} from "node:fs";
import {dirname, resolve} from "node:path";
import {fileURLToPath} from "node:url";

const HERE = dirname(fileURLToPath(import.meta.url));
const REVIEWED_TEMPLATE_PATH = resolve(
  HERE,
  "../../Platfrom/backend/src/test/resources/managed-dependency/simulated-dealership-experience-v1.template.json",
);
const REVIEWED_TEMPLATE = JSON.parse(readFileSync(REVIEWED_TEMPLATE_PATH, "utf8"));

export const TEMPLATE_VERSION_ID = "mkv-template-simulated-dealership-experience-v1-5";
export const SPEC_ID = "mds-simulated-dealership-experience-v1-5";
export const SOURCE_COMMIT = "8c5339901faf3dcb8c9edd73c875738461d3330b";
export const POSTGRES_IMAGE =
  "postgres@sha256:721873c34ceb9f8d8fc265984940dc982404c105f19ad51be9fdc5970a6080ea";

export const DEPENDENCY_IMAGES = Object.freeze({
  simulator: Object.freeze({
    serviceName: "simulated-dealership-provider",
    repository: "ghcr.io/mahmoudashraf/loomai-external-vehicle-provider-simulator",
    digest: "sha256:e254e26829149586be15e5c284b6a2fe3c895d4fcc88ca92adf887300f58f794",
    commit: SOURCE_COMMIT,
  }),
  inbox: Object.freeze({
    serviceName: "simulated-dealership-basic-inbox",
    repository: "ghcr.io/mahmoudashraf/loomai-basic-inbox-service",
    digest: "sha256:d74871880950f09674e0558cdfb09bdadcce050e89ae7b33312dad7069ac3759",
    commit: "2035dc36082fefccbfd3a757d38e493fa8f001fc",
  }),
  receiver: Object.freeze({
    serviceName: "simulated-dealership-engagement-receiver",
    repository: "ghcr.io/mahmoudashraf/loomai-dealership-demo-backend",
    digest: "sha256:dc252fedab7b302847fd6aec1e2ea7167d41ea756522fbd58930c6e6bbf1067f",
    commit: SOURCE_COMMIT,
  }),
});

function addCapacity(left, right) {
  return {
    milliCpu: left.milliCpu + right.milliCpu,
    memoryMiB: left.memoryMiB + right.memoryMiB,
    diskMiB: left.diskMiB + right.diskMiB,
    resources: left.resources + right.resources,
  };
}

export function reviewedManagedDependencyCapacity() {
  return REVIEWED_TEMPLATE.services.reduce((total, service) => {
    let serviceTotal = addCapacity(total, service.capacity);
    if (service.database) serviceTotal = addCapacity(serviceTotal, service.database.capacity);
    for (const volume of service.volumes ?? []) {
      serviceTotal = addCapacity(serviceTotal, volume.capacity);
    }
    return serviceTotal;
  }, {milliCpu: 0, memoryMiB: 0, diskMiB: 0, resources: 0});
}

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
