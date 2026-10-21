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
  if (req.auth!.role === Role.FIELD_PERSON) {
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
  // Optional — when provided by a manager, creates/updates the plan FOR
  // that user (must be a descendant). Omitted = creates for the caller.
  userId: z.string().optional(),
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

/** True iff `targetId` is `managerId` or anywhere in that manager's subtree. */
async function isDescendant(companyId: string, managerId: string, targetId: string): Promise<boolean> {
  if (managerId === targetId) return true;
  const all = await prisma.user.findMany({
    where: { companyId, isActive: true },
    select: { id: true, managerId: true },
  });
  const subtree = new Set<string>()
  const walk = (m: string) => { for (const u of all) if (u.managerId === m) { subtree.add(u.id); walk(u.id); } }
  walk(managerId);
  return subtree.has(targetId);
}

router.post('/', async (req, res) => {
  const parsed = planSchema.safeParse(req.body);
  if (!parsed.success) return res.status(400).json({ error: 'Invalid input' });
  const { userId: bodyUserId, date, notes, entries } = parsed.data;

  // Resolve the plan's owner — self by default, otherwise a descendant.
  const targetUserId = bodyUserId ?? req.auth!.userId;
  if (targetUserId !== req.auth!.userId) {
    if (req.auth!.role === Role.FIELD_PERSON) {
      return res.status(403).json({ error: 'FIELD_PERSON_CANNOT_PLAN_FOR_OTHERS' });
    }
    const allowed = await isDescendant(req.auth!.companyId, req.auth!.userId, targetUserId);
    if (!allowed) return res.status(403).json({ error: 'TARGET_NOT_IN_SUBTREE' });
  }

  // Plans a manager creates for a report start as APPROVED (manager is the
  // approver anyway); self-created plans still start as PLANNED.
  const status = targetUserId !== req.auth!.userId
    ? TourPlanStatus.APPROVED
    : TourPlanStatus.PLANNED;

  const plan = await prisma.tourPlan.upsert({
    where: { userId_date: { userId: targetUserId, date: new Date(date) } },
    update: { notes, status, approvedBy: targetUserId !== req.auth!.userId ? req.auth!.userId : null },
    create: {
      userId: targetUserId,
      date: new Date(date),
      notes,
      status,
      approvedBy: targetUserId !== req.auth!.userId ? req.auth!.userId : null,
    },
  });

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
router.patch('/:id/review', requireRole(Role.ADMIN, Role.SENIOR_MANAGER, Role.REGIONAL_MANAGER), async (req, res) => {
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
