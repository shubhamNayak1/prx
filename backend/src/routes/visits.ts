import { Router } from 'express';
import { z } from 'zod';
import { Role } from '@prisma/client';
import { prisma } from '../lib/prisma';
import { requireAuth } from '../middleware/auth';

const router = Router();
router.use(requireAuth);

// Single endpoint to record a visit (offline-friendly: sent in full on check-out)
const visitSchema = z.object({
  clientId: z.string(),
  checkInAt: z.string().datetime(),
  checkInLat: z.number().optional(),
  checkInLng: z.number().optional(),
  checkOutAt: z.string().datetime().optional(),
  checkOutLat: z.number().optional(),
  checkOutLng: z.number().optional(),
  isJointWork: z.boolean().optional(),
  jointWithUserId: z.string().optional(),
  productsDiscussed: z.array(z.string()).optional(),
  notes: z.string().optional(),
});

router.post('/', async (req, res) => {
  const parsed = visitSchema.safeParse(req.body);
  if (!parsed.success) {
    return res.status(400).json({ error: 'Invalid input', details: parsed.error.flatten() });
  }
  const data = parsed.data;

  const client = await prisma.client.findFirst({
    where: { id: data.clientId, companyId: req.auth!.companyId },
  });
  if (!client) return res.status(404).json({ error: 'Client not found' });

  const visit = await prisma.visit.create({
    data: {
      userId: req.auth!.userId,
      clientId: data.clientId,
      checkInAt: new Date(data.checkInAt),
      checkInLat: data.checkInLat,
      checkInLng: data.checkInLng,
      checkOutAt: data.checkOutAt ? new Date(data.checkOutAt) : null,
      checkOutLat: data.checkOutLat,
      checkOutLng: data.checkOutLng,
      isJointWork: data.isJointWork ?? false,
      jointWithUserId: data.jointWithUserId,
      productsDiscussed: data.productsDiscussed ?? [],
      notes: data.notes,
    },
  });
  res.status(201).json({ visit });
});

router.get('/', async (req, res) => {
  const { from, to, userId } = req.query as { from?: string; to?: string; userId?: string };
  const where: any = {};
  if (from && to) where.checkInAt = { gte: new Date(from), lte: new Date(to) };

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

  const visits = await prisma.visit.findMany({
    where,
    orderBy: { checkInAt: 'desc' },
    include: {
      client: { select: { id: true, name: true, type: true } },
      user: { select: { id: true, name: true } },
    },
    take: 500,
  });
  res.json({ visits });
});

export default router;
