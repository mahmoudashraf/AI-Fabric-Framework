import { createContext, useContext, useState, type ReactNode } from 'react'
import { Box, Tab, Tabs } from '@mui/material'

export type CustomerDetailSection = 'overview' | 'configuration' | 'advanced'
export type CustomerEditorSection = Exclude<CustomerDetailSection, 'overview'>
const EditorSection = createContext<{ section: CustomerEditorSection; setSection: (section: CustomerEditorSection) => void; notices?: ReactNode } | null>(null)

function readSection(key: string, fallback: CustomerDetailSection): CustomerDetailSection {
  try {
    const value = localStorage.getItem(`loomai-customer-detail:${key}`)
    if (value === 'overview' || value === 'configuration' || value === 'advanced') return value
  } catch { /* View preference is optional, never authority. */ }
  return fallback
}

export function CustomerDetailSections({ preferenceKey, overview, configuration, advanced }: {
  preferenceKey: string
  overview: ReactNode
  configuration: ReactNode
  advanced: ReactNode
}) {
  const [section, setSection] = useState(() => readSection(preferenceKey, 'overview'))
  return <>
    <Tabs value={section} onChange={(_, next: CustomerDetailSection) => {
      setSection(next)
      try { localStorage.setItem(`loomai-customer-detail:${preferenceKey}`, next) } catch { /* Optional preference. */ }
    }} variant="scrollable" scrollButtons="auto" aria-label="Resource detail sections" sx={{ borderBottom: 1, borderColor: 'divider' }}>
      <Tab value="overview" label="Overview" /><Tab value="configuration" label="Configuration" /><Tab value="advanced" label="Advanced" />
    </Tabs>
    {([['overview', overview], ['configuration', configuration], ['advanced', advanced]] as const).map(([id, content]) =>
      <Box key={id} role="tabpanel" aria-label={`${id} resource details`} hidden={section !== id} sx={{ pt: 2 }}>{content}</Box>)}
  </>
}

export function CustomerEditorSections({ scopeKey, initialSection = 'configuration', children, notices }: {
  scopeKey: string
  initialSection?: CustomerEditorSection
  children: ReactNode
  notices?: ReactNode
}) {
  return <EditorSections key={scopeKey} initialSection={initialSection} notices={notices}>{children}</EditorSections>
}

function EditorSections({ initialSection, children, notices }: { initialSection: CustomerEditorSection; children: ReactNode; notices?: ReactNode }) {
  const [section, setSection] = useState<CustomerEditorSection>(initialSection)
  return <EditorSection.Provider value={{ section, setSection, notices }}>
    <CustomerEditorNotices />
    <CustomerEditorDepthSwitch />
    {children}
  </EditorSection.Provider>
}

export function CustomerEditorDepthSwitch({ label = 'Configuration depth' }: { label?: string }) {
  const depth = useContext(EditorSection)
  if (!depth) return null
  return <Tabs value={depth.section} onChange={(_, next: CustomerEditorSection) => depth.setSection(next)} aria-label={label} sx={{ mb: 3, borderBottom: 1, borderColor: 'divider' }}>
    <Tab value="configuration" label="Configuration" /><Tab value="advanced" label="Advanced" />
  </Tabs>
}

export function useCustomerEditorSection() { return useContext(EditorSection)?.section ?? null }

export function CustomerEditorNotices() { return useContext(EditorSection)?.notices ?? null }

export function CustomerAdvancedSection({ children }: { children: ReactNode }) {
  const section = useCustomerEditorSection()
  return section == null ? <>{children}</> : <Box hidden={section === 'configuration'}>{children}</Box>
}
