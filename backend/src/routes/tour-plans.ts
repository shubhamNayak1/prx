import { Router } from 'express';
import { z } from 'zod';
import { Role, TourPlanStatus } from '@prisma/client';
import { prisma } from '../lib/prisma';
import { requireAuth, requireRole } from '../middleware/auth';

const router = Router();
router.use(requireAuth);

// MR: own plans for date range. Manager/Admin: their downstream MRs' plans.
router.get('/', async (req, res) => {
  const { from, to, userId } = req.query as { from?: string; to?: string; userId?: string };
  const dateFilter =
    from && to
      ? { date: { gte: new Date(from), lte: new Date(to) } }
      : {};

  let userFilter: any;
  if (req.auth!.role === Role.MR) {
    userFilter = { userId: req.auth!.userId };
  } else if (userId) {
    userFilter = { userId };
  } else {
    // Manager: descendants
    const all = await prisma.user.findMany({
      where: { companyId: req.auth!.companyId, isActive: true },
      select: { id: true, managerId: true },
    });
    const ids = new Set<string>([req.auth!.userId]);
    const collect = (m: string) => {
      for (const u of all) if (u.managerId === m) { ids.add(u.id); collect(u.id); }
    };
    collect(req.auth!.userId);
    userFilter = { userId: { in: Array.from(ids) } };
  }

  const plans = await prisma.tourPlan.findMany({
    where: { ...userFilter, ...dateFilter },
    orderBy: [{ date: 'asc' }],
    include: {
      user: { select: { id: true, name: true, employeeCode: true } },
      entries: { include: { client: { select: { id: true, name: true, type: true } } } },
    },
  });
  res.json({ plans });
});

const planSchema = z.object({
  date: z.string().regex(/^\d{4}-\d{2}-\d{2}$/),
  notes: z.string().optional(),
  entries: z.array(
    z.object({
      clientId: z.string().optional(),
      area: z.string().optional(),
      notes: z.string().optional(),
    }),
  ),
});

router.post('/', async (req, res) => {
  const parsed = planSchema.safeParse(req.body);
  if (!parsed.success) return res.status(400).json({ error: 'Invalid input' });
  const { date, notes, entries } = parsed.data;

  const plan = await prisma.tourPlan.upsert({
    where: { userId_date: { userId: req.auth!.userId, date: new Date(date) } },
    update: { notes, status: TourPlanStatus.PLANNED },
    create: { userId: req.auth!.userId, date: new Date(date), notes },
  });

  // Replace entries
  await prisma.tourPlanEntry.deleteMany({ where: { tourPlanId: plan.id } });
  if (entries.length > 0) {
    await prisma.tourPlanEntry.createMany({
      data: entries.map((e) => ({ tourPlanId: plan.id, ...e })),
    });
  }

  const full = await prisma.tourPlan.findUnique({
    where: { id: plan.id },
    include: { entries: { include: { client: true } } },
  });
  res.status(201).json({ plan: full });
});

// Manager/Admin: approve or reject
const reviewSchema = z.object({ status: z.enum(['APPROVED', 'REJECTED']) });
router.patch('/:id/review', requireRole(Role.ADMIN, Role.MANAGER), async (req, res) => {
  const parsed = reviewSchema.safeParse(req.body);
  if (!parsed.success) return res.status(400).json({ error: 'Invalid input' });

  const plan = await prisma.tourPlan.findFirst({
    where: { id: req.params.id },
    include: { user: true },
  });
  if (!plan || plan.user.companyId !== req.auth!.companyId) {
    return res.status(404).json({ error: 'Not found' });
  }

  const updated = await prisma.tourPlan.update({
    where: { id: plan.id },
    data: { status: parsed.data.status, approvedBy: req.auth!.userId },
  });
  res.json({ plan: updated });
});

export default router;
