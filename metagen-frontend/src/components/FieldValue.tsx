import { useQuery } from '@tanstack/react-query'
import api from '@/lib/api'
import { formatValue, useRuntimeEntity } from '@/lib/runtime'
import { RuntimeField } from '@/types/meta'

type RecordPage = { content: Record<string, unknown>[] }

/** Renders a field value; a REFERENCE is shown with the label of the record it points to, not its id. */
export default function FieldValue({ field, value }: { field: RuntimeField; value: unknown }) {
  const target = field.type === 'REFERENCE' ? (field.options?.targetEntity as string | undefined) : undefined
  const { data: targetMeta } = useRuntimeEntity(target)
  const { data: page } = useQuery<RecordPage>({
    queryKey: ['reference-options', target],
    queryFn: () => api.get(`/data/${target}`, { params: { size: 200, sort: 'created_at,desc' } }).then((r) => r.data),
    enabled: !!target && !!targetMeta,
  })
  if (!target || !value) return <>{formatValue(value)}</>
  const labelField = targetMeta?.fields.find((f) => ['STRING', 'EMAIL'].includes(f.type))?.name
  const row = page?.content.find((r) => String(r.id) === String(value))
  return <>{row && labelField ? String(row[labelField] ?? value) : formatValue(value)}</>
}
