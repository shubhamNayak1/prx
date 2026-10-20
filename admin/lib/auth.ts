'use client';

const TOKEN_KEY = 'fp_token';
const USER_KEY = 'fp_user';

export type Role = 'ADMIN' | 'SENIOR_MANAGER' | 'REGIONAL_MANAGER' | 'FIELD_PERSON';

export const MANAGER_ROLES: Role[] = ['SENIOR_MANAGER', 'REGIONAL_MANAGER'];
export const BILLABLE_ROLES: Role[] = [...MANAGER_ROLES, 'FIELD_PERSON'];
export const APPROVER_ROLES: Role[] = ['ADMIN', ...MANAGER_ROLES];

export const ROLE_LABEL: Record<Role, string> = {
  ADMIN: 'Admin',
  SENIOR_MANAGER: 'Senior Manager',
  REGIONAL_MANAGER: 'Regional Manager',
  FIELD_PERSON: 'Field Person',
};

export type AuthUser = {
  id: string;
  email: string;
  name: string;
  role: Role;
  companyId: string;
};

export function saveAuth(token: string, user: AuthUser) {
  localStorage.setItem(TOKEN_KEY, token);
  localStorage.setItem(USER_KEY, JSON.stringify(user));
}

export function clearAuth() {
  localStorage.removeItem(TOKEN_KEY);
  localStorage.removeItem(USER_KEY);
}

export function getToken(): string | null {
  if (typeof window === 'undefined') return null;
  return localStorage.getItem(TOKEN_KEY);
}

export function getUser(): AuthUser | null {
  if (typeof window === 'undefined') return null;
  const raw = localStorage.getItem(USER_KEY);
  return raw ? JSON.parse(raw) : null;
}
