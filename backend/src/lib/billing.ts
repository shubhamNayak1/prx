import { BillingCycle } from '@prisma/client';
import { prisma } from './prisma';
import { BILLABLE_ROLES } from './roles';

export const TRIAL_DAYS = 7;
export const TRIAL_REP_CAP = 5;
export const DEFAULT_PRICE_PER_REP_PAISE = 49900; // ₹499
export const GST_PCT = 18;

export const CYCLE_MONTHS: Record<BillingCycle, number> = {
  MONTHLY: 1,
  QUARTERLY: 3,
  ANNUAL: 12,
};

export const CYCLE_DISCOUNT_PCT: Record<BillingCycle, number> = {
  MONTHLY: 0,
  QUARTERLY: 10,
  ANNUAL: 20,
};

export function addDays(d: Date, n: number): Date {
  const r = new Date(d);
  r.setDate(r.getDate() + n);
  return r;
}

export function addMonths(d: Date, n: number): Date {
  const r = new Date(d);
  r.setMonth(r.getMonth() + n);
  return r;
}

export type PriceBreakdown = {
  activeReps: number;
  months: number;
  pricePerRepPaise: number;
  discountPct: number;
  subtotalPaise: number;
  gstPaise: number;
  totalPaise: number;
};

export function priceFor(cycle: BillingCycle, activeReps: number, pricePerRepPaise: number): PriceBreakdown {
  const months = CYCLE_MONTHS[cycle];
  const discountPct = CYCLE_DISCOUNT_PCT[cycle];
  const gross = pricePerRepPaise * activeReps * months;
  const subtotal = Math.round((gross * (100 - discountPct)) / 100);
  const gst = Math.round((subtotal * GST_PCT) / 100);
  const total = subtotal + gst;
  return {
    activeReps,
    months,
    pricePerRepPaise,
    discountPct,
    subtotalPaise: subtotal,
    gstPaise: gst,
    totalPaise: total,
  };
}

/**
 * "Active rep" = any user in the company (role MANAGER or MR) who had attendance
 * in the last `lookbackDays`. Admins are never billed. The company gets at least
 * one billable seat so a dormant tenant still generates a tiny invoice.
 */
export async function countActiveReps(companyId: string, lookbackDays: number): Promise<number> {
  const since = addDays(new Date(), -lookbackDays);
  const rows = await prisma.user.findMany({
    where: {
      companyId,
      role: { in: BILLABLE_ROLES },
      isActive: true,
      attendance: { some: { date: { gte: since } } },
    },
    select: { id: true },
  });
  return Math.max(1, rows.length);
}
