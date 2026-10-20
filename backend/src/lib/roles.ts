import { Role } from '@prisma/client';

/** Both manager levels — they approve, review and see downstream reports. */
export const MANAGER_ROLES: Role[] = [Role.SENIOR_MANAGER, Role.REGIONAL_MANAGER];

/** Every non-admin role. These are the people the subscription bills for. */
export const BILLABLE_ROLES: Role[] = [...MANAGER_ROLES, Role.FIELD_PERSON];

/** Roles that approve things (new clients, tour plans, expenses, DCR edits). */
export const APPROVER_ROLES: Role[] = [Role.ADMIN, ...MANAGER_ROLES];

export function isManager(r: Role): boolean {
  return r === Role.SENIOR_MANAGER || r === Role.REGIONAL_MANAGER;
}

export function isFieldPerson(r: Role): boolean {
  return r === Role.FIELD_PERSON;
}

export function isApprover(r: Role): boolean {
  return APPROVER_ROLES.includes(r);
}

/**
 * Hierarchy rule: a user with role `childRole` may report to a user with
 * role `parentRole`. Admin reports to nobody (null manager). Field Person
 * cannot be anyone's manager. Everything in between is flexible — small
 * companies can skip the Senior Manager tier.
 */
export function canReportTo(childRole: Role, parentRole: Role): boolean {
  // Field Person is never a parent.
  if (parentRole === Role.FIELD_PERSON) return false;
  // Admin is top; cannot report to anyone.
  if (childRole === Role.ADMIN) return false;
  // Senior Manager reports to Admin.
  if (childRole === Role.SENIOR_MANAGER) return parentRole === Role.ADMIN;
  // Regional Manager reports to Senior Manager or Admin.
  if (childRole === Role.REGIONAL_MANAGER) {
    return parentRole === Role.SENIOR_MANAGER || parentRole === Role.ADMIN;
  }
  // Field Person reports to any manager role or Admin.
  if (childRole === Role.FIELD_PERSON) {
    return parentRole === Role.ADMIN || isManager(parentRole);
  }
  return false;
}
