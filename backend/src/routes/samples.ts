import { Router } from 'express';
import { z } from 'zod';
import { Role } from '@prisma/client';
import { prisma } from '../lib/prisma';
import { requireAuth, requireRole } from '../middleware/auth';

const router = Router();
router.use(requireAuth);

// ───────── Products ─────────

router.get('/products', async (req, res) => {
  const products = await prisma.sampleProduct.findMany({
    where: { companyId: req.auth!.companyId },
    orderBy: { name: 'asc' },
  });
  res.json({ products });
});

const productSchema = z.object({
  name: z.string().min(1),
  unitType: z.string().default('strip'),
  isGift: z.boolean().default(false),
});

router.post('/products', requireRole(Role.ADMIN), async (req, res) => {
  const parsed = productSchema.safeParse(req.body);
  if (!parsed.success) return res.status(400).json({ error: 'Invalid input' });
  const product = await prisma.sampleProduct.create({
    data: { ...parsed.data, companyId: req.auth!.companyId },
  });
  res.status(201).json({ product });
});

// ───────── Issuance (admin issues stock to MR) ─────────

const issueSchema = z.object({
  productId: z.string(),
  userId: z.string(),
  quantity: z.number().int().positive(),
});

router.post('/issues', requireRole(Role.ADMIN, Role.MANAGER), async (req, res) => {
  const parsed = issueSchema.safeParse(req.body);
  if (!parsed.success) return res.status(400).json({ error: 'Invalid input' });

  const product = await prisma.sampleProduct.findFirst({
    where: { id: parsed.data.productId, companyId: req.auth!.companyId },
  });
  const user = await prisma.user.findFirst({
    where: { id: parsed.data.userId, companyId: req.auth!.companyId },
  });
  if (!product || !user) return res.status(404).json({ error: 'Product or user not found' });

  const issue = await prisma.sampleIssue.create({
    data: parsed.data,
  });
  res.status(201).json({ issue });
});

router.get('/issues', async (req, res) => {
  const where: any = {};
  if (req.auth!.role === Role.MR) where.userId = req.auth!.userId;
  else if (req.query.userId) where.userId = req.query.userId;

  const issues = await prisma.sampleIssue.findMany({
    where,
    orderBy: { issuedAt: 'desc' },
    include: {
      product: true,
      user: { select: { id: true, name: true } },
    },
    take: 200,
  });
  res.json({ issues });
});

// ───────── Distribution (MR records giving to doctor) ─────────

const distSchema = z.object({
  sampleIssueId: z.string(),
  visitId: z.string().optional(),
  quantity: z.number().int().positive(),
  actionLat: z.number().optional(),
  actionLng: z.number().optional(),
});

router.post('/distributions', async (req, res) => {
  const parsed = distSchema.safeParse(req.body);
  if (!parsed.success) return res.status(400).json({ error: 'Invalid input' });

  const issue = await prisma.sampleIssue.findFirst({
    where: { id: parsed.data.sampleIssueId, userId: req.auth!.userId },
    include: { distributions: true },
  });
  if (!issue) return res.status(404).json({ error: 'Sample issue not found or not yours' });

  const distributed = issue.distributions.reduce((s, d) => s + d.quantity, 0);
  const remaining = issue.quantity - distributed;
  if (parsed.data.quantity > remaining) {
    return res.status(400).json({ error: `Only ${remaining} units remaining` });
  }

  const dist = await prisma.sampleDistribution.create({
    data: {
      sampleIssueId: parsed.data.sampleIssueId,
      visitId: parsed.data.visitId,
      quantity: parsed.data.quantity,
      userId: req.auth!.userId,
      actionLat: parsed.data.actionLat,
      actionLng: parsed.data.actionLng,
    },
  });
  res.status(201).json({ distribution: dist });
});

// ───────── Balance (MR's current stock) ─────────

router.get('/balance', async (req, res) => {
  const issues = await prisma.sampleIssue.findMany({
    where: { userId: req.auth!.userId },
    include: {
      product: true,
      distributions: { select: { quantity: true } },
    },
    orderBy: { issuedAt: 'desc' },
  });

  const balance = issues.map((i) => {
    const distributed = i.distributions.reduce((s, d) => s + d.quantity, 0);
    return {
      issueId: i.id,
      product: i.product,
      issued: i.quantity,
      distributed,
      remaining: i.quantity - distributed,
      issuedAt: i.issuedAt,
    };
  });

  res.json({ balance });
});

export default router;
