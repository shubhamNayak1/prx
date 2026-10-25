import { Router } from 'express';
import { z } from 'zod';
import { Role } from '@prisma/client';
import { prisma } from '../lib/prisma';
import { requireAuth } from '../middleware/auth';

const router = Router();
router.use(requireAuth);

const dateOnly = (iso: string) => {
  // Parse "YYYY-MM-DD" into a UTC midnight Date suitable for @db.Date columns.
  const [y, m, d] = iso.split('-').map(Number);
  return new Date(Date.UTC(y, (m || 1) - 1, d || 1));
};

const upsertSchema = z.object({
  date: z.string().regex(/^\d{4}-\d{2}-\d{2}$/),
  workTypeI: z.enum(['INDP', 'JOINT', 'MEETING']).optional().nullable(),
  workTypeII: z.enum(['LOCAL', 'EX', 'OS']).optional().nullable(),
  placeOfWork: z.string().optional().nullable(),
  baseTownOnOs: z.string().optional().nullable(),
  routeNoInSvl: z.string().optional().nullable(),
  kmTravelled: z.number().optional().nullable(),
  dfrNo: z.number().int().optional().nullable(),
});

// GET /api/dcr-days?from=YYYY-MM-DD&to=YYYY-MM-DD[&userId=…]
router.get('/', async (req, res) => {
  const { from, to, userId } = req.query as { from?: string; to?: string; userId?: string };

  let targetUserId = req.auth!.userId;
  if (userId && userId !== req.auth!.userId) {
    if (req.auth!.role === Role.FIELD_PERSON) {
      return res.status(403).json({ error: 'FIELD_PERSON_SELF_ONLY' });
    }
    // Managers / admin: must be in own company (admin can see anyone; manager
    // scope is enforced on the report route, not here — reads are cheap).
    const u = await prisma.user.findFirst({
      where: { id: userId, companyId: req.auth!.companyId },
      select: { id: true },
    });
    if (!u) return res.status(404).json({ error: 'User not found' });
    targetUserId = userId;
  }

  const where: any = { userId: targetUserId };
  if (from && to) where.date = { gte: dateOnly(from), lte: dateOnly(to) };

  const days = await prisma.dcrDay.findMany({
    where,
    orderBy: { date: 'asc' },
  });
  res.json({ days });
});

// PUT /api/dcr-days — upsert for the signed-in user and the given date.
router.put('/', async (req, res) => {
  const parsed = upsertSchema.safeParse(req.body);
  if (!parsed.success) {
    return res.status(400).json({ error: 'Invalid input', details: parsed.error.flatten() });
  }
  const { date, ...rest } = parsed.data;
  const d = dateOnly(date);
  const day = await prisma.dcrDay.upsert({
    where: { userId_date: { userId: req.auth!.userId, date: d } },
    update: rest as any,
    create: {
      userId: req.auth!.userId,
      date: d,
      ...(rest as any),
    },
  });
  res.json({ day });
});

export default router;
