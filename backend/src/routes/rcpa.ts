import { Router } from 'express';
import { z } from 'zod';
import { Role } from '@prisma/client';
import { prisma } from '../lib/prisma';
import { requireAuth } from '../middleware/auth';

const router = Router();
router.use(requireAuth);

const rcpaSchema = z.object({
  clientId: z.string(),
  date: z.string().regex(/^\d{4}-\d{2}-\d{2}$/),
  ourBrand: z.string().min(1),
  ourQuantity: z.number().int().nonnegative(),
  competitorBrand: z.string().min(1),
  competitorQuantity: z.number().int().nonnegative(),
  remarks: z.string().optional(),
  actionLat: z.number().optional(),
  actionLng: z.number().optional(),
});

router.post('/', async (req, res) => {
  const parsed = rcpaSchema.safeParse(req.body);
  if (!parsed.success) return res.status(400).json({ error: 'Invalid input' });

  const client = await prisma.client.findFirst({
    where: { id: parsed.data.clientId, companyId: req.auth!.companyId },
  });
  if (!client) return res.status(404).json({ error: 'Client not found' });

  const entry = await prisma.rcpaEntry.create({
    data: {
      ...parsed.data,
      date: new Date(parsed.data.date),
      userId: req.auth!.userId,
    },
  });
  res.status(201).json({ entry });
});

router.get('/', async (req, res) => {
  const { from, to, userId } = req.query as { from?: string; to?: string; userId?: string };
  const where: any = {};
  if (from && to) where.date = { gte: new Date(from), lte: new Date(to) };

  if (req.auth!.role === Role.MR) {
    where.userId = req.auth!.userId;
  } else if (userId) {
    where.userId = userId;
  } else {
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

  const entries = await prisma.rcpaEntry.findMany({
    where,
    orderBy: { date: 'desc' },
    include: {
      client: { select: { id: true, name: true } },
      user: { select: { id: true, name: true } },
    },
    take: 500,
  });
  res.json({ entries });
});

// Market share aggregation (manager view)
router.get('/market-share', async (req, res) => {
  const all = await prisma.user.findMany({
    where: { companyId: req.auth!.companyId, isActive: true },
    select: { id: true, managerId: true },
  });
  const ids = new Set<string>();
  if (req.auth!.role === Role.MR) {
    ids.add(req.auth!.userId);
  } else {
    const collect = (m: string) => {
      for (const u of all) if (u.managerId === m) { ids.add(u.id); collect(u.id); }
    };
    collect(req.auth!.userId);
  }
  if (ids.size === 0) return res.json({ ours: [], competitors: [] });

  const entries = await prisma.rcpaEntry.findMany({
    where: { userId: { in: Array.from(ids) } },
    select: { ourBrand: true, ourQuantity: true, competitorBrand: true, competitorQuantity: true },
    take: 5000,
  });

  const tally = (key: 'ours' | 'comp', brand: string, qty: number, map: Record<string, number>) => {
    map[brand] = (map[brand] ?? 0) + qty;
  };
  const oursMap: Record<string, number> = {};
  const compsMap: Record<string, number> = {};
  for (const e of entries) {
    tally('ours', e.ourBrand, e.ourQuantity, oursMap);
    tally('comp', e.competitorBrand, e.competitorQuantity, compsMap);
  }
  const sorted = (m: Record<string, number>) =>
    Object.entries(m).map(([brand, units]) => ({ brand, units })).sort((a, b) => b.units - a.units);

  res.json({ ours: sorted(oursMap), competitors: sorted(compsMap) });
});

export default router;
