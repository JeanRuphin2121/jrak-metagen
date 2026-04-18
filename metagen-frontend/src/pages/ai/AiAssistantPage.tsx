import { useState } from 'react'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { Sparkles, CheckCircle, AlertCircle, ChevronDown, ChevronUp } from 'lucide-react'
import api from '@/lib/api'
import { MetaProposal, EntityProposal, WorkflowProposal } from '@/types/meta'

export default function AiAssistantPage() {
  const qc = useQueryClient()
  const [description, setDescription]     = useState('')
  const [proposal, setProposal]           = useState<MetaProposal | null>(null)
  const [importSuccess, setImportSuccess] = useState(false)
  const [expandedEntity, setExpandedEntity] = useState<string | null>(null)

  const generate = useMutation({
    mutationFn: () =>
      api.post<MetaProposal>('/ai/generate', { description }).then((r) => r.data),
    onSuccess: (data) => {
      setProposal(data)
      setImportSuccess(false)
      setExpandedEntity(data.entities[0]?.name ?? null)
    },
  })

  const importAll = useMutation({
    mutationFn: () => api.post('/studio/config/import-proposal', proposal),
    onSuccess: () => {
      setImportSuccess(true)
      qc.invalidateQueries({ queryKey: ['entities'] })
    },
  })

  const charCount = description.length
  const workflowMap = Object.fromEntries(
    (proposal?.workflows ?? []).map((w) => [w.name, w])
  )

  return (
    <div className="max-w-3xl mx-auto space-y-6">
      <div>
        <h1 className="text-2xl font-bold text-gray-900 flex items-center gap-2">
          <Sparkles size={22} className="text-purple-500" />
          AI Assistant
        </h1>
        <p className="text-gray-500 mt-1 text-sm">
          Describe your business domain in plain language. The AI will propose entities, fields, and workflows.
        </p>
      </div>

      {/* Input */}
      <div className="rounded-xl border border-gray-200 bg-white p-5 shadow-sm space-y-3">
        <label className="block text-sm font-medium text-gray-700">
          Business domain description
        </label>
        <textarea
          rows={5}
          placeholder="e.g. I want to manage suppliers with purchase orders. An order can be DRAFT, then APPROVED, then RECEIVED."
          value={description}
          onChange={(e) => setDescription(e.target.value)}
          className="w-full rounded-md border border-gray-300 px-3 py-2 text-sm resize-none focus:outline-none focus:ring-2 focus:ring-purple-400"
        />
        <div className="flex items-center justify-between">
          <span className={`text-xs ${charCount > 2000 ? 'text-red-500' : 'text-gray-400'}`}>
            {charCount} / 2000
          </span>
          <button
            onClick={() => generate.mutate()}
            disabled={charCount < 10 || charCount > 2000 || generate.isPending}
            className="flex items-center gap-2 rounded-md bg-purple-600 px-5 py-2 text-sm font-medium text-white hover:bg-purple-700 disabled:opacity-50 transition-colors"
          >
            <Sparkles size={15} />
            {generate.isPending ? 'Generating…' : 'Generate proposal'}
          </button>
        </div>
        {generate.isError && (
          <div className="flex items-center gap-2 text-sm text-red-600">
            <AlertCircle size={15} />
            Generation failed. Ensure <code className="bg-red-50 px-1 rounded">metagen.ai.enabled=true</code> and{' '}
            <code className="bg-red-50 px-1 rounded">ANTHROPIC_API_KEY</code> is set.
          </div>
        )}
      </div>

      {/* Proposal */}
      {proposal && (
        <div className="space-y-4">
          {/* AI summary */}
          <div className="rounded-xl border border-purple-200 bg-purple-50 px-5 py-4">
            <p className="text-sm font-medium text-purple-800">AI summary</p>
            <p className="text-sm text-purple-700 mt-1">{proposal.explanation}</p>
          </div>

          {/* Entity cards */}
          {proposal.entities.map((entity) => (
            <EntityCard
              key={entity.name}
              entity={entity}
              workflow={entity.workflow ? workflowMap[entity.workflow] ?? null : null}
              expanded={expandedEntity === entity.name}
              onToggle={() =>
                setExpandedEntity((v) => (v === entity.name ? null : entity.name))
              }
            />
          ))}

          {/* Import action */}
          <div className="flex items-center justify-between rounded-xl border border-gray-200 bg-white p-4 shadow-sm">
            <p className="text-sm text-gray-600">
              {proposal.entities.length} entit{proposal.entities.length === 1 ? 'y' : 'ies'} ·{' '}
              {proposal.workflows.length} workflow{proposal.workflows.length !== 1 ? 's' : ''} detected.
              Review above, then import.
            </p>
            <div className="flex items-center gap-3">
              {importSuccess && (
                <span className="flex items-center gap-1.5 text-sm text-green-600">
                  <CheckCircle size={15} />
                  Imported!
                </span>
              )}
              {importAll.isError && (
                <span className="text-sm text-red-600">Import failed</span>
              )}
              <button
                onClick={() => importAll.mutate()}
                disabled={importAll.isPending || importSuccess}
                className="rounded-md bg-gray-900 px-5 py-2 text-sm font-medium text-white hover:bg-gray-700 disabled:opacity-50 transition-colors"
              >
                {importAll.isPending ? 'Importing…' : 'Import all'}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}

// ── Entity card ───────────────────────────────────────────────────────────────

function EntityCard({
  entity,
  workflow,
  expanded,
  onToggle,
}: {
  entity: EntityProposal
  workflow: WorkflowProposal | null
  expanded: boolean
  onToggle: () => void
}) {
  return (
    <div className="rounded-xl border border-gray-200 bg-white shadow-sm overflow-hidden">
      <button
        onClick={onToggle}
        className="flex w-full items-center justify-between px-5 py-4 text-left hover:bg-gray-50 transition-colors"
      >
        <div>
          <span className="font-semibold text-gray-900">{entity.label}</span>
          <span className="ml-2 text-xs text-gray-400 font-mono">{entity.name}</span>
          <span className="ml-3 text-xs text-gray-500">
            {entity.fields.length} field{entity.fields.length !== 1 ? 's' : ''}
            {workflow ? ` · workflow: ${workflow.label}` : ''}
          </span>
        </div>
        {expanded
          ? <ChevronUp size={16} className="text-gray-400" />
          : <ChevronDown size={16} className="text-gray-400" />}
      </button>

      {expanded && (
        <div className="border-t border-gray-100 px-5 py-4 space-y-4">
          {/* Fields */}
          <div>
            <p className="text-xs font-semibold uppercase tracking-wider text-gray-400 mb-2">Fields</p>
            <div className="space-y-1.5">
              {entity.fields.map((f) => (
                <div key={f.name} className="flex items-center gap-3 text-sm">
                  <span className="font-mono text-gray-800 w-36 truncate">{f.name}</span>
                  <span className="rounded-full bg-brand-50 px-2 py-0.5 text-xs font-medium text-brand-700">
                    {f.type}
                  </span>
                  {f.required && <span className="text-xs text-red-500">required</span>}
                  <span className="text-gray-500">{f.label}</span>
                </div>
              ))}
            </div>
          </div>

          {/* Workflow transitions */}
          {workflow && (
            <div>
              <p className="text-xs font-semibold uppercase tracking-wider text-gray-400 mb-2">
                Workflow — {workflow.label}
              </p>
              <div className="flex flex-wrap gap-2">
                {workflow.transitions.map((t, i) => (
                  <span
                    key={i}
                    className="rounded-full bg-gray-100 px-3 py-1 text-xs text-gray-700"
                  >
                    {t.from} → {t.to}
                    {t.label ? ` (${t.label})` : ''}
                  </span>
                ))}
              </div>
            </div>
          )}
        </div>
      )}
    </div>
  )
}
