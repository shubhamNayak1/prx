import { Router } from 'express';
import { z } from 'zod';
import { prisma } from '../lib/prisma';
import { requireAuth } from '../middleware/auth';

const router = Router();
router.use(requireAuth);

const punchSchema = z.object({
  date: z.string().regex(/^\d{4}-\d{2}-\d{2}$/), // YYYY-MM-DD
  at: z.string().datetime(),
  lat: z.number().optional(),
  lng: z.number().optional(),
  photo: z.string().url().optional(),
});

// Punch in
router.post('/punch-in', async (req, res) => {
  const parsed = punchSchema.safeParse(req.body);
  if (!parsed.success) return res.status(400).json({ error: 'Invalid input' });

  const { date, at, lat, lng, photo } = parsed.data;
  const dateObj = new Date(date);

  const existing = await prisma.attendance.findUnique({
    where: { userId_date: { userId: req.auth!.userId, date: dateObj } },
  });

  if (existing?.punchInAt) {
    return res.json({ attendance: existing, alreadyPunchedIn: true });
  }

  const attendance = await prisma.attendance.upsert({
    where: { userId_date: { userId: req.auth!.userId, date: dateObj } },
    create: {
      userId: req.auth!.userId,
      date: dateObj,
      punchInAt: new Date(at),
      punchInLat: lat,
      punchInLng: lng,
      punchInPhoto: photo,
    },
    update: {
      punchInAt: new Date(at),
      punchInLat: lat,
      punchInLng: lng,
      punchInPhoto: photo,
    },
  });
  res.json({ attendance });
});

// Punch out
router.post('/punch-out', async (req, res) => {
  const parsed = punchSchema.safeParse(req.body);
  if (!parsed.success) return res.status(400).json({ error: 'Invalid input' });

  const { date, at, lat, lng } = parsed.data;
  const dateObj = new Date(date);

  const attendance = await prisma.attendance.update({
    where: { userId_date: { userId: req.auth!.userId, date: dateObj } },
    data: {
      punchOutAt: new Date(at),
      punchOutLat: lat,
      punchOutLng: lng,
    },
  });
  res.json({ attendance });
});

// Today's status
router.get('/today', async (req, res) => {
  const today = new Date();
  today.setHours(0, 0, 0, 0);

  const attendance = await prisma.attendance.findUnique({
    where: { userId_date: { userId: req.auth!.userId, date: today } },
  });
  res.json({ attendance });
});

// Manager view — today's attendance for direct + indirect reports
router.get('/team-today', async (req, res) => {
  const today = new Date();
  today.setHours(0, 0, 0, 0);

  // Recursive descendants of current manager
  const all = await prisma.user.findMany({
    where: { companyId: req.auth!.companyId, isActive: true },
    select: { id: true, name: true, role: true, managerId: true },
  });
  const descendantIds = new Set<string>();
  const collect = (mgrId: string) => {
    for (const u of all) {
      if (u.managerId === mgrId) {
        descendantIds.add(u.id);
        collect(u.id);
      }
    }
  };
  collect(req.auth!.userId);

  const records = await prisma.attendance.findMany({
    where: { date: today, userId: { in: Array.from(descendantIds) } },
    include: { user: { select: { id: true, name: true, employeeCode: true } } },
  });
  res.json({ records });
});

export default router;
