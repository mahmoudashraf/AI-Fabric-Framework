import { useEffect } from 'react'
import { useLocation, useParams } from 'react-router-dom'
import { Alert, Box, Typography } from '@mui/material'
import { usePlatformAuth } from '../auth/PlatformAuthProvider'
import { isCustomerSession } from '../auth/customerSession'
import { useCustomerSupport } from '../components/customer/CustomerSupportContext'

export function CustomerSupportPage({ view = 'assistant' }: { view?: 'assistant' | 'requests' }) {
  const auth = usePlatformAuth()
  if (!isCustomerSession(auth.session)) return <Alert severity="error">Sign in to your customer account to request support.</Alert>
  return <SupportRoute view={view} />
}

function SupportRoute({ view }: { view: 'assistant' | 'requests' }) {
  const { caseId } = useParams()
  const location = useLocation()
  const { setMinimized, openRequest, showRequests, openRequestForm } = useCustomerSupport()
  useEffect(() => {
    if (new URLSearchParams(location.search).get('new') === '1') openRequestForm()
    else if (caseId) openRequest(caseId)
    else if (view === 'requests') showRequests()
    else setMinimized(false)
  }, [caseId, view, location.search, setMinimized, openRequest, showRequests, openRequestForm])
  return <Box><Typography variant="overline" color="secondary.main">LoomAI support</Typography>
    <Typography variant="h4">{view === 'requests' ? 'My requests' : 'Integration support'}</Typography></Box>
}
