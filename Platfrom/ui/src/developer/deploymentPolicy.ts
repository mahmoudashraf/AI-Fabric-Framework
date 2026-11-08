export type DeploymentPolicy = { allowedEnvironments: string[]; tags: string[] }

export function stagingOnly(policy?: DeploymentPolicy | null) {
  return policy?.allowedEnvironments.length === 1 && policy.allowedEnvironments[0] === 'staging'
}

export function environmentAllowed(policy: DeploymentPolicy | null | undefined, environment: string) {
  return !policy || policy.allowedEnvironments.includes(environment)
}
