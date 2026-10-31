import { useEffect, useState } from 'react'
import { BrowserRouter, createBrowserRouter, RouterProvider } from 'react-router-dom'
import { usePlatformAuth } from '../auth/PlatformAuthProvider'
import App from '../App'

function CustomerRouting() {
  const [router, setRouter] = useState<ReturnType<typeof createBrowserRouter> | null>(null)
  useEffect(() => {
    const next = createBrowserRouter([{ path: '*', element: <App /> }])
    setRouter(next)
    return () => next.dispose()
  }, [])
  return router ? <RouterProvider router={router} /> : null
}

export function PlatformRouting() {
  const { session } = usePlatformAuth()
  // Keep one-use auth exchanges mounted while the account role is loading.
  const identityProtocolRoute = ['/auth/desk/authorize', '/auth/desk/sign-in', '/auth/callback', '/auth/recovery', '/support/team-intake'].includes(window.location.pathname)
    || window.location.pathname.startsWith('/auth/desk/sign-in/')
  return session?.role === 'CUSTOMER_ADMIN' && !identityProtocolRoute
    ? <CustomerRouting />
    : <BrowserRouter useTransitions={false}><App /></BrowserRouter>
}
