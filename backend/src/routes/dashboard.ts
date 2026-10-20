import { Router } from 'express';
import { Role, ExpenseStatus, TourPlanStatus } from '@prisma/client';
import { prisma } from '../lib/prisma';
import { requireAuth } from '../middleware/auth';
import { BILLABLE_ROLES, isManager } from '../lib/roles';

const router = Router();
router.use(requireAuth);

// 360° summary for manager/admin (or own-data for MR)
router.get('/summary', async (req, res) => {
  // Pick downstream user IDs
  const all = await prisma.user.findMany({
    where: { companyId: req.auth!.companyId, isActive: true },
    select: { id: true, managerId: true, role: true },
  });
  const ids = new Set<string>();
  if (req.auth!.role === Role.FIELD_PERSON) {
    ids.add(req.auth!.userId);
  } else if (isManager(req.auth!.role)) {
    // Walks the full descendant tree — Senior Manager picks up both
    // Regional Managers and their Field Persons automatically.
    const collect = (m: string) => {
      for (const u of all) if (u.managerId === m) { ids.add(u.id); collect(u.id); }
    };
    collect(req.auth!.userId);
  } else {
    // Admin sees the whole company
    for (const u of all) ids.add(u.id);
  }
  const idList = Array.from(ids);
  const mrIds = all.filter((u) => u.role === Role.FIELD_PERSON && idList.includes(u.id)).map((u) => u.id);

  const today = new Date(); today.setHours(0, 0, 0, 0);
  const monthStart = new Date(today.getFullYear(), today.getMonth(), 1);
  const monthEnd = new Date(today.getFullYear(), today.getMonth() + 1, 1);

  const [
    todayAttendance,
    monthVisits,
    monthPlannedEntries,
    pendingExpenses,
    monthExpenses,
    pendingPlans,
  ] = await Promise.all([
    prisma.attendance.count({
      where: { userId: { in: mrIds }, date: today, punchInAt: { not: null } },
    }),
    prisma.visit.count({
      where: { userId: { in: idList }, checkInAt: { gte: monthStart, lt: monthEnd } },
    }),
    prisma.tourPlanEntry.count({
      where: {
        tourPlan: {
          userId: { in: idList },
          date: { gte: monthStart, lt: monthEnd },
          status: { in: [TourPlanStatus.APPROVED, TourPlanStatus.COMPLETED] },
        },
      },
    }),
    prisma.expense.aggregate({
      where: { userId: { in: idList }, status: ExpenseStatus.PENDING },
      _count: { _all: true },
      _sum: { amount: true },
    }),
    prisma.expense.aggregate({
      where: { userId: { in: idList }, date: { gte: monthStart, lt: monthEnd } },
      _sum: { amount: true },
    }),
    prisma.tourPlan.count({
      where: { userId: { in: idList }, status: TourPlanStatus.PLANNED },
    }),
  ]);

  res.json({
    counts: {
      mrTotal: mrIds.length,
      todayPunchedIn: todayAttendance,
      monthVisits,
      monthPlanned: monthPlannedEntries,
      attainmentPct:
        monthPlannedEntries > 0
          ? Math.round((monthVisits / monthPlannedEntries) * 1000) / 10
          : null,
      pendingExpensesCount: pendingExpenses._count._all,
      pendingExpensesAmount: Number(pendingExpenses._sum.amount ?? 0),
      monthExpensesAmount: Number(monthExpenses._sum.amount ?? 0),
      pendingTourPlans: pendingPlans,
    },
  });
});

// Daily visits over last 30 days (for line chart)
router.get('/visits-trend', async (req, res) => {
  const all = await prisma.user.findMany({
    where: { companyId: req.auth!.companyId, isActive: true },
    select: { id: true, managerId: true, role: true },
  });
  const ids = new Set<string>();
  if (req.auth!.role === Role.FIELD_PERSON) ids.add(req.auth!.userId);
  else if (isManager(req.auth!.role)) {
    const collect = (m: string) => {
      for (const u of all) if (u.managerId === m) { ids.add(u.id); collect(u.id); }
    };
    collect(req.auth!.userId);
  } else {
    for (const u of all) ids.add(u.id);
  }

  const since = new Date();
  since.setDate(since.getDate() - 29);
  since.setHours(0, 0, 0, 0);

  const visits = await prisma.visit.findMany({
    where: { userId: { in: Array.from(ids) }, checkInAt: { gte: since } },
    select: { checkInAt: true },
  });

  // Bucket by day
  const buckets: Record<string, number> = {};
  for (let i = 0; i < 30; i++) {
    const d = new Date(since); d.setDate(d.getDate() + i);
    buckets[d.toISOString().slice(0, 10)] = 0;
  }
  for (const v of visits) {
    const k = v.checkInAt.toISOString().slice(0, 10);
    if (k in buckets) buckets[k] += 1;
  }

  res.json({
    trend: Object.entries(buckets).map(([date, count]) => ({ date, count })),
  });
});

// ─────────────── Role-aware KPIs + team rollup for mobile homes ───────────────
//
// Admin           : company-wide KPIs; `team` = top-level managers under Admin.
// Senior Manager  : scoped to their subtree; `team` = direct Regional Managers
//                   with each one's region rollup.
// Regional Manager: scoped to their subtree; `team` = direct Field Persons
//                   with each one's attendance + today's calls.
// Field Person    : own data only; `team` empty.
router.get('/role-summary', async (req, res) => {
  const companyId = req.auth!.companyId;
  const userId = req.auth!.userId;
  const role = req.auth!.role;

  type U = { id: string; name: string; managerId: string | null; role: Role; grade: string | null };
  const allUsers: U[] = await prisma.user.findMany({
    where: { companyId, isActive: true },
    select: { id: true, name: true, managerId: true, role: true, grade: true },
  });

  const scopeIds = new Set<string>();
  const directReports: U[] = [];
  if (role === Role.FIELD_PERSON) {
    scopeIds.add(userId);
  } else if (isManager(role)) {
    const collect = (m: string) => {
      for (const u of allUsers) if (u.managerId === m) {
        scopeIds.add(u.id);
        if (u.managerId === userId) directReports.push(u);
        collect(u.id);
      }
    };
    collect(userId);
  } else {
    for (const u of allUsers) scopeIds.add(u.id);
  }

  const idList = Array.from(scopeIds);
  const fieldPersonIds = allUsers
    .filter((u) => u.role === Role.FIELD_PERSON && scopeIds.has(u.id))
    .map((u) => u.id);

  // Admin needs the whole company's billable headcount for the subscription
  // tile. For other roles it's the billable users within their subtree.
  const billableUsers = allUsers.filter(
    (u) => BILLABLE_ROLES.includes(u.role) && (role === Role.ADMIN || scopeIds.has(u.id)),
  ).length;

  const today = new Date(); today.setHours(0, 0, 0, 0);
  const tomorrow = new Date(today); tomorrow.setDate(today.getDate() + 1);
  const monthStart = new Date(today.getFullYear(), today.getMonth(), 1);
  const monthEnd = new Date(today.getFullYear(), today.getMonth() + 1, 1);

  const [
    activeTodayCount,
    callsToday,
    callsPlannedToday,
    monthVisits,
    monthPlanned,
    pendingExpAgg,
    monthExpAgg,
    pendingPlans,
  ] = await Promise.all([
    prisma.attendance.count({
      where: { userId: { in: fieldPersonIds }, date: today, punchInAt: { not: null } },
    }),
    prisma.visit.count({
      where: { userId: { in: idList }, checkInAt: { gte: today, lt: tomorrow } },
    }),
    prisma.tourPlanEntry.count({
      where: {
        tourPlan: {
          userId: { in: idList },
          date: today,
          status: { in: [TourPlanStatus.APPROVED, TourPlanStatus.COMPLETED, TourPlanStatus.PLANNED] },
        },
      },
    }),
    prisma.visit.count({
      where: { userId: { in: idList }, checkInAt: { gte: monthStart, lt: monthEnd } },
    }),
    prisma.tourPlanEntry.count({
      where: {
        tourPlan: {
          userId: { in: idList },
          date: { gte: monthStart, lt: monthEnd },
          status: { in: [TourPlanStatus.APPROVED, TourPlanStatus.COMPLETED] },
        },
      },
    }),
    prisma.expense.aggregate({
      where: { userId: { in: idList }, status: ExpenseStatus.PENDING },
      _count: { _all: true },
      _sum: { amount: true },
    }),
    prisma.expense.aggregate({
      where: { userId: { in: idList }, date: { gte: monthStart, lt: monthEnd } },
      _sum: { amount: true },
    }),
    prisma.tourPlan.count({
      where: { userId: { in: idList }, status: TourPlanStatus.PLANNED },
    }),
  ]);

  const attainmentPct = monthPlanned > 0 ? Math.round((monthVisits / monthPlanned) * 100) : 0;

  // ─── Team rollup ───
  type TeamEntry = {
    id: string; name: string; role: Role; grade: string | null;
    activeToday: number;        // field persons active in subtree (for managers)
    totalReps: number;          // field persons in subtree (for managers)
    callsToday: number;         // today's visits in subtree
    callsPlanned: number;       // today's planned entries in subtree
    monthVisits: number;
    monthPlanned: number;
    attainmentPct: number;
    status: 'ActiveToday' | 'NotPunched' | 'OffField' | 'Flagged';
  };
  const team: TeamEntry[] = [];

  if (role === Role.ADMIN) {
    // Admin's "team" = direct managers (SR/REG) they can click into.
    const admins = allUsers.filter((u) => u.managerId == null && u.id !== userId);
    const topLevelManagers = allUsers.filter((u) =>
      (u.role === Role.SENIOR_MANAGER || u.role === Role.REGIONAL_MANAGER) &&
      (u.managerId == null || admins.some((a) => a.id === u.managerId) || u.managerId === userId),
    );
    for (const dr of topLevelManagers) team.push(await rollup(dr, allUsers, today, monthStart, monthEnd));
  } else if (isManager(role)) {
    for (const dr of directReports) team.push(await rollup(dr, allUsers, today, monthStart, monthEnd));
  }

  res.json({
    role,
    kpis: {
      totalUsers: idList.length,
      totalReps: fieldPersonIds.length,
      activeToday: activeTodayCount,
      callsToday,
      callsPlannedToday,
      monthVisits,
      monthPlanned,
      attainmentPct,
      pendingTourPlans: pendingPlans,
      pendingExpensesCount: pendingExpAgg._count._all,
      pendingExpensesAmount: Number(pendingExpAgg._sum.amount ?? 0),
      monthSpendInr: Number(monthExpAgg._sum.amount ?? 0),
      pendingClients: 0, // TODO once Client.status is added
      billableUsers,
    },
    team,
  });
});

async function rollup(
  u: { id: string; name: string; role: Role; grade: string | null },
  all: Array<{ id: string; managerId: string | null; role: Role }>,
  today: Date,
  monthStart: Date,
  monthEnd: Date,
) {
  const sub = new Set<string>([u.id]);
  const collect = (m: string) => {
    for (const x of all) if (x.managerId === m) { sub.add(x.id); collect(x.id); }
  };
  collect(u.id);
  const subFieldIds = all.filter((x) => x.role === Role.FIELD_PERSON && sub.has(x.id)).map((x) => x.id);

  const tomorrow = new Date(today); tomorrow.setDate(today.getDate() + 1);
  const [activeToday, callsToday, callsPlanned, mv, mp, flaggedCount] = await Promise.all([
    prisma.attendance.count({ where: { userId: { in: subFieldIds }, date: today, punchInAt: { not: null } } }),
    prisma.visit.count({ where: { userId: { in: Array.from(sub) }, checkInAt: { gte: today, lt: tomorrow } } }),
    prisma.tourPlanEntry.count({ where: { tourPlan: { userId: { in: Array.from(sub) }, date: today, status: { in: [TourPlanStatus.APPROVED, TourPlanStatus.COMPLETED, TourPlanStatus.PLANNED] } } } }),
    prisma.visit.count({ where: { userId: { in: Array.from(sub) }, checkInAt: { gte: monthStart, lt: monthEnd } } }),
    prisma.tourPlanEntry.count({ where: { tourPlan: { userId: { in: Array.from(sub) }, date: { gte: monthStart, lt: monthEnd }, status: { in: [TourPlanStatus.APPROVED, TourPlanStatus.COMPLETED] } } } }),
    // Minimal flagging: expenses rejected this month
    prisma.expense.count({ where: { userId: { in: Array.from(sub) }, status: ExpenseStatus.REJECTED, date: { gte: monthStart, lt: monthEnd } } }),
  ]);

  const status: 'ActiveToday' | 'NotPunched' | 'OffField' | 'Flagged' =
    flaggedCount > 0 ? 'Flagged'
    : u.role === Role.FIELD_PERSON
      ? (activeToday > 0 ? 'ActiveToday' : 'NotPunched')
      : (activeToday > 0 ? 'ActiveToday' : 'OffField');

  return {
    id: u.id,
    name: u.name,
    role: u.role,
    grade: u.grade,
    activeToday,
    totalReps: subFieldIds.length,
    callsToday,
    callsPlanned,
    monthVisits: mv,
    monthPlanned: mp,
    attainmentPct: mp > 0 ? Math.round((mv / mp) * 100) : 0,
    status,
  };
}

export default router;
