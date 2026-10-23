import { useEffect, type ReactNode } from 'react'
import { useBlocker } from 'react-router-dom'
import { Button, Dialog, DialogActions, DialogContent, DialogTitle } from '@mui/material'

export function CustomerSetupNavigationGuard({ children }: { children: ReactNode }) {
  const blocker = useBlocker(({ currentLocation, nextLocation }) => {
    const current = new URLSearchParams(currentLocation.search)
    const next = new URLSearchParams(nextLocation.search)
    current.delete('step'); next.delete('step')
    if (!current.has('deploymentId') && next.has('deploymentId')) next.delete('deploymentId')
    return currentLocation.pathname !== nextLocation.pathname || current.toString() !== next.toString()
  })
  useEffect(() => {
    const preventDiscard = (event: BeforeUnloadEvent) => { event.preventDefault(); event.returnValue = '' }
    window.addEventListener('beforeunload', preventDiscard)
    return () => window.removeEventListener('beforeunload', preventDiscard)
  }, [])
  return <>{children}<Dialog open={blocker.state === 'blocked'} onClose={() => blocker.state === 'blocked' && blocker.reset()} maxWidth="xs" fullWidth>
    <DialogTitle>Leave deployment setup?</DialogTitle>
    <DialogContent>Unsaved form entries will be discarded. Completed server operations are not undone, retried or activated by leaving.</DialogContent>
    <DialogActions><Button onClick={() => blocker.state === 'blocked' && blocker.reset()}>Keep editing</Button><Button onClick={() => blocker.state === 'blocked' && blocker.proceed()}>Leave setup</Button></DialogActions>
  </Dialog></>
}
