// ── Auth ─────────────────────────────────────────────────────────────────────

export interface LoginRequest {
  username: string
  password: string
}

export interface LoginResponse {
  token: string
  username: string
  roles: string[]
  expiresIn: number
}

// ── Meta types ───────────────────────────────────────────────────────────────

export type MetaFieldType =
  | 'STRING' | 'TEXT' | 'RICH_TEXT' | 'EMAIL' | 'URL' | 'PHONE'
  | 'INTEGER' | 'LONG' | 'DECIMAL' | 'BOOLEAN'
  | 'DATE' | 'TIME' | 'DATETIME'
  | 'FILE' | 'IMAGE' | 'JSON'
  | 'ENUM' | 'MULTI_ENUM' | 'REFERENCE'

export type MetaViewType = 'LIST' | 'FORM' | 'DASHBOARD' | 'KANBAN' | 'CALENDAR' | 'CHART'

export interface MetaField {
  id: string
  name: string
  label: string
  type: MetaFieldType
  required: boolean
  searchable: boolean
  position: number
  options: Record<string, unknown>
}

export interface MetaView {
  id: string
  name: string
  type: MetaViewType
  config: Record<string, unknown>
}

export interface MetaEntity {
  id: string
  name: string
  label: string
  tableName: string
  softDelete: boolean
  fields: MetaField[]
  views: MetaView[]
  workflowName: string | null
}

export interface MetaEntitySummary {
  id: string
  name: string
  label: string
  tableName: string
  fieldCount?: number
}

// ── Studio API request/response ──────────────────────────────────────────────

export interface CreateEntityRequest {
  name: string
  label: string
  tableName?: string
  softDelete?: boolean
}

export interface AddFieldRequest {
  name: string
  label: string
  type: MetaFieldType
  required?: boolean
  searchable?: boolean
  position?: number
  options?: Record<string, unknown>
}

// ── Roles & permissions ──────────────────────────────────────────────────────

export interface MetaRole {
  id: string
  name: string
  label: string
  builtIn: boolean
}

export interface MetaPermission {
  id: string
  roleId: string
  entityName: string
  canRead: boolean
  canWrite: boolean
  canDelete: boolean
  rowFilter: Record<string, unknown> | null
  fieldMask: Record<string, string[]> | null
}

// ── Workflow ──────────────────────────────────────────────────────────────────

export interface MetaState {
  id: string
  name: string
  label: string
  initial: boolean
  terminal: boolean
  color: string | null
}

export interface MetaTransition {
  id: string
  label: string
  fromState: string
  toState: string
  condition: Record<string, unknown> | null
  action: string | null
  requiredRole: string | null
}

export interface MetaWorkflow {
  id: string
  name: string
  label: string
  states: MetaState[]
  transitions: MetaTransition[]
}

// ── AI ────────────────────────────────────────────────────────────────────────

export interface GenerateRequest {
  description: string
}

export interface FieldProposal {
  name: string
  label: string
  type: string           // MetaFieldType name
  required: boolean
  unique: boolean
  searchable: boolean
  options?: Record<string, unknown>
}

export interface StateProposal {
  name: string
  label: string
  initial: boolean
  terminal: boolean
  color: string | null
}

export interface TransitionProposal {
  from: string
  to: string
  label: string
  requiredRole: string | null
}

export interface WorkflowProposal {
  name: string
  label: string
  states: StateProposal[]
  transitions: TransitionProposal[]
}

export interface EntityProposal {
  name: string
  label: string
  description: string | null
  workflow: string | null    // workflow name reference
  fields: FieldProposal[]
}

export interface MetaProposal {
  entities: EntityProposal[]
  workflows: WorkflowProposal[]
  explanation: string
}

// ── User management ───────────────────────────────────────────────────────────

export interface CreateUserRequest {
  username: string
  email: string
  password: string
  roles: string[]
}

// ── Dynamic data ──────────────────────────────────────────────────────────────

export interface RecordPage {
  content: Record<string, unknown>[]
  totalElements: number
  totalPages: number
  page: number
  size: number
}

// ── Runtime metadata (what the generated UI needs, filtered by the user's roles) ──

export interface RuntimeField {
  name: string
  label: string
  type: MetaFieldType
  required: boolean
  unique: boolean
  searchable: boolean
  readonly: boolean
  defaultValue: string | null
  options: Record<string, unknown> | null
}

export interface RuntimeState {
  name: string
  label: string
  color: string | null
  initial: boolean
  terminal: boolean
}

export interface RuntimeEntity {
  name: string
  label: string
  description: string | null
  hasWorkflow: boolean
  states: RuntimeState[]
  fields: RuntimeField[]
  access: { canRead: boolean; canWrite: boolean; canDelete: boolean }
}

export interface CurrentUser {
  username: string
  roles: string[]
  admin: boolean
}

export interface AvailableTransition {
  id: string
  label: string
  toState: string
  toStateLabel: string
}

export interface HistoryEntry {
  from_state: string | null
  to_state: string
  transition_label: string
  username: string
  comment: string | null
  created_at: string
}

/** RFC 7807 problem as returned by the API. */
export interface ApiProblem {
  title?: string
  detail?: string
  errors?: Record<string, string> | string[]
}
