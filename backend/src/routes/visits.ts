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
  pobValue: z.number().optional().nullable(),
  paymentCollected: z.number().optional().nullable(),
  expiryAmount: z.number().optional().nullable(),
  breakageAmount: z.number().optional().nullable(),
  overdueOutstanding: z.number().optional().nullable(),
  orderValue: z.number().optional().nullable(),
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
      pobValue: data.pobValue ?? null,
      paymentCollected: data.paymentCollected ?? null,
      expiryAmount: data.expiryAmount ?? null,
      breakageAmount: data.breakageAmount ?? null,
      overdueOutstanding: data.overdueOutstanding ?? null,
      orderValue: data.orderValue ?? null,
    },
  });
  res.status(201).json({ visit });
});

router.get('/', async (req, res) => {
  const { from, to, userId } = req.query as { from?: string; to?: string; userId?: string };
  const where: any = {};
  if (from && to) where.checkInAt = { gte: new Date(from), lte: new Date(to) };

  if (req.auth!.role === Role.FIELD_PERSON) {
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
      client: {
        select: {
          id: true, name: true, type: true,
          speciality: true, address: true, city: true, phone: true,
        },
      },
      user: { select: { id: true, name: true } },
    },
    take: 500,
  });
  res.json({ visits });
});

/**
 * Joint-work review. Only the manager that the Field Person tagged can
 * approve/reject, and only while the visit is still pending (jointWorkApproved
 * is null). Returns 403 otherwise.
 */
const jointReviewSchema = z.object({ approved: z.boolean() });
router.patch('/:id/joint-review', async (req, res) => {
  const parsed = jointReviewSchema.safeParse(req.body);
  if (!parsed.success) return res.status(400).json({ error: 'Invalid input' });

  const visit = await prisma.visit.findFirst({
    where: {
      id: req.params.id,
      user: { companyId: req.auth!.companyId },
    },
  });
  if (!visit) return res.status(404).json({ error: 'Visit not found' });
  if (!visit.isJointWork || !visit.jointWithUserId) {
    return res.status(400).json({ error: 'NOT_A_JOINT_WORK_VISIT' });
  }
  if (visit.jointWithUserId !== req.auth!.userId) {
    return res.status(403).json({ error: 'NOT_THE_TAGGED_MANAGER' });
  }
  if (visit.jointWorkApproved !== null) {
    return res.status(400).json({ error: 'ALREADY_REVIEWED' });
  }

  const updated = await prisma.visit.update({
    where: { id: visit.id },
    data: { jointWorkApproved: parsed.data.approved },
  });
  res.json({ visit: updated });
});

/**
 * Pending joint-work claims for the signed-in manager — field reps tagged
 * them as the accompanying manager and the visit is awaiting their review.
 */
router.get('/joint-pending', async (req, res) => {
  if (req.auth!.role === Role.FIELD_PERSON) {
    return res.status(403).json({ error: 'MANAGER_ONLY' });
  }
  const visits = await prisma.visit.findMany({
    where: {
      user: { companyId: req.auth!.companyId },
      isJointWork: true,
      jointWithUserId: req.auth!.userId,
      jointWorkApproved: null,
    },
    orderBy: { checkInAt: 'desc' },
    include: {
      client: { select: { id: true, name: true, type: true } },
      user: { select: { id: true, name: true, employeeCode: true } },
    },
    take: 200,
  });
  res.json({ visits });
});

export default router;
