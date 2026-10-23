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
  return session?.role === 'CUSTOMER_ADMIN'
    ? <CustomerRouting />
    : <BrowserRouter useTransitions={false}><App /></BrowserRouter>
}
