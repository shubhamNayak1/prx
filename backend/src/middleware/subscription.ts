import { Request, Response, NextFunction } from 'express';
import { Role } from '@prisma/client';
import { prisma } from '../lib/prisma';
import { TRIAL_REP_CAP } from '../lib/billing';
import { BILLABLE_ROLES } from '../lib/roles';

/**
 * Blocks writes when the tenant's subscription is LOCKED or CANCELLED.
 * Reads continue to work so admins can log in, review data, and pay.
 * Attach after requireAuth on write routes that need gating.
 */
export async function requireActiveSubscription(req: Request, res: Response, next: NextFunction) {
  if (!req.auth) return res.status(401).json({ error: 'Unauthenticated' });
  const sub = await prisma.subscription.findUnique({
    where: { companyId: req.auth.companyId },
    select: { status: true },
  });
  if (!sub) return res.status(402).json({ error: 'NO_SUBSCRIPTION' });
  if (sub.status === 'LOCKED' || sub.status === 'CANCELLED') {
    return res.status(402).json({ error: 'SUBSCRIPTION_LOCKED', status: sub.status });
  }
  next();
}

/**
 * While the tenant is on TRIAL, caps billable users (MR + MANAGER) at
 * TRIAL_REP_CAP. Must be mounted BEFORE the user-creation handler.
 */
export async function enforceTrialRepCap(req: Request, res: Response, next: NextFunction) {
  if (!req.auth) return res.status(401).json({ error: 'Unauthenticated' });

  const role = req.body?.role as Role | undefined;
  if (!role || !BILLABLE_ROLES.includes(role)) return next();

  const sub = await prisma.subscription.findUnique({
    where: { companyId: req.auth.companyId },
    select: { status: true },
  });
  if (!sub || sub.status !== 'TRIAL') return next();

  const count = await prisma.user.count({
    where: {
      companyId: req.auth.companyId,
      isActive: true,
      role: { in: BILLABLE_ROLES },
    },
  });
  if (count >= TRIAL_REP_CAP) {
    return res.status(402).json({
      error: 'TRIAL_REP_CAP',
      message: `Trial is limited to ${TRIAL_REP_CAP} field users. Upgrade to add more.`,
      cap: TRIAL_REP_CAP,
    });
  }
  next();
}
