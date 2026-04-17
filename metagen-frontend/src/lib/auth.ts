import { LoginRequest, LoginResponse } from '@/types/meta'
import api from './api'

const TOKEN_KEY = 'mg_token'
const USER_KEY  = 'mg_user'

export interface AuthUser {
  username: string
  roles: string[]
}

export async function login(req: LoginRequest): Promise<AuthUser> {
  const { data } = await api.post<LoginResponse>('/auth/login', req)
  localStorage.setItem(TOKEN_KEY, data.token)
  const user: AuthUser = { username: data.username, roles: data.roles }
  localStorage.setItem(USER_KEY, JSON.stringify(user))
  return user
}

export function logout(): void {
  localStorage.removeItem(TOKEN_KEY)
  localStorage.removeItem(USER_KEY)
}

export function getCurrentUser(): AuthUser | null {
  const raw = localStorage.getItem(USER_KEY)
  if (!raw) return null
  try {
    return JSON.parse(raw) as AuthUser
  } catch {
    return null
  }
}

export function isAuthenticated(): boolean {
  return !!localStorage.getItem(TOKEN_KEY)
}

export function hasRole(role: string): boolean {
  return getCurrentUser()?.roles.includes(role) ?? false
}
