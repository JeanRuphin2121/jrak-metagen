import { Link } from 'react-router-dom'
import { Database, Sparkles, ArrowRight, Table2 } from 'lucide-react'
import { getCurrentUser } from '@/lib/auth'
import { useMe, useRuntimeEntities } from '@/lib/runtime'

export default function DashboardPage() {
  const user = getCurrentUser()
  const { data: me } = useMe()
  const { data: entities = [], isLoading } = useRuntimeEntities()

  return (
    <div className="max-w-4xl mx-auto space-y-8">
      <div>
        <h1 className="text-2xl font-bold text-gray-900">Welcome, {user?.username}</h1>
        <p className="text-gray-500 mt-1">
          {isLoading ? 'Loading…' : entities.length === 0
            ? 'No application is available for your roles yet.'
            : <>You can access <strong>{entities.length}</strong> application{entities.length === 1 ? '' : 's'}.</>}
        </p>
      </div>

      {me?.admin && (
        <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
          <Card to="/studio/entities" icon={<Database size={22} className="text-brand-600" />}
            title="Studio" text="Manage entities & fields" tint="bg-brand-100" />
          <Card to="/ai" icon={<Sparkles size={22} className="text-purple-600" />}
            title="AI Assistant" text="Generate a schema from text" tint="bg-purple-100" />
        </div>
      )}

      {entities.length > 0 && (
        <div>
          <h2 className="text-lg font-semibold text-gray-900 mb-3">Applications</h2>
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
            {entities.map((e) => (
              <Card key={e.name} to={`/data/${e.name}`} icon={<Table2 size={22} className="text-gray-600" />}
                title={e.label} text={e.description ?? (e.hasWorkflow ? 'With workflow' : `${e.fields.length} fields`)}
                tint="bg-gray-100" />
            ))}
          </div>
        </div>
      )}
    </div>
  )
}

function Card({ to, icon, title, text, tint }: {
  to: string; icon: React.ReactNode; title: string; text: string; tint: string
}) {
  return (
    <Link to={to}
      className="flex items-center gap-4 rounded-xl border border-gray-200 bg-white p-5 shadow-sm hover:shadow-md transition-shadow">
      <div className={`rounded-lg p-3 ${tint}`}>{icon}</div>
      <div className="flex-1 min-w-0">
        <p className="font-semibold text-gray-900">{title}</p>
        <p className="text-sm text-gray-500 truncate">{text}</p>
      </div>
      <ArrowRight size={18} className="text-gray-400" />
    </Link>
  )
}
