import { RuntimeEntity } from '@/types/meta'

export default function StateBadge({ entity, state }: { entity?: RuntimeEntity; state: unknown }) {
  if (!state) return <span className="text-gray-400">—</span>
  const found = entity?.states.find((s) => s.name === state)
  const color = found?.color ?? '#6B7280'
  return (
    <span
      className="inline-flex items-center rounded-full px-2.5 py-0.5 text-xs font-medium"
      style={{ backgroundColor: color + '22', color }}
    >
      {found?.label ?? String(state)}
    </span>
  )
}
