import { Navigate, Route, Routes } from 'react-router-dom'
import { AppShell } from './layout/AppShell'
import { ActionsPage } from './pages/ActionsPage'
import { DeploymentsPage } from './pages/DeploymentsPage'
import { DiagnosticsPage } from './pages/DiagnosticsPage'
import { KnowledgePage } from './pages/KnowledgePage'
import { ProvidersPage } from './pages/ProvidersPage'
import { RevisionsPage } from './pages/RevisionsPage'
import { SecurityPage } from './pages/SecurityPage'
import { VerificationPage } from './pages/VerificationPage'

export default function App() {
  return (
    <AppShell>
      <Routes>
        <Route path="/" element={<Navigate to="/deployments" replace />} />
        <Route path="/deployments" element={<DeploymentsPage />} />
        <Route path="/actions" element={<ActionsPage />} />
        <Route path="/knowledge" element={<KnowledgePage />} />
        <Route path="/providers" element={<ProvidersPage />} />
        <Route path="/security" element={<SecurityPage />} />
        <Route path="/verification" element={<VerificationPage />} />
        <Route path="/revisions" element={<RevisionsPage />} />
        <Route path="/diagnostics" element={<DiagnosticsPage />} />
      </Routes>
    </AppShell>
  )
}

