import { Navigate, Route, Routes } from 'react-router-dom'
import { isAuthenticated } from '@/lib/auth'
import Layout from '@/components/layout/Layout'
import LoginPage from '@/pages/LoginPage'
import DashboardPage from '@/pages/DashboardPage'
import EntityListPage from '@/pages/studio/EntityListPage'
import EntityDetailPage from '@/pages/studio/EntityDetailPage'
import DataListPage from '@/pages/data/DataListPage'
import DataFormPage from '@/pages/data/DataFormPage'
import AiAssistantPage from '@/pages/ai/AiAssistantPage'
import UserListPage from '@/pages/admin/UserListPage'

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
        <Route path="studio/entities" element={<EntityListPage />} />
        <Route path="studio/entities/:name" element={<EntityDetailPage />} />

        {/* Data browser */}
        <Route path="data/:entityName" element={<DataListPage />} />
        <Route path="data/:entityName/new" element={<DataFormPage />} />
        <Route path="data/:entityName/:id/edit" element={<DataFormPage />} />

        {/* AI assistant */}
        <Route path="ai" element={<AiAssistantPage />} />

        {/* Admin */}
        <Route path="admin/users" element={<UserListPage />} />
      </Route>

      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  )
}
