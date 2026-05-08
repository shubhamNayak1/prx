import { Router } from 'express';
import { z } from 'zod';
import { Role } from '@prisma/client';
import { prisma } from '../lib/prisma';
import { requireAuth, requireRole } from '../middleware/auth';

const router = Router();
router.use(requireAuth);

router.get('/', async (req, res) => {
  const policies = await prisma.expensePolicy.findMany({
    where: { companyId: req.auth!.companyId },
    orderBy: { grade: 'asc' },
  });
  res.json({ policies });
});

// Current user's policy (MR uses this to pre-fill TA/DA rates)
router.get('/me', async (req, res) => {
  const me = await prisma.user.findUnique({ where: { id: req.auth!.userId } });
  if (!me?.grade) return res.json({ policy: null });
  const policy = await prisma.expensePolicy.findUnique({
    where: { companyId_grade: { companyId: me.companyId, grade: me.grade } },
  });
  res.json({ policy });
});

const policySchema = z.object({
  grade: z.string().min(1),
  taRatePerKm: z.number().nonnegative(),
  daFlatRate: z.number().nonnegative(),
});

router.put('/', requireRole(Role.ADMIN), async (req, res) => {
  const parsed = policySchema.safeParse(req.body);
  if (!parsed.success) return res.status(400).json({ error: 'Invalid input' });

  const policy = await prisma.expensePolicy.upsert({
    where: { companyId_grade: { companyId: req.auth!.companyId, grade: parsed.data.grade } },
    update: { taRatePerKm: parsed.data.taRatePerKm, daFlatRate: parsed.data.daFlatRate },
    create: { ...parsed.data, companyId: req.auth!.companyId },
  });
  res.json({ policy });
});

export default router;
