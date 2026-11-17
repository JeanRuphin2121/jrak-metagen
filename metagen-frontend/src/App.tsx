import { Navigate, Route, Routes } from 'react-router-dom'
import { isAuthenticated } from '@/lib/auth'
import Layout from '@/components/layout/Layout'
import LoginPage from '@/pages/LoginPage'
import DashboardPage from '@/pages/DashboardPage'
import EntityListPage from '@/pages/studio/EntityListPage'
import EntityDetailPage from '@/pages/studio/EntityDetailPage'
import DataListPage from '@/pages/data/DataListPage'
import DataFormPage from '@/pages/data/DataFormPage'
import RecordDetailPage from '@/pages/data/RecordDetailPage'
import ConfigPage from '@/pages/ConfigPage'
import { useMe } from '@/lib/runtime'
import AiAssistantPage from '@/pages/ai/AiAssistantPage'
import UserListPage from '@/pages/admin/UserListPage'

function RequireAdmin({ children }: { children: React.ReactNode }) {
  const { data: me, isLoading } = useMe()
  if (isLoading) return null
  return me?.admin ? <>{children}</> : <Navigate to="/dashboard" replace />
}

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

        {/* Studio — schema design */}
        <Route path="studio/entities" element={<RequireAdmin><EntityListPage /></RequireAdmin>} />
        <Route path="studio/entities/:name" element={<RequireAdmin><EntityDetailPage /></RequireAdmin>} />
        <Route path="config" element={<RequireAdmin><ConfigPage /></RequireAdmin>} />

        {/* Data browser */}
        <Route path="data/:entityName" element={<DataListPage />} />
        <Route path="data/:entityName/new" element={<DataFormPage />} />
        <Route path="data/:entityName/:id" element={<RecordDetailPage />} />
        <Route path="data/:entityName/:id/edit" element={<DataFormPage />} />

        {/* AI assistant */}
        <Route path="ai" element={<RequireAdmin><AiAssistantPage /></RequireAdmin>} />

        {/* Admin */}
        <Route path="admin/users" element={<RequireAdmin><UserListPage /></RequireAdmin>} />
      </Route>

      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  )
}
