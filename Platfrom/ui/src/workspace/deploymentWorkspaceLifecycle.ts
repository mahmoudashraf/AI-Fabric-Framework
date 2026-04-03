import type { AlertColor, ChipProps } from '@mui/material'
import type { DeploymentWorkspaceLifecycleSummary } from '../api/platformApi'

export type DeploymentWorkspaceEditorBufferState = {
  dirty: boolean
  label: string
  description: string
}

export type DeploymentWorkspaceStateDisplay = {
  label: string
  description: string
  color: ChipProps['color']
  severity: AlertColor
}

export function savedDraftStateDisplay(
  lifecycle: DeploymentWorkspaceLifecycleSummary,
): DeploymentWorkspaceStateDisplay {
  switch (lifecycle.savedDraftState) {
    case 'NEVER_PUBLISHED':
      return {
        label: 'Saved draft only',
        description: 'Only the editable draft exists right now. Publish a version before any apply can happen.',
        color: 'warning',
        severity: 'warning',
      }
    case 'MATCHES_LATEST_PUBLISHED':
      return {
        label: 'Saved draft matches latest publish',
        description: 'The saved draft is aligned with the most recently published version.',
        color: 'success',
        severity: 'success',
      }
    case 'UNPUBLISHED_CHANGES':
      return {
        label: 'Saved draft has unpublished changes',
        description: 'The saved draft is ahead of the latest published version and needs publish before apply.',
        color: 'warning',
        severity: 'warning',
      }
    default:
      return {
        label: lifecycle.savedDraftState,
        description: lifecycle.summaryMessage,
        color: 'default',
        severity: 'info',
      }
  }
}

export function liveStateDisplay(
  lifecycle: DeploymentWorkspaceLifecycleSummary,
): DeploymentWorkspaceStateDisplay {
  switch (lifecycle.liveState) {
    case 'NOT_PUBLISHED':
      return {
        label: 'No live version',
        description: 'Nothing has been published or applied yet.',
        color: 'warning',
        severity: 'warning',
      }
    case 'LATEST_PUBLISHED_NOT_APPLIED':
      return {
        label: 'Latest publish not applied',
        description: 'A published version exists, but it is not live yet.',
        color: 'warning',
        severity: 'warning',
      }
    case 'LATEST_APPLY_IN_PROGRESS':
      return {
        label: 'Apply in progress',
        description: 'The latest published version is currently rolling out.',
        color: 'secondary',
        severity: 'info',
      }
    case 'LATEST_APPLY_FAILED':
      return {
        label: 'Latest apply failed',
        description: 'The newest published version did not reach a healthy live state.',
        color: 'error',
        severity: 'error',
      }
    case 'LIVE_MATCHES_LATEST_PUBLISHED':
      return {
        label: 'Live matches latest publish',
        description: 'The currently live deployment is aligned with the newest published version.',
        color: 'success',
        severity: 'success',
      }
    case 'LIVE_OUTDATED':
      return {
        label: 'Live is behind latest publish',
        description: 'The live deployment is still on an older applied version.',
        color: 'warning',
        severity: 'warning',
      }
    default:
      return {
        label: lifecycle.liveState,
        description: lifecycle.summaryMessage,
        color: 'default',
        severity: 'info',
      }
  }
}

export function editorBufferStateDisplay(
  state: DeploymentWorkspaceEditorBufferState | null,
): DeploymentWorkspaceStateDisplay | null {
  if (!state) {
    return null
  }
  return {
    label: state.dirty ? `${state.label}: unsaved changes` : `${state.label}: saved`,
    description: state.description,
    color: state.dirty ? 'warning' : 'success',
    severity: state.dirty ? 'warning' : 'info',
  }
}
