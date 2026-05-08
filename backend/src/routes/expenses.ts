import { Router } from 'express';
import { z } from 'zod';
import { Role, ExpenseType, ExpenseStatus } from '@prisma/client';
import { prisma } from '../lib/prisma';
import { requireAuth, requireRole } from '../middleware/auth';

const router = Router();
router.use(requireAuth);

const expenseSchema = z.object({
  date: z.string().regex(/^\d{4}-\d{2}-\d{2}$/),
  type: z.nativeEnum(ExpenseType),
  category: z.string().optional(),
  amount: z.number().nonnegative(),
  fromLocation: z.string().optional(),
  toLocation: z.string().optional(),
  distanceKm: z.number().nonnegative().optional(),
  modeOfTravel: z.string().optional(),
  billPhoto: z.string().optional(),
  remarks: z.string().optional(),
  actionLat: z.number().optional(),
  actionLng: z.number().optional(),
});

router.post('/', async (req, res) => {
  const parsed = expenseSchema.safeParse(req.body);
  if (!parsed.success) {
    return res.status(400).json({ error: 'Invalid input', details: parsed.error.flatten() });
  }
  const data = parsed.data;
  const expense = await prisma.expense.create({
    data: {
      userId: req.auth!.userId,
      date: new Date(data.date),
      type: data.type,
      category: data.category,
      amount: data.amount,
      fromLocation: data.fromLocation,
      toLocation: data.toLocation,
      distanceKm: data.distanceKm,
      modeOfTravel: data.modeOfTravel,
      billPhoto: data.billPhoto,
      remarks: data.remarks,
      actionLat: data.actionLat,
      actionLng: data.actionLng,
    },
  });
  res.status(201).json({ expense });
});

router.get('/', async (req, res) => {
  const { from, to, status, userId } = req.query as {
    from?: string; to?: string; status?: ExpenseStatus; userId?: string;
  };

  const where: any = {};
  if (from && to) where.date = { gte: new Date(from), lte: new Date(to) };
  if (status) where.status = status;

  if (req.auth!.role === Role.MR) {
    where.userId = req.auth!.userId;
  } else if (userId) {
    where.userId = userId;
  } else {
    // Manager: descendants
    const all = await prisma.user.findMany({
      where: { companyId: req.auth!.companyId, isActive: true },
      select: { id: true, managerId: true },
    });
    const ids = new Set<string>();
    const collect = (m: string) => {
      for (const u of all) if (u.managerId === m) { ids.add(u.id); collect(u.id); }
    };
    collect(req.auth!.userId);
    where.userId = { in: Array.from(ids) };
  }

  const expenses = await prisma.expense.findMany({
    where,
    orderBy: { date: 'desc' },
    include: { user: { select: { id: true, name: true, employeeCode: true } } },
    take: 500,
  });
  res.json({ expenses });
});

const reviewSchema = z.object({
  status: z.enum(['APPROVED', 'REJECTED']),
  remarks: z.string().optional(),
});

router.patch('/:id/review', requireRole(Role.ADMIN, Role.MANAGER), async (req, res) => {
  const parsed = reviewSchema.safeParse(req.body);
  if (!parsed.success) return res.status(400).json({ error: 'Invalid input' });

  const expense = await prisma.expense.findFirst({
    where: { id: req.params.id },
    include: { user: true },
  });
  if (!expense || expense.user.companyId !== req.auth!.companyId) {
    return res.status(404).json({ error: 'Not found' });
  }

  const updated = await prisma.expense.update({
    where: { id: expense.id },
    data: {
      status: parsed.data.status,
      approvedBy: req.auth!.userId,
      remarks: parsed.data.remarks ?? expense.remarks,
    },
  });
  res.json({ expense: updated });
});

export default router;
