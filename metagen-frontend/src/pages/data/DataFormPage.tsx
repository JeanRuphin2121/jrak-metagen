import { useEffect, useState } from 'react'
import { useParams, useNavigate, Link } from 'react-router-dom'
import { useQuery, useMutation } from '@tanstack/react-query'
import { ArrowLeft, Save } from 'lucide-react'
import api from '@/lib/api'
import { MetaEntity, MetaField, MetaFieldType } from '@/types/meta'

type FormValues = Record<string, string | boolean | number>

export default function DataFormPage() {
  const { entityName, id } = useParams<{ entityName: string; id?: string }>()
  const navigate = useNavigate()
  const isEdit   = !!id
  const [values, setValues]   = useState<FormValues>({})
  const [error, setError]     = useState<string | null>(null)

  const { data: entity } = useQuery<MetaEntity>({
    queryKey: ['entity', entityName],
    queryFn: () => api.get(`/studio/entities/${entityName}`).then((r) => r.data),
    enabled: !!entityName,
  })

  const { data: record } = useQuery<Record<string, unknown>>({
    queryKey: ['record', entityName, id],
    queryFn: () => api.get(`/data/${entityName}/${id}`).then((r) => r.data),
    enabled: isEdit && !!id,
  })

  // Pre-fill form when editing
  useEffect(() => {
    if (record && entity) {
      const initial: FormValues = {}
      entity.fields.forEach((f) => {
        initial[f.name] = String(record[f.name] ?? '')
      })
      setValues(initial)
    }
  }, [record, entity])

  const save = useMutation({
    mutationFn: () =>
      isEdit
        ? api.put(`/data/${entityName}/${id}`, values)
        : api.post(`/data/${entityName}`, values),
    onSuccess: () => navigate(`/data/${entityName}`),
    onError: (err: unknown) => {
      const msg = (err as { response?: { data?: { detail?: string } } })?.response?.data?.detail
      setError(msg ?? 'Save failed')
    },
  })

  const sortedFields = entity?.fields.slice().sort((a, b) => a.position - b.position) ?? []

  function handleSubmit(e: React.FormEvent) {
    e.preventDefault()
    save.mutate()
  }

  return (
    <div className="max-w-2xl mx-auto space-y-6">
      <div className="flex items-center gap-3">
        <Link to={`/data/${entityName}`} className="text-gray-400 hover:text-gray-700">
          <ArrowLeft size={18} />
        </Link>
        <h1 className="text-2xl font-bold text-gray-900">
          {isEdit ? 'Edit' : 'New'} {entity?.label ?? entityName}
        </h1>
      </div>

      {entity && (
        <form
          onSubmit={handleSubmit}
          className="rounded-xl border border-gray-200 bg-white p-6 shadow-sm space-y-5"
        >
          {sortedFields.map((field) => (
            <FieldInput
              key={field.name}
              field={field}
              value={values[field.name] ?? ''}
              onChange={(v) => setValues({ ...values, [field.name]: v })}
            />
          ))}

          {error && <p className="text-sm text-red-600">{error}</p>}

          <div className="flex gap-3 pt-2">
            <button
              type="submit"
              disabled={save.isPending}
              className="flex items-center gap-2 rounded-md bg-brand-600 px-5 py-2 text-sm font-medium text-white hover:bg-brand-700 disabled:opacity-50"
            >
              <Save size={15} />
              {save.isPending ? 'Saving…' : 'Save'}
            </button>
            <Link
              to={`/data/${entityName}`}
              className="rounded-md border border-gray-300 px-4 py-2 text-sm text-gray-600 hover:bg-gray-50"
            >
              Cancel
            </Link>
          </div>
        </form>
      )}
    </div>
  )
}

// ── Dynamic field input ───────────────────────────────────────────────────────

function FieldInput({
  field,
  value,
  onChange,
}: {
  field: MetaField
  value: string | boolean | number
  onChange: (v: string | boolean | number) => void
}) {
  const inputClass =
    'w-full rounded-md border border-gray-300 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-brand-500'

  const label = (
    <label className="block text-sm font-medium text-gray-700 mb-1">
      {field.label}
      {field.required && <span className="text-red-500 ml-1">*</span>}
    </label>
  )

  switch (field.type as MetaFieldType) {
    case 'BOOLEAN':
      return (
        <div className="flex items-center gap-3">
          <input
            type="checkbox"
            id={field.name}
            checked={!!value}
            onChange={(e) => onChange(e.target.checked)}
            className="h-4 w-4 rounded border-gray-300 text-brand-600"
          />
          <label htmlFor={field.name} className="text-sm font-medium text-gray-700">
            {field.label}
          </label>
        </div>
      )

    case 'TEXT':
    case 'RICH_TEXT':
      return (
        <div>
          {label}
          <textarea
            rows={4}
            required={field.required}
            value={String(value)}
            onChange={(e) => onChange(e.target.value)}
            className={inputClass + ' resize-none'}
          />
        </div>
      )

    case 'INTEGER':
    case 'LONG':
      return (
        <div>
          {label}
          <input
            type="number"
            step="1"
            required={field.required}
            value={String(value)}
            onChange={(e) => onChange(e.target.value)}
            className={inputClass}
          />
        </div>
      )

    case 'DECIMAL':
      return (
        <div>
          {label}
          <input
            type="number"
            step="any"
            required={field.required}
            value={String(value)}
            onChange={(e) => onChange(e.target.value)}
            className={inputClass}
          />
        </div>
      )

    case 'DATE':
      return (
        <div>
          {label}
          <input type="date" required={field.required} value={String(value)}
            onChange={(e) => onChange(e.target.value)} className={inputClass} />
        </div>
      )

    case 'DATETIME':
      return (
        <div>
          {label}
          <input type="datetime-local" required={field.required} value={String(value)}
            onChange={(e) => onChange(e.target.value)} className={inputClass} />
        </div>
      )

    case 'EMAIL':
      return (
        <div>
          {label}
          <input type="email" required={field.required} value={String(value)}
            onChange={(e) => onChange(e.target.value)} className={inputClass} />
        </div>
      )

    case 'URL':
      return (
        <div>
          {label}
          <input type="url" required={field.required} value={String(value)}
            onChange={(e) => onChange(e.target.value)} className={inputClass} />
        </div>
      )

    default:
      return (
        <div>
          {label}
          <input
            type="text"
            required={field.required}
            value={String(value)}
            onChange={(e) => onChange(e.target.value)}
            className={inputClass}
          />
        </div>
      )
  }
}
