import { hasRole } from '@/lib/auth'
import { useQuery } from '@tanstack/react-query'
import api from './api'
import { ApiProblem, CurrentUser, RuntimeEntity } from '@/types/meta'

export function useMe() {
  return useQuery<CurrentUser>({
    queryKey: ['me'],
    queryFn: () => api.get('/me').then((r) => r.data),
    staleTime: 60_000,
  })
}

export function useRuntimeEntities() {
  return useQuery<RuntimeEntity[]>({
    queryKey: ['runtime-entities'],
    queryFn: () => api.get('/meta/entities').then((r) => r.data),
    staleTime: 30_000,
  })
}

export function useRuntimeEntity(name?: string) {
  return useQuery<RuntimeEntity>({
    queryKey: ['runtime-entity', name],
    queryFn: () => api.get(`/meta/entities/${name}`).then((r) => r.data),
    enabled: !!name,
  })
}

/** Turns an API error into one readable message (field errors included). */
export function errorMessage(err: unknown, fallback = 'Something went wrong'): string {
  const problem = (err as { response?: { data?: ApiProblem } })?.response?.data
  if (!problem) return err instanceof Error && err.message && !('response' in err) ? err.message : fallback
  const errors = problem.errors
  if (errors && !Array.isArray(errors) && Object.keys(errors).length > 0) {
    return Object.entries(errors).map(([f, m]) => `${f}: ${m}`).join(' · ')
  }
  if (Array.isArray(errors) && errors.length > 0) return errors.join(' · ')
  return problem.detail ?? fallback
}

export function formatValue(value: unknown): string {
  if (value === null || value === undefined || value === '') return '—'
  if (typeof value === 'boolean') return value ? 'Yes' : 'No'
  if (typeof value === 'object') return JSON.stringify(value)
  return String(value)
}

/** A record in a terminal workflow state is frozen for everyone except ADMIN (the server enforces it too). */
export function isLocked(entity: RuntimeEntity | undefined, state: unknown): boolean {
  if (!entity || !state || hasRole('ADMIN')) return false
  return entity.states.some((s) => s.name === state && s.terminal)
}
