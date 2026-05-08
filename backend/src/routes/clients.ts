import { Router } from 'express';
import { z } from 'zod';
import { ClientType } from '@prisma/client';
import { prisma } from '../lib/prisma';
import { requireAuth } from '../middleware/auth';

const router = Router();
router.use(requireAuth);

router.get('/', async (req, res) => {
  const search = (req.query.q as string | undefined)?.trim();
  const type = req.query.type as ClientType | undefined;
  const clients = await prisma.client.findMany({
    where: {
      companyId: req.auth!.companyId,
      ...(type ? { type } : {}),
      ...(search ? { name: { contains: search, mode: 'insensitive' } } : {}),
    },
    orderBy: { name: 'asc' },
    take: 500,
  });
  res.json({ clients });
});

router.get('/:id', async (req, res) => {
  const client = await prisma.client.findFirst({
    where: { id: req.params.id, companyId: req.auth!.companyId },
  });
  if (!client) return res.status(404).json({ error: 'Not found' });
  res.json({ client });
});

const clientSchema = z.object({
  name: z.string().min(1),
  type: z.nativeEnum(ClientType),
  speciality: z.string().optional(),
  address: z.string().optional(),
  city: z.string().optional(),
  pincode: z.string().optional(),
  phone: z.string().optional(),
  email: z.string().email().optional().or(z.literal('').transform(() => undefined)),
  latitude: z.number().optional(),
  longitude: z.number().optional(),
});

router.post('/', async (req, res) => {
  const parsed = clientSchema.safeParse(req.body);
  if (!parsed.success) {
    return res.status(400).json({ error: 'Invalid input', details: parsed.error.flatten() });
  }
  const client = await prisma.client.create({
    data: {
      ...parsed.data,
      companyId: req.auth!.companyId,
      createdById: req.auth!.userId,
    },
  });
  res.status(201).json({ client });
});

router.patch('/:id', async (req, res) => {
  const parsed = clientSchema.partial().safeParse(req.body);
  if (!parsed.success) return res.status(400).json({ error: 'Invalid input' });
  const target = await prisma.client.findFirst({
    where: { id: req.params.id, companyId: req.auth!.companyId },
  });
  if (!target) return res.status(404).json({ error: 'Not found' });
  const client = await prisma.client.update({ where: { id: target.id }, data: parsed.data });
  res.json({ client });
});

export default router;
