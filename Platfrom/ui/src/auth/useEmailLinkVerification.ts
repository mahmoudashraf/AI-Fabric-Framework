import { useEffect, useState } from 'react'
import { prepareCustomerIdentity, usePlatformAuth } from './PlatformAuthProvider'
import { customerAuthReturn } from './customerAuthReturn'
import { readEmailLink, verifyEmailLinkOnce } from './emailLinkVerification'

export function useEmailLinkVerification(type: 'email' | 'recovery') {
  const auth = usePlatformAuth()
  const [input] = useState(() => {
    try { return { link: readEmailLink(window.location.search, type), error: null } }
    catch (ex) { return { link: null, error: ex instanceof Error ? ex.message : 'Invalid email link.' } }
  })
  const [result, setResult] = useState<{ pending: boolean; error: string | null; subject: string | null }>({
    pending: !!input.link, error: input.error, subject: null,
  })
  useEffect(() => {
    if (!input.link) return
    const url = new URL(window.location.href)
    url.searchParams.delete('token_hash'); url.searchParams.delete('type')
    window.history.replaceState(null, '', url.pathname + url.search + url.hash)
    if (!auth.supabase || !auth.customerConfig?.projectUrl) {
      if (!auth.customerAuthLoading) setResult({ pending: false, subject: null, error: 'Email verification is unavailable. Please try again later.' })
      return
    }
    prepareCustomerIdentity(customerAuthReturn()?.purpose === 'link')
    let active = true
    verifyEmailLinkOnce(auth.supabase, auth.customerConfig.projectUrl, input.link).then(subject => {
      if (active) setResult({ pending: false, error: null, subject })
    }).catch(ex => { if (active) setResult({ pending: false, error: ex instanceof Error ? ex.message : 'Email verification failed.', subject: null }) })
    return () => { active = false }
  }, [auth.supabase, auth.customerConfig?.projectUrl, auth.customerAuthLoading, input.link])
  return { ...result, present: !!input.link || !!input.error }
}
