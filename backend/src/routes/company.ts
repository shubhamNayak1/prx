import { Router } from 'express';
import { z } from 'zod';
import { Role } from '@prisma/client';
import { prisma } from '../lib/prisma';
import { requireAuth, requireRole } from '../middleware/auth';

const router = Router();
router.use(requireAuth);

/** Any authed user sees basic company info; sensitive billing contacts are
 *  included only for admins. */
router.get('/', async (req, res) => {
  const company = await prisma.company.findUnique({
    where: { id: req.auth!.companyId },
    select: {
      id: true, name: true, gstin: true,
      billingEmail: req.auth!.role === Role.ADMIN,
      billingPhone: req.auth!.role === Role.ADMIN,
      createdAt: true,
    },
  });
  if (!company) return res.status(404).json({ error: 'not found' });
  res.json({ company });
});

const updateSchema = z.object({
  name: z.string().min(2).max(120).optional(),
  gstin: z.string().length(15).or(z.literal('')).optional(),
  billingEmail: z.string().email().or(z.literal('')).optional(),
  billingPhone: z.string().min(7).max(15).or(z.literal('')).optional(),
});

router.patch('/', requireRole(Role.ADMIN), async (req, res) => {
  const parsed = updateSchema.safeParse(req.body);
  if (!parsed.success) return res.status(400).json({ error: 'Invalid input', details: parsed.error.flatten() });

  const data = parsed.data;
  const normalized = {
    ...(data.name !== undefined && { name: data.name }),
    ...(data.gstin !== undefined && { gstin: data.gstin || null }),
    ...(data.billingEmail !== undefined && { billingEmail: data.billingEmail || null }),
    ...(data.billingPhone !== undefined && { billingPhone: data.billingPhone || null }),
  };

  const company = await prisma.company.update({
    where: { id: req.auth!.companyId },
    data: normalized,
    select: { id: true, name: true, gstin: true, billingEmail: true, billingPhone: true },
  });
  res.json({ company });
});

export default router;
