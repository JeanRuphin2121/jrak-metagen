import { useState } from 'react'
import { Link } from 'react-router-dom'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { Plus, Database, ChevronRight } from 'lucide-react'
import api from '@/lib/api'
import { MetaEntitySummary, CreateEntityRequest } from '@/types/meta'

export default function EntityListPage() {
  const qc = useQueryClient()
  const [showForm, setShowForm] = useState(false)
  const [form, setForm] = useState<CreateEntityRequest>({ name: '', label: '' })
  const [formError, setFormError] = useState<string | null>(null)

  const { data: entities = [], isLoading } = useQuery<MetaEntitySummary[]>({
    queryKey: ['entities'],
    queryFn: () => api.get('/studio/entities').then((r) => r.data),
  })

  const createEntity = useMutation({
    mutationFn: (req: CreateEntityRequest) => api.post('/studio/entities', req),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['entities'] })
      setShowForm(false)
      setForm({ name: '', label: '' })
      setFormError(null)
    },
    onError: (err: unknown) => {
      const msg = (err as { response?: { data?: { detail?: string } } })?.response?.data?.detail
      setFormError(msg ?? 'Failed to create entity')
    },
  })

  function handleCreate(e: React.FormEvent) {
    e.preventDefault()
    createEntity.mutate(form)
  }

  return (
    <div className="max-w-3xl mx-auto space-y-6">
      <div className="flex items-center justify-between">
        <h1 className="text-2xl font-bold text-gray-900">Entities</h1>
        <button
          onClick={() => setShowForm((v) => !v)}
          className="flex items-center gap-2 rounded-md bg-brand-600 px-4 py-2 text-sm font-medium text-white hover:bg-brand-700 transition-colors"
        >
          <Plus size={16} />
          New entity
        </button>
      </div>

      {/* Create form */}
      {showForm && (
        <form
          onSubmit={handleCreate}
          className="rounded-xl border border-brand-200 bg-brand-50 p-5 space-y-4"
        >
          <h2 className="font-semibold text-brand-800">New entity</h2>
          <div className="grid grid-cols-2 gap-4">
            <div>
              <label className="block text-xs font-medium text-gray-600 mb-1">Name <span className="text-gray-400">(snake_case)</span></label>
              <input
                required
                placeholder="supplier"
                value={form.name}
                onChange={(e) => setForm({ ...form, name: e.target.value })}
                className="w-full rounded border border-gray-300 px-3 py-1.5 text-sm focus:outline-none focus:ring-2 focus:ring-brand-500"
              />
            </div>
            <div>
              <label className="block text-xs font-medium text-gray-600 mb-1">Label</label>
              <input
                required
                placeholder="Supplier"
                value={form.label}
                onChange={(e) => setForm({ ...form, label: e.target.value })}
                className="w-full rounded border border-gray-300 px-3 py-1.5 text-sm focus:outline-none focus:ring-2 focus:ring-brand-500"
              />
            </div>
          </div>
          {formError && <p className="text-sm text-red-600">{formError}</p>}
          <div className="flex gap-3">
            <button
              type="submit"
              disabled={createEntity.isPending}
              className="rounded-md bg-brand-600 px-4 py-1.5 text-sm font-medium text-white hover:bg-brand-700 disabled:opacity-50"
            >
              {createEntity.isPending ? 'Creating…' : 'Create'}
            </button>
            <button type="button" onClick={() => setShowForm(false)} className="text-sm text-gray-500 hover:text-gray-800">
              Cancel
            </button>
          </div>
        </form>
      )}

      {/* Entity list */}
      {isLoading ? (
        <p className="text-gray-500 text-sm">Loading…</p>
      ) : entities.length === 0 ? (
        <div className="rounded-xl border-2 border-dashed border-gray-200 py-16 text-center">
          <Database size={32} className="mx-auto text-gray-300 mb-3" />
          <p className="text-gray-500">No entities yet. Create your first one.</p>
        </div>
      ) : (
        <div className="overflow-hidden rounded-xl border border-gray-200 bg-white shadow-sm divide-y divide-gray-100">
          {entities.map((e) => (
            <Link
              key={e.id}
              to={`/studio/entities/${e.name}`}
              className="flex items-center px-5 py-4 hover:bg-gray-50 transition-colors"
            >
              <Database size={18} className="text-brand-500 mr-3 shrink-0" />
              <div className="flex-1 min-w-0">
                <p className="font-medium text-gray-900">{e.label}</p>
                <p className="text-xs text-gray-400 font-mono">{e.name} · {e.tableName}</p>
              </div>
              <ChevronRight size={16} className="text-gray-400" />
            </Link>
          ))}
        </div>
      )}
    </div>
  )
}
