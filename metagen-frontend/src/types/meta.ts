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
  rowFilterScript: string | null
}

// ── Workflow ──────────────────────────────────────────────────────────────────

export interface MetaState {
  id: string
  name: string
  label: string
  isInitial: boolean
  isTerminal: boolean
  color: string | null
}

export interface MetaTransition {
  id: string
  name: string
  label: string
  fromStateName: string
  toStateName: string
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
