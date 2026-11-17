import { useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { ArrowLeft, Pencil, Trash2 } from 'lucide-react'
import api from '@/lib/api'
import { AvailableTransition, HistoryEntry } from '@/types/meta'
import { errorMessage, formatValue, isLocked, useRuntimeEntity } from '@/lib/runtime'
import FieldValue from '@/components/FieldValue'
import StateBadge from '@/components/StateBadge'

export default function RecordDetailPage() {
  const { entityName, id } = useParams<{ entityName: string; id: string }>()
  const navigate = useNavigate()
  const qc = useQueryClient()
  const [comment, setComment] = useState('')
  const [error, setError] = useState<string | null>(null)

  const { data: entity } = useRuntimeEntity(entityName)
  const base = `/data/${entityName}/${id}`

  const { data: record, error: recordError } = useQuery<Record<string, unknown>>({
    queryKey: ['record', entityName, id],
    queryFn: () => api.get(base).then((r) => r.data),
  })
  const { data: transitions = [] } = useQuery<AvailableTransition[]>({
    queryKey: ['transitions', entityName, id, record?.state],
    queryFn: () => api.get(`${base}/transitions`).then((r) => r.data),
    enabled: !!entity?.hasWorkflow && !!record,
  })
  const { data: history = [] } = useQuery<HistoryEntry[]>({
    queryKey: ['history', entityName, id, record?.state],
    queryFn: () => api.get(`${base}/history`).then((r) => r.data),
    enabled: !!entity?.hasWorkflow && !!record,
  })

  const refresh = () => {
    qc.invalidateQueries({ queryKey: ['record', entityName, id] })
    qc.invalidateQueries({ queryKey: ['records', entityName] })
  }

  const run = useMutation({
    mutationFn: (t: AvailableTransition) =>
      api.post(`${base}/transition/${t.id}`, { comment: comment || undefined }),
    onSuccess: () => { setComment(''); setError(null); refresh() },
    onError: (e) => { setError(errorMessage(e, 'Transition failed')); refresh() },
  })

  const remove = useMutation({
    mutationFn: () => api.delete(base),
    onSuccess: () => { refresh(); navigate(`/data/${entityName}`) },
    onError: (e) => setError(errorMessage(e, 'Delete failed')),
  })

  if (recordError) return <p className="text-sm text-gray-500">This record does not exist or you cannot access it.</p>
  if (!entity || !record) return <p className="text-sm text-gray-500">Loading…</p>

  return (
    <div className="max-w-3xl mx-auto space-y-6">
      <div className="flex items-center gap-3">
        <Link to={`/data/${entityName}`} className="text-gray-400 hover:text-gray-700"><ArrowLeft size={18} /></Link>
        <h1 className="flex-1 text-2xl font-bold text-gray-900">{entity.label}</h1>
        {entity.hasWorkflow && <StateBadge entity={entity} state={record.state} />}
        {entity.access.canWrite && !isLocked(entity, record.state) && (
          <Link to={`/data/${entityName}/${id}/edit`}
            className="flex items-center gap-1.5 rounded-md border border-gray-300 px-3 py-1.5 text-sm text-gray-700 hover:bg-gray-50">
            <Pencil size={14} /> Edit
          </Link>
        )}
        {entity.access.canDelete && !isLocked(entity, record.state) && (
          <button onClick={() => { if (confirm('Delete this record?')) remove.mutate() }}
            className="flex items-center gap-1.5 rounded-md border border-red-200 px-3 py-1.5 text-sm text-red-600 hover:bg-red-50">
            <Trash2 size={14} /> Delete
          </button>
        )}
      </div>

      <dl className="rounded-xl border border-gray-200 bg-white p-6 shadow-sm grid grid-cols-1 sm:grid-cols-2 gap-x-8 gap-y-4">
        {entity.fields.map((f) => (
          <div key={f.name}>
            <dt className="text-xs font-medium uppercase tracking-wide text-gray-400">{f.label}</dt>
            <dd className="mt-0.5 text-sm text-gray-800 break-words"><FieldValue field={f} value={record[f.name]} /></dd>
          </div>
        ))}
        <div>
          <dt className="text-xs font-medium uppercase tracking-wide text-gray-400">Created</dt>
          <dd className="mt-0.5 text-sm text-gray-800">
            {formatValue(record.created_at && new Date(String(record.created_at)).toLocaleString())} · {formatValue(record.created_by)}
          </dd>
        </div>
      </dl>

      {error && <p className="text-sm text-red-600">{error}</p>}

      {entity.hasWorkflow && (
        <section className="rounded-xl border border-gray-200 bg-white p-6 shadow-sm space-y-4">
          <h2 className="text-sm font-semibold text-gray-700">Workflow</h2>
          {transitions.length === 0 ? (
            <p className="text-sm text-gray-500">No action available for you in this state.</p>
          ) : (
            <>
              <input value={comment} onChange={(e) => setComment(e.target.value)} placeholder="Comment (optional)"
                className="w-full rounded-md border border-gray-300 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-brand-500" />
              <div className="flex flex-wrap gap-2">
                {transitions.map((t) => (
                  <button key={t.id} disabled={run.isPending} onClick={() => run.mutate(t)}
                    className="rounded-md bg-brand-600 px-4 py-2 text-sm font-medium text-white hover:bg-brand-700 disabled:opacity-50">
                    {t.label} → {t.toStateLabel}
                  </button>
                ))}
              </div>
            </>
          )}

          {history.length > 0 && (
            <ol className="border-l-2 border-gray-100 pl-4 space-y-3 pt-2">
              {history.map((h, i) => (
                <li key={i} className="text-sm">
                  <span className="font-medium text-gray-800">{h.transition_label}</span>
                  <span className="text-gray-500"> · {h.from_state ?? '—'} → {h.to_state} · {h.username}</span>
                  <div className="text-xs text-gray-400">{new Date(h.created_at).toLocaleString()}</div>
                  {h.comment && <div className="text-gray-600 italic">“{h.comment}”</div>}
                </li>
              ))}
            </ol>
          )}
        </section>
      )}
    </div>
  )
}
