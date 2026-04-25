import CheckCircleOutlineIcon from '@mui/icons-material/CheckCircleOutline'
import BuildOutlinedIcon from '@mui/icons-material/BuildOutlined'
import BlockOutlinedIcon from '@mui/icons-material/BlockOutlined'
import ErrorOutlineIcon from '@mui/icons-material/ErrorOutline'
import HourglassEmptyOutlinedIcon from '@mui/icons-material/HourglassEmptyOutlined'
import ShieldOutlinedIcon from '@mui/icons-material/ShieldOutlined'
import CancelOutlinedIcon from '@mui/icons-material/CancelOutlined'
import ReportProblemOutlinedIcon from '@mui/icons-material/ReportProblemOutlined'

export type PartnerStatus =
  | 'ready'
  | 'needsSetup'
  | 'blocked'
  | 'verifyFailed'
  | 'waitingMerchant'
  | 'waitingOperator'
  | 'revoked'
  | 'escalated'

export const statusTokens: Record<PartnerStatus, { label: string; main: string; bg: string; fg: string; icon: typeof CheckCircleOutlineIcon }> = {
  ready: { label: 'Ready', main: '#16A34A', bg: '#ECFDF5', fg: '#166534', icon: CheckCircleOutlineIcon },
  needsSetup: { label: 'Needs setup', main: '#2563EB', bg: '#EFF6FF', fg: '#1E40AF', icon: BuildOutlinedIcon },
  blocked: { label: 'Blocked', main: '#DC2626', bg: '#FEF2F2', fg: '#991B1B', icon: BlockOutlinedIcon },
  verifyFailed: { label: 'Verification failed', main: '#EA580C', bg: '#FFF7ED', fg: '#9A3412', icon: ErrorOutlineIcon },
  waitingMerchant: { label: 'Waiting on merchant', main: '#A16207', bg: '#FEFCE8', fg: '#854D0E', icon: HourglassEmptyOutlinedIcon },
  waitingOperator: { label: 'Waiting on operator', main: '#7C3AED', bg: '#F5F3FF', fg: '#5B21B6', icon: ShieldOutlinedIcon },
  revoked: { label: 'Revoked', main: '#475569', bg: '#F1F5F9', fg: '#334155', icon: CancelOutlinedIcon },
  escalated: { label: 'Escalated', main: '#BE123C', bg: '#FFF1F2', fg: '#9F1239', icon: ReportProblemOutlinedIcon },
}

export function statusFromWire(value: string | undefined): PartnerStatus {
  switch ((value ?? '').toUpperCase()) {
    case 'READY':
    case 'ACTIVE':
    case 'APPROVED':
    case 'PASSED':
    case 'SUCCESS':
    case 'APPLIED':
      return 'ready'
    case 'BLOCKED':
      return 'blocked'
    case 'VERIFY_FAILED':
    case 'VERIFICATION_FAILED':
    case 'FAILED':
      return 'verifyFailed'
    case 'WAITING_ON_MERCHANT':
    case 'DRAFT':
      return 'waitingMerchant'
    case 'WAITING_ON_OPERATOR':
      return 'waitingOperator'
    case 'REVOKED':
    case 'SUSPENDED':
    case 'CLOSED':
      return 'revoked'
    case 'ESCALATED':
    case 'OPEN':
      return 'escalated'
    default:
      return 'needsSetup'
  }
}
