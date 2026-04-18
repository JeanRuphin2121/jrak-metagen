import { useState } from 'react'
import { useParams, Link, useNavigate } from 'react-router-dom'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { Plus, Trash2, Pencil, ArrowLeft, Search } from 'lucide-react'
import api from '@/lib/api'
import { MetaEntity, RecordPage } from '@/types/meta'

export default function DataListPage() {
  const { entityName } = useParams<{ entityName: string }>()
  const navigate       = useNavigate()
  const qc             = useQueryClient()
  const [search, setSearch] = useState('')
  const [page, setPage]     = useState(0)
  const PAGE_SIZE = 20

  const { data: entity } = useQuery<MetaEntity>({
    queryKey: ['entity', entityName],
    queryFn: () => api.get(`/studio/entities/${entityName}`).then((r) => r.data),
    enabled: !!entityName,
  })

  const { data: records, isLoading } = useQuery<RecordPage>({
    queryKey: ['records', entityName, page, search],
    queryFn: () =>
      api.get(`/data/${entityName}`, {
        params: { page, size: PAGE_SIZE, search: search || undefined },
      }).then((r) => r.data),
    enabled: !!entityName,
  })

  const deleteRecord = useMutation({
    mutationFn: (id: string) => api.delete(`/data/${entityName}/${id}`),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['records', entityName] }),
  })

  const visibleFields = entity?.fields.slice().sort((a, b) => a.position - b.position).slice(0, 5) ?? []

  return (
    <div className="max-w-5xl mx-auto space-y-6">
      {/* Header */}
      <div className="flex items-center gap-3">
        <Link to={`/studio/entities/${entityName}`} className="text-gray-400 hover:text-gray-700">
          <ArrowLeft size={18} />
        </Link>
        <div className="flex-1">
          <h1 className="text-2xl font-bold text-gray-900">{entity?.label ?? entityName}</h1>
          <p className="text-xs text-gray-400 font-mono">{entity?.tableName}</p>
        </div>
        <Link
          to={`/data/${entityName}/new`}
          className="flex items-center gap-2 rounded-md bg-brand-600 px-4 py-2 text-sm font-medium text-white hover:bg-brand-700"
        >
          <Plus size={15} />
          New record
        </Link>
      </div>

      {/* Search */}
      <div className="relative max-w-xs">
        <Search size={15} className="absolute left-3 top-1/2 -translate-y-1/2 text-gray-400" />
        <input
          type="text"
          placeholder="Search…"
          value={search}
          onChange={(e) => { setSearch(e.target.value); setPage(0) }}
          className="w-full rounded-md border border-gray-300 pl-9 pr-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-brand-500"
        />
      </div>

      {/* Table */}
      {isLoading ? (
        <p className="text-gray-500 text-sm">Loading…</p>
      ) : !records || records.content.length === 0 ? (
        <div className="rounded-xl border-2 border-dashed border-gray-200 py-16 text-center">
          <p className="text-gray-500">No records yet.</p>
        </div>
      ) : (
        <div className="overflow-hidden rounded-xl border border-gray-200 bg-white shadow-sm">
          <table className="min-w-full divide-y divide-gray-100 text-sm">
            <thead className="bg-gray-50">
              <tr>
                {visibleFields.map((f) => (
                  <th key={f.name} className="px-4 py-3 text-left font-medium text-gray-500">
                    {f.label}
                  </th>
                ))}
                <th className="px-4 py-3 text-right font-medium text-gray-500">Actions</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-100">
              {records.content.map((row) => {
                const id = String(row['id'])
                return (
                  <tr key={id} className="hover:bg-gray-50">
                    {visibleFields.map((f) => (
                      <td key={f.name} className="px-4 py-3 text-gray-700 max-w-[200px] truncate">
                        {String(row[f.name] ?? '—')}
                      </td>
                    ))}
                    <td className="px-4 py-3 text-right">
                      <div className="flex items-center justify-end gap-2">
                        <button
                          onClick={() => navigate(`/data/${entityName}/${id}/edit`)}
                          className="text-gray-400 hover:text-brand-600"
                          title="Edit"
                        >
                          <Pencil size={15} />
                        </button>
                        <button
                          onClick={() => {
                            if (confirm('Delete this record?')) deleteRecord.mutate(id)
                          }}
                          className="text-gray-400 hover:text-red-500"
                          title="Delete"
                        >
                          <Trash2 size={15} />
                        </button>
                      </div>
                    </td>
                  </tr>
                )
              })}
            </tbody>
          </table>

          {/* Pagination */}
          {records.totalPages > 1 && (
            <div className="flex items-center justify-between border-t border-gray-100 px-4 py-3 text-sm text-gray-500">
              <span>
                {records.totalElements} record{records.totalElements !== 1 ? 's' : ''}
              </span>
              <div className="flex gap-2">
                <button
                  onClick={() => setPage((p) => p - 1)}
                  disabled={page === 0}
                  className="rounded px-3 py-1 border border-gray-200 hover:bg-gray-50 disabled:opacity-40"
                >
                  Previous
                </button>
                <span className="rounded px-3 py-1 bg-gray-100">
                  {page + 1} / {records.totalPages}
                </span>
                <button
                  onClick={() => setPage((p) => p + 1)}
                  disabled={page + 1 >= records.totalPages}
                  className="rounded px-3 py-1 border border-gray-200 hover:bg-gray-50 disabled:opacity-40"
                >
                  Next
                </button>
              </div>
            </div>
          )}
        </div>
      )}
    </div>
  )
}
