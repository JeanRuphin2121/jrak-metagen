import { useState } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import { Download, Upload, CheckCircle } from 'lucide-react'
import api from '@/lib/api'
import { errorMessage } from '@/lib/runtime'

/** Config as code: export the platform as YAML, import one (dry run first). */
export default function ConfigPage() {
  const qc = useQueryClient()
  const [yaml, setYaml] = useState('')
  const [result, setResult] = useState<Record<string, unknown> | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  async function exportYaml() {
    setError(null)
    try {
      const { data } = await api.get<string>('/studio/config/export', { responseType: 'text', transformResponse: (d) => d })
      setYaml(data)
    } catch (e) { setError(errorMessage(e, 'Export failed')) }
  }

  async function importYaml(dryRun: boolean, deploy: boolean) {
    setBusy(true); setError(null); setResult(null)
    try {
      const { data } = await api.post('/studio/config/import', yaml, {
        params: { dryRun, deploy },
        headers: { 'Content-Type': 'text/plain' },
      })
      setResult(data)
      if (!dryRun) {
        qc.invalidateQueries({ queryKey: ['runtime-entities'] })
        qc.invalidateQueries({ queryKey: ['entities'] })
      }
    } catch (e) { setError(errorMessage(e, 'Import failed')) }
    finally { setBusy(false) }
  }

  const btn = 'flex items-center gap-2 rounded-md px-4 py-2 text-sm font-medium disabled:opacity-50'
  return (
    <div className="max-w-4xl mx-auto space-y-6">
      <div>
        <h1 className="text-2xl font-bold text-gray-900">Configuration as code</h1>
        <p className="text-sm text-gray-500">
          Roles, workflows, entities and permissions as one YAML file you can version in Git.
          Import never deletes anything. Run a dry run first.
        </p>
      </div>

      <textarea value={yaml} onChange={(e) => setYaml(e.target.value)} rows={22} spellCheck={false}
        placeholder="Paste a configuration here or click Export"
        className="w-full rounded-lg border border-gray-300 p-3 font-mono text-xs focus:outline-none focus:ring-2 focus:ring-brand-500" />

      <div className="flex flex-wrap gap-3">
        <button onClick={exportYaml} className={`${btn} border border-gray-300 text-gray-700 hover:bg-gray-50`}>
          <Download size={15} /> Export current
        </button>
        <button disabled={busy || !yaml.trim()} onClick={() => importYaml(true, false)}
          className={`${btn} border border-brand-600 text-brand-700 hover:bg-brand-50`}>
          <CheckCircle size={15} /> Validate (dry run)
        </button>
        <button disabled={busy || !yaml.trim()}
          onClick={() => { if (confirm('Apply this configuration and deploy the tables?')) importYaml(false, true) }}
          className={`${btn} bg-brand-600 text-white hover:bg-brand-700`}>
          <Upload size={15} /> Import and deploy
        </button>
      </div>

      {error && <p className="rounded-md bg-red-50 p-3 text-sm text-red-700 break-words">{error}</p>}
      {result && (
        <pre className="rounded-md bg-gray-900 p-4 text-xs text-green-300 overflow-x-auto">{JSON.stringify(result, null, 2)}</pre>
      )}
    </div>
  )
}
