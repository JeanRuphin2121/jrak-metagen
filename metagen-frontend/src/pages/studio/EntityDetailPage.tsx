import { useState } from 'react'
import { useParams, Link } from 'react-router-dom'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { ArrowLeft, Plus, Table2 } from 'lucide-react'
import api from '@/lib/api'
import { MetaEntity, AddFieldRequest, MetaFieldType } from '@/types/meta'
import { errorMessage } from '@/lib/runtime'

const FIELD_TYPES: MetaFieldType[] = [
  'STRING', 'TEXT', 'INTEGER', 'LONG', 'DECIMAL', 'BOOLEAN',
  'DATE', 'DATETIME', 'EMAIL', 'URL', 'PHONE', 'ENUM', 'MULTI_ENUM', 'REFERENCE', 'JSON',
]

export default function EntityDetailPage() {
  const { name } = useParams<{ name: string }>()
  const qc = useQueryClient()
  const [showFieldForm, setShowFieldForm] = useState(false)
  const [optionValues, setOptionValues] = useState('')
  const [targetEntity, setTargetEntity] = useState('')
  const [fieldForm, setFieldForm] = useState<AddFieldRequest>({
    name: '', label: '', type: 'STRING',
  })

  const { data: entity, isLoading } = useQuery<MetaEntity>({
    queryKey: ['entity', name],
    queryFn: () => api.get(`/studio/entities/${name}`).then((r) => r.data),
    enabled: !!name,
  })

  const addField = useMutation({
    mutationFn: (req: AddFieldRequest) =>
      api.post(`/studio/entities/${name}/fields`, req),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['entity', name] })
      setShowFieldForm(false)
      setFieldForm({ name: '', label: '', type: 'STRING' })
      setOptionValues(''); setTargetEntity('')
    },
  })

  const deployEntity = useMutation({
    mutationFn: () => api.post<{ summary: string; warnings: string[] }>(`/studio/entities/${name}/deploy`).then((r) => r.data),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['runtime-entities'] }),
  })

  function handleAddField(e: React.FormEvent) {
    e.preventDefault()
    const req: AddFieldRequest = { ...fieldForm }
    if (req.type === 'ENUM' || req.type === 'MULTI_ENUM') {
      req.options = { values: optionValues.split(',').map((v) => v.trim()).filter(Boolean) }
    } else if (req.type === 'REFERENCE') {
      req.options = { targetEntity: targetEntity.trim() }
    }
    addField.mutate(req)
  }

  if (isLoading) return <p className="text-gray-500 text-sm">Loading…</p>
  if (!entity) return <p className="text-red-500 text-sm">Entity not found.</p>

  return (
    <div className="max-w-4xl mx-auto space-y-6">
      {/* Header */}
      <div className="flex items-center gap-3">
        <Link to="/studio/entities" className="text-gray-400 hover:text-gray-700">
          <ArrowLeft size={18} />
        </Link>
        <div className="flex-1">
          <h1 className="text-2xl font-bold text-gray-900">{entity.label}</h1>
          <p className="text-xs text-gray-400 font-mono">{entity.name} · {entity.tableName}</p>
        </div>
        <Link
          to={`/data/${name}`}
          className="flex items-center gap-2 rounded-md border border-gray-300 px-4 py-2 text-sm font-medium text-gray-700 hover:bg-gray-50"
        >
          <Table2 size={15} />
          Browse data
        </Link>
        <button
          onClick={() => deployEntity.mutate()}
          disabled={deployEntity.isPending}
          className="rounded-md bg-green-600 px-4 py-2 text-sm font-medium text-white hover:bg-green-700 disabled:opacity-50"
        >
          {deployEntity.isPending ? 'Deploying…' : 'Deploy table'}
        </button>
      </div>

      {/* Deploy result */}
      {deployEntity.isSuccess && (
        <div className="rounded-md bg-green-50 border border-green-200 px-4 py-3 text-sm text-green-800">
          {deployEntity.data?.summary ?? 'Deployed.'}
          {deployEntity.data?.warnings?.map((w) => <p key={w} className="mt-1 text-amber-700">⚠ {w}</p>)}
        </div>
      )}
      {deployEntity.isError && (
        <div className="rounded-md bg-red-50 border border-red-200 px-4 py-3 text-sm text-red-700">
          {errorMessage(deployEntity.error, 'Deploy failed')}
        </div>
      )}

      {/* Fields table */}
      <div>
        <div className="flex items-center justify-between mb-3">
          <h2 className="font-semibold text-gray-800">Fields ({entity.fields.length})</h2>
          <button
            onClick={() => setShowFieldForm((v) => !v)}
            className="flex items-center gap-1.5 rounded-md border border-brand-300 px-3 py-1.5 text-sm font-medium text-brand-600 hover:bg-brand-50"
          >
            <Plus size={14} />
            Add field
          </button>
        </div>

        {/* Add field form */}
        {showFieldForm && (
          <form
            onSubmit={handleAddField}
            className="mb-4 rounded-xl border border-brand-200 bg-brand-50 p-4 space-y-3"
          >
            <div className="grid grid-cols-3 gap-3">
              <div>
                <label className="block text-xs font-medium text-gray-600 mb-1">Name</label>
                <input
                  required
                  placeholder="email"
                  value={fieldForm.name}
                  onChange={(e) => setFieldForm({ ...fieldForm, name: e.target.value })}
                  className="w-full rounded border border-gray-300 px-2.5 py-1.5 text-sm focus:outline-none focus:ring-2 focus:ring-brand-500"
                />
              </div>
              <div>
                <label className="block text-xs font-medium text-gray-600 mb-1">Label</label>
                <input
                  required
                  placeholder="Email"
                  value={fieldForm.label}
                  onChange={(e) => setFieldForm({ ...fieldForm, label: e.target.value })}
                  className="w-full rounded border border-gray-300 px-2.5 py-1.5 text-sm focus:outline-none focus:ring-2 focus:ring-brand-500"
                />
              </div>
              <div>
                <label className="block text-xs font-medium text-gray-600 mb-1">Type</label>
                <select
                  value={fieldForm.type}
                  onChange={(e) => setFieldForm({ ...fieldForm, type: e.target.value as MetaFieldType })}
                  className="w-full rounded border border-gray-300 px-2.5 py-1.5 text-sm focus:outline-none focus:ring-2 focus:ring-brand-500"
                >
                  {FIELD_TYPES.map((t) => (
                    <option key={t} value={t}>{t}</option>
                  ))}
                </select>
              </div>
            </div>
            {(fieldForm.type === 'ENUM' || fieldForm.type === 'MULTI_ENUM') && (
              <input value={optionValues} onChange={(e) => setOptionValues(e.target.value)} required
                placeholder="Allowed values, comma separated (e.g. IT, OFFICE, TRAVEL)"
                className="w-full rounded border border-gray-300 px-2.5 py-1.5 text-sm" />
            )}
            {fieldForm.type === 'REFERENCE' && (
              <input value={targetEntity} onChange={(e) => setTargetEntity(e.target.value)} required
                placeholder="Target entity name (e.g. supplier)"
                className="w-full rounded border border-gray-300 px-2.5 py-1.5 text-sm" />
            )}
            {addField.isError && <p className="text-sm text-red-600">{errorMessage(addField.error, 'Could not add the field')}</p>}
            <div className="flex items-center gap-4 text-sm">
              <label className="flex items-center gap-2 text-gray-600">
                <input
                  type="checkbox"
                  checked={fieldForm.required ?? false}
                  onChange={(e) => setFieldForm({ ...fieldForm, required: e.target.checked })}
                />
                Required
              </label>
              <label className="flex items-center gap-2 text-gray-600">
                <input
                  type="checkbox"
                  checked={fieldForm.searchable ?? false}
                  onChange={(e) => setFieldForm({ ...fieldForm, searchable: e.target.checked })}
                />
                Searchable
              </label>
            </div>
            <div className="flex gap-3">
              <button
                type="submit"
                disabled={addField.isPending}
                className="rounded-md bg-brand-600 px-4 py-1.5 text-sm font-medium text-white hover:bg-brand-700 disabled:opacity-50"
              >
                {addField.isPending ? 'Adding…' : 'Add'}
              </button>
              <button type="button" onClick={() => setShowFieldForm(false)} className="text-sm text-gray-500 hover:text-gray-800">
                Cancel
              </button>
            </div>
          </form>
        )}

        {entity.fields.length === 0 ? (
          <p className="text-sm text-gray-400 italic">No fields yet.</p>
        ) : (
          <div className="overflow-hidden rounded-xl border border-gray-200 bg-white shadow-sm">
            <table className="min-w-full divide-y divide-gray-100 text-sm">
              <thead className="bg-gray-50">
                <tr>
                  <th className="px-4 py-3 text-left font-medium text-gray-500">#</th>
                  <th className="px-4 py-3 text-left font-medium text-gray-500">Name</th>
                  <th className="px-4 py-3 text-left font-medium text-gray-500">Label</th>
                  <th className="px-4 py-3 text-left font-medium text-gray-500">Type</th>
                  <th className="px-4 py-3 text-left font-medium text-gray-500">Req</th>
                  <th className="px-4 py-3 text-left font-medium text-gray-500">Search</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-100">
                {[...entity.fields]
                  .sort((a, b) => a.position - b.position)
                  .map((f) => (
                    <tr key={f.id} className="hover:bg-gray-50">
                      <td className="px-4 py-2.5 text-gray-400">{f.position}</td>
                      <td className="px-4 py-2.5 font-mono text-xs text-gray-800">{f.name}</td>
                      <td className="px-4 py-2.5 text-gray-700">{f.label}</td>
                      <td className="px-4 py-2.5">
                        <span className="rounded-full bg-brand-50 px-2 py-0.5 text-xs font-medium text-brand-700">
                          {f.type}
                        </span>
                      </td>
                      <td className="px-4 py-2.5 text-gray-500">{f.required ? '✓' : '—'}</td>
                      <td className="px-4 py-2.5 text-gray-500">{f.searchable ? '✓' : '—'}</td>
                    </tr>
                  ))}
              </tbody>
            </table>
          </div>
        )}
      </div>
    </div>
  )
}
