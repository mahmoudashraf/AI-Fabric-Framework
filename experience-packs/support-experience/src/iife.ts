import { code, version, mountInstallation } from './index'
declare global { interface Window { LoomAIWorkspace?: { registerExperiencePack(value: unknown): void } } }
if (!window.LoomAIWorkspace) throw new Error('The Platform AI Workspace installer is required.')
window.LoomAIWorkspace.registerExperiencePack({ code, version, isolation: 'frame', mount: mountInstallation })
