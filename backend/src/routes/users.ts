import { Router } from 'express';
import bcrypt from 'bcryptjs';
import { z } from 'zod';
import { Role } from '@prisma/client';
import { prisma } from '../lib/prisma';
import { requireAuth, requireRole } from '../middleware/auth';

const router = Router();

router.use(requireAuth);

// List users in current company
router.get('/', async (req, res) => {
  const users = await prisma.user.findMany({
    where: { companyId: req.auth!.companyId },
    orderBy: { createdAt: 'desc' },
    select: {
      id: true,
      name: true,
      email: true,
      phone: true,
      role: true,
      grade: true,
      employeeCode: true,
      isActive: true,
      managerId: true,
      manager: { select: { id: true, name: true } },
      createdAt: true,
    },
  });
  res.json({ users });
});

// Hierarchy view — tree of managers and their reports
router.get('/hierarchy', async (req, res) => {
  const users = await prisma.user.findMany({
    where: { companyId: req.auth!.companyId, isActive: true },
    select: {
      id: true,
      name: true,
      role: true,
      grade: true,
      managerId: true,
    },
  });
  res.json({ users });
});

const createUserSchema = z.object({
  name: z.string().min(1),
  email: z.string().email(),
  password: z.string().min(6),
  phone: z.string().optional(),
  role: z.nativeEnum(Role),
  grade: z.string().optional(),
  employeeCode: z.string().optional(),
  managerId: z.string().optional().nullable(),
});

router.post('/', requireRole(Role.ADMIN), async (req, res) => {
  const parsed = createUserSchema.safeParse(req.body);
  if (!parsed.success) {
    return res.status(400).json({ error: 'Invalid input', details: parsed.error.flatten() });
  }
  const data = parsed.data;

  if (data.managerId) {
    const mgr = await prisma.user.findFirst({
      where: { id: data.managerId, companyId: req.auth!.companyId },
    });
    if (!mgr) return res.status(400).json({ error: 'Manager not found in your company' });
    if (mgr.role === Role.MR) return res.status(400).json({ error: 'Cannot report to an MR' });
  }

  const existing = await prisma.user.findUnique({ where: { email: data.email } });
  if (existing) return res.status(409).json({ error: 'Email already in use' });

  const user = await prisma.user.create({
    data: {
      companyId: req.auth!.companyId,
      name: data.name,
      email: data.email,
      phone: data.phone,
      passwordHash: bcrypt.hashSync(data.password, 10),
      role: data.role,
      grade: data.grade,
      employeeCode: data.employeeCode,
      managerId: data.managerId || null,
    },
    select: { id: true, name: true, email: true, role: true },
  });
  res.status(201).json({ user });
});

const updateUserSchema = createUserSchema.partial().omit({ password: true }).extend({
  isActive: z.boolean().optional(),
  password: z.string().min(6).optional(),
});

router.patch('/:id', requireRole(Role.ADMIN), async (req, res) => {
  const parsed = updateUserSchema.safeParse(req.body);
  if (!parsed.success) {
    return res.status(400).json({ error: 'Invalid input', details: parsed.error.flatten() });
  }
  const target = await prisma.user.findFirst({
    where: { id: req.params.id, companyId: req.auth!.companyId },
  });
  if (!target) return res.status(404).json({ error: 'User not found' });

  const { password, ...rest } = parsed.data;
  const user = await prisma.user.update({
    where: { id: target.id },
    data: {
      ...rest,
      passwordHash: password ? bcrypt.hashSync(password, 10) : undefined,
    },
    select: { id: true, name: true, email: true, role: true, isActive: true, managerId: true },
  });
  res.json({ user });
});

router.delete('/:id', requireRole(Role.ADMIN), async (req, res) => {
  const target = await prisma.user.findFirst({
    where: { id: req.params.id, companyId: req.auth!.companyId },
  });
  if (!target) return res.status(404).json({ error: 'User not found' });
  await prisma.user.update({ where: { id: target.id }, data: { isActive: false } });
  res.status(204).end();
});

export default router;
