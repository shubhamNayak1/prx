import { Router } from 'express';
import { Role, ExpenseStatus, TourPlanStatus } from '@prisma/client';
import { prisma } from '../lib/prisma';
import { requireAuth } from '../middleware/auth';

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
  if (req.auth!.role === Role.MR) {
    ids.add(req.auth!.userId);
  } else if (req.auth!.role === Role.MANAGER) {
    const collect = (m: string) => {
      for (const u of all) if (u.managerId === m) { ids.add(u.id); collect(u.id); }
    };
    collect(req.auth!.userId);
  } else {
    // Admin sees the whole company
    for (const u of all) ids.add(u.id);
  }
  const idList = Array.from(ids);
  const mrIds = all.filter((u) => u.role === Role.MR && idList.includes(u.id)).map((u) => u.id);

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
  if (req.auth!.role === Role.MR) ids.add(req.auth!.userId);
  else if (req.auth!.role === Role.MANAGER) {
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

export default router;
