import { Navigate, Route, Routes } from 'react-router-dom'
import { isAuthenticated } from '@/lib/auth'
import Layout from '@/components/layout/Layout'
import LoginPage from '@/pages/LoginPage'
import DashboardPage from '@/pages/DashboardPage'
import EntityListPage from '@/pages/studio/EntityListPage'
import EntityDetailPage from '@/pages/studio/EntityDetailPage'
import AiAssistantPage from '@/pages/ai/AiAssistantPage'

function RequireAuth({ children }: { children: React.ReactNode }) {
  return isAuthenticated() ? <>{children}</> : <Navigate to="/login" replace />
}

export default function App() {
  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />

      <Route
        path="/"
        element={
          <RequireAuth>
            <Layout />
          </RequireAuth>
        }
      >
        <Route index element={<Navigate to="/dashboard" replace />} />
        <Route path="dashboard" element={<DashboardPage />} />
        <Route path="studio/entities" element={<EntityListPage />} />
        <Route path="studio/entities/:name" element={<EntityDetailPage />} />
        <Route path="ai" element={<AiAssistantPage />} />
      </Route>

      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  )
}
