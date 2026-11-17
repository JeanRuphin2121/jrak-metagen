import { useEffect, useState } from 'react'
import { useParams, useNavigate, Link } from 'react-router-dom'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { ArrowLeft, Save } from 'lucide-react'
import api from '@/lib/api'
import { RecordPage, RuntimeField } from '@/types/meta'
import { errorMessage, useRuntimeEntity } from '@/lib/runtime'

type Value = string | boolean | string[]
type FormValues = Record<string, Value>

export default function DataFormPage() {
  const { entityName, id } = useParams<{ entityName: string; id?: string }>()
  const navigate = useNavigate()
  const qc = useQueryClient()
  const isEdit = !!id
  const [values, setValues] = useState<FormValues>({})
  const [error, setError] = useState<string | null>(null)

  const { data: entity } = useRuntimeEntity(entityName)

  const { data: record } = useQuery<Record<string, unknown>>({
    queryKey: ['record', entityName, id],
    queryFn: () => api.get(`/data/${entityName}/${id}`).then((r) => r.data),
    enabled: isEdit,
  })

  useEffect(() => {
    if (!entity) return
    const initial: FormValues = {}
    entity.fields.forEach((f) => {
      const raw = record?.[f.name]
      if (f.type === 'BOOLEAN') initial[f.name] = raw === true || (!record && f.defaultValue === 'true')
      else if (f.type === 'MULTI_ENUM') initial[f.name] = Array.isArray(raw) ? (raw as string[]) : []
      else if (f.type === 'JSON') initial[f.name] = raw == null ? '' : JSON.stringify(raw, null, 2)
      else if (f.type === 'DATETIME' && typeof raw === 'string') initial[f.name] = raw.slice(0, 16)
      else initial[f.name] = raw == null ? (record ? '' : f.defaultValue ?? '') : String(raw)
    })
    setValues(initial)
  }, [record, entity])

  const save = useMutation({
    mutationFn: () => {
      const body = toPayload(entity?.fields ?? [], values)
      return isEdit ? api.put(`/data/${entityName}/${id}`, body) : api.post(`/data/${entityName}`, body)
    },
    onSuccess: (res) => {
      qc.invalidateQueries({ queryKey: ['records', entityName] })
      qc.invalidateQueries({ queryKey: ['record', entityName] })
      navigate(`/data/${entityName}/${res.data.id ?? id}`)
    },
    onError: (e) => setError(errorMessage(e, 'Save failed')),
  })

  if (entity && !entity.access.canWrite) {
    return <p className="text-sm text-gray-500">You do not have permission to edit {entity.label}.</p>
  }

  return (
    <div className="max-w-2xl mx-auto space-y-6">
      <div className="flex items-center gap-3">
        <Link to={`/data/${entityName}`} className="text-gray-400 hover:text-gray-700"><ArrowLeft size={18} /></Link>
        <h1 className="text-2xl font-bold text-gray-900">
          {isEdit ? 'Edit' : 'New'} {entity?.label ?? entityName}
        </h1>
      </div>

      {entity && (
        <form
          onSubmit={(e) => { e.preventDefault(); setError(null); save.mutate() }}
          className="rounded-xl border border-gray-200 bg-white p-6 shadow-sm space-y-5"
        >
          {entity.fields.map((field) => (
            <FieldInput
              key={field.name}
              field={field}
              value={values[field.name] ?? ''}
              disabled={field.readonly}
              onChange={(v) => setValues({ ...values, [field.name]: v })}
            />
          ))}

          {error && <p className="text-sm text-red-600">{error}</p>}

          <div className="flex gap-3 pt-2">
            <button type="submit" disabled={save.isPending}
              className="flex items-center gap-2 rounded-md bg-brand-600 px-5 py-2 text-sm font-medium text-white hover:bg-brand-700 disabled:opacity-50">
              <Save size={15} /> {save.isPending ? 'Saving…' : 'Save'}
            </button>
            <Link to={`/data/${entityName}`}
              className="rounded-md border border-gray-300 px-4 py-2 text-sm text-gray-600 hover:bg-gray-50">Cancel</Link>
          </div>
        </form>
      )}
    </div>
  )
}

/** Converts form state into the JSON the API expects (empty → null, numbers, JSON). */
function toPayload(fields: RuntimeField[], values: FormValues): Record<string, unknown> {
  const body: Record<string, unknown> = {}
  for (const f of fields) {
    if (f.readonly) continue
    const v = values[f.name]
    if (f.type === 'BOOLEAN' || f.type === 'MULTI_ENUM') { body[f.name] = v; continue }
    const text = String(v ?? '').trim()
    if (text === '') { body[f.name] = null; continue }
    if (f.type === 'INTEGER' || f.type === 'LONG' || f.type === 'DECIMAL') body[f.name] = Number(text)
    else if (f.type === 'JSON') {
      try { body[f.name] = JSON.parse(text) } catch { throw new Error(`${f.label}: invalid JSON`) }
    } else body[f.name] = String(v)
  }
  return body
}

function FieldInput({ field, value, onChange, disabled }: {
  field: RuntimeField
  value: Value
  onChange: (v: Value) => void
  disabled: boolean
}) {
  const inputClass =
    'w-full rounded-md border border-gray-300 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-brand-500 disabled:bg-gray-100 disabled:text-gray-500'
  const label = (
    <label className="block text-sm font-medium text-gray-700 mb-1">
      {field.label}{field.required && <span className="text-red-500 ml-1">*</span>}
      {disabled && <span className="ml-2 text-xs font-normal text-gray-400">read-only</span>}
    </label>
  )
  const text = String(value)
  const common = { required: field.required && !disabled, disabled, className: inputClass }

  switch (field.type) {
    case 'BOOLEAN':
      return (
        <label className="flex items-center gap-3 text-sm font-medium text-gray-700">
          <input type="checkbox" checked={value === true} disabled={disabled}
            onChange={(e) => onChange(e.target.checked)} className="h-4 w-4 rounded border-gray-300" />
          {field.label}
        </label>
      )
    case 'TEXT':
    case 'RICH_TEXT':
    case 'JSON':
      return <div>{label}<textarea rows={4} {...common} value={text} onChange={(e) => onChange(e.target.value)} /></div>
    case 'INTEGER':
    case 'LONG':
      return <div>{label}<input type="number" step="1" {...common} value={text} onChange={(e) => onChange(e.target.value)} /></div>
    case 'DECIMAL':
      return <div>{label}<input type="number" step="any" {...common} value={text} onChange={(e) => onChange(e.target.value)} /></div>
    case 'DATE':
      return <div>{label}<input type="date" {...common} value={text} onChange={(e) => onChange(e.target.value)} /></div>
    case 'TIME':
      return <div>{label}<input type="time" {...common} value={text} onChange={(e) => onChange(e.target.value)} /></div>
    case 'DATETIME':
      return <div>{label}<input type="datetime-local" {...common} value={text} onChange={(e) => onChange(e.target.value)} /></div>
    case 'EMAIL':
      return <div>{label}<input type="email" {...common} value={text} onChange={(e) => onChange(e.target.value)} /></div>
    case 'URL':
      return <div>{label}<input type="url" {...common} value={text} onChange={(e) => onChange(e.target.value)} /></div>
    case 'ENUM': {
      const options = (field.options?.values as string[] | undefined) ?? []
      return (
        <div>{label}
          <select {...common} value={text} onChange={(e) => onChange(e.target.value)}>
            <option value="">—</option>
            {options.map((o) => <option key={o} value={o}>{o}</option>)}
          </select>
        </div>
      )
    }
    case 'MULTI_ENUM': {
      const options = (field.options?.values as string[] | undefined) ?? []
      const selected = Array.isArray(value) ? value : []
      return (
        <div>{label}
          <div className="flex flex-wrap gap-3">
            {options.map((o) => (
              <label key={o} className="flex items-center gap-1.5 text-sm text-gray-700">
                <input type="checkbox" disabled={disabled} checked={selected.includes(o)}
                  onChange={(e) => onChange(e.target.checked ? [...selected, o] : selected.filter((x) => x !== o))} />
                {o}
              </label>
            ))}
          </div>
        </div>
      )
    }
    case 'REFERENCE':
      return <ReferenceSelect field={field} label={label} value={text} onChange={onChange} disabled={disabled} inputClass={inputClass} />
    default:
      return <div>{label}<input type="text" {...common} value={text} onChange={(e) => onChange(e.target.value)} /></div>
  }
}

/** Dropdown of the records of the target entity, labelled with their first text field. */
function ReferenceSelect({ field, label, value, onChange, disabled, inputClass }: {
  field: RuntimeField
  label: React.ReactNode
  value: string
  onChange: (v: Value) => void
  disabled: boolean
  inputClass: string
}) {
  const target = field.options?.targetEntity as string | undefined
  const { data: targetMeta } = useRuntimeEntity(target)
  const { data: page } = useQuery<RecordPage>({
    queryKey: ['reference-options', target],
    queryFn: () => api.get(`/data/${target}`, { params: { size: 200, sort: 'created_at,desc' } }).then((r) => r.data),
    enabled: !!target && !!targetMeta,
  })
  const labelField = targetMeta?.fields.find((f) => ['STRING', 'EMAIL'].includes(f.type))?.name
  return (
    <div>{label}
      <select className={inputClass} disabled={disabled} required={field.required && !disabled}
        value={value} onChange={(e) => onChange(e.target.value)}>
        <option value="">—</option>
        {page?.content.map((r) => (
          <option key={String(r.id)} value={String(r.id)}>
            {labelField ? String(r[labelField] ?? r.id) : String(r.id)}
          </option>
        ))}
      </select>
    </div>
  )
}
