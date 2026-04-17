import { useQuery } from '@tanstack/react-query'
import { Link } from 'react-router-dom'
import { Database, Sparkles, ArrowRight } from 'lucide-react'
import api from '@/lib/api'
import { MetaEntitySummary } from '@/types/meta'
import { getCurrentUser } from '@/lib/auth'

export default function DashboardPage() {
  const user = getCurrentUser()

  const { data: entities = [] } = useQuery<MetaEntitySummary[]>({
    queryKey: ['entities'],
    queryFn: () => api.get('/studio/entities').then((r) => r.data),
  })

  return (
    <div className="max-w-4xl mx-auto space-y-8">
      <div>
        <h1 className="text-2xl font-bold text-gray-900">
          Welcome back, {user?.username}
        </h1>
        <p className="text-gray-500 mt-1">
          You have <strong>{entities.length}</strong> entit{entities.length === 1 ? 'y' : 'ies'} configured.
        </p>
      </div>

      {/* Quick-access cards */}
      <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
        <Link
          to="/studio/entities"
          className="flex items-center gap-4 rounded-xl border border-gray-200 bg-white p-5 shadow-sm hover:shadow-md transition-shadow"
        >
          <div className="rounded-lg bg-brand-100 p-3">
            <Database size={22} className="text-brand-600" />
          </div>
          <div className="flex-1">
            <p className="font-semibold text-gray-900">Studio</p>
            <p className="text-sm text-gray-500">Manage entities & fields</p>
          </div>
          <ArrowRight size={18} className="text-gray-400" />
        </Link>

        <Link
          to="/ai"
          className="flex items-center gap-4 rounded-xl border border-gray-200 bg-white p-5 shadow-sm hover:shadow-md transition-shadow"
        >
          <div className="rounded-lg bg-purple-100 p-3">
            <Sparkles size={22} className="text-purple-600" />
          </div>
          <div className="flex-1">
            <p className="font-semibold text-gray-900">AI Assistant</p>
            <p className="text-sm text-gray-500">Generate schema from text</p>
          </div>
          <ArrowRight size={18} className="text-gray-400" />
        </Link>
      </div>

      {/* Recent entities */}
      {entities.length > 0 && (
        <div>
          <h2 className="text-lg font-semibold text-gray-900 mb-3">Entities</h2>
          <div className="overflow-hidden rounded-xl border border-gray-200 bg-white shadow-sm">
            <table className="min-w-full divide-y divide-gray-100 text-sm">
              <thead className="bg-gray-50">
                <tr>
                  <th className="px-4 py-3 text-left font-medium text-gray-500">Name</th>
                  <th className="px-4 py-3 text-left font-medium text-gray-500">Label</th>
                  <th className="px-4 py-3 text-left font-medium text-gray-500">Table</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-100">
                {entities.map((e) => (
                  <tr key={e.id} className="hover:bg-gray-50">
                    <td className="px-4 py-3">
                      <Link to={`/studio/entities/${e.name}`} className="font-medium text-brand-600 hover:underline">
                        {e.name}
                      </Link>
                    </td>
                    <td className="px-4 py-3 text-gray-600">{e.label}</td>
                    <td className="px-4 py-3 text-gray-400 font-mono text-xs">{e.tableName}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      )}
    </div>
  )
}
