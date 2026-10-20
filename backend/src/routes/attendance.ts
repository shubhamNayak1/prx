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

/** Create a new punch-in session. Refuses if there's an open session for
 *  the user+date (prevents double-tap duplicates). */
router.post('/punch-in', async (req, res) => {
  const parsed = punchSchema.safeParse(req.body);
  if (!parsed.success) return res.status(400).json({ error: 'Invalid input' });
  const { date, at, lat, lng, photo } = parsed.data;
  const dateObj = new Date(date);

  const open = await prisma.attendance.findFirst({
    where: { userId: req.auth!.userId, date: dateObj, punchOutAt: null },
    orderBy: { punchInAt: 'desc' },
  });
  if (open) {
    return res.json({ attendance: open, alreadyPunchedIn: true });
  }

  const attendance = await prisma.attendance.create({
    data: {
      userId: req.auth!.userId,
      date: dateObj,
      punchInAt: new Date(at),
      punchInLat: lat,
      punchInLng: lng,
      punchInPhoto: photo,
    },
  });
  res.json({ attendance });
});

/** Close the latest open session for this user+date. */
router.post('/punch-out', async (req, res) => {
  const parsed = punchSchema.safeParse(req.body);
  if (!parsed.success) return res.status(400).json({ error: 'Invalid input' });
  const { date, at, lat, lng } = parsed.data;
  const dateObj = new Date(date);

  const open = await prisma.attendance.findFirst({
    where: { userId: req.auth!.userId, date: dateObj, punchOutAt: null },
    orderBy: { punchInAt: 'desc' },
  });
  if (!open) {
    return res.status(400).json({ error: 'NO_OPEN_SESSION' });
  }

  const attendance = await prisma.attendance.update({
    where: { id: open.id },
    data: {
      punchOutAt: new Date(at),
      punchOutLat: lat,
      punchOutLng: lng,
    },
  });
  res.json({ attendance });
});

/** All of today's sessions for the signed-in user, newest first. */
router.get('/today', async (req, res) => {
  const today = new Date();
  today.setHours(0, 0, 0, 0);

  const sessions = await prisma.attendance.findMany({
    where: { userId: req.auth!.userId, date: today },
    orderBy: { punchInAt: 'desc' },
  });
  res.json({ sessions });
});

/**
 * Manager / admin view — today's sessions grouped per user with cumulative
 * hours. Returns one entry per descendant user, including users who haven't
 * punched in yet.
 */
router.get('/team-today', async (req, res) => {
  const today = new Date();
  today.setHours(0, 0, 0, 0);
  const tomorrow = new Date(today);
  tomorrow.setDate(tomorrow.getDate() + 1);

  const all = await prisma.user.findMany({
    where: { companyId: req.auth!.companyId, isActive: true },
    select: { id: true, name: true, role: true, employeeCode: true, managerId: true },
  });
  const descendantIds = new Set<string>();
  const collect = (mgrId: string) => {
    for (const u of all) {
      if (u.managerId === mgrId) { descendantIds.add(u.id); collect(u.id); }
    }
  };
  collect(req.auth!.userId);

  const sessions = await prisma.attendance.findMany({
    where: { date: today, userId: { in: Array.from(descendantIds) } },
    orderBy: { punchInAt: 'asc' },
  });

  const byUser = new Map<string, typeof sessions>();
  for (const s of sessions) {
    const list = byUser.get(s.userId) ?? [];
    list.push(s);
    byUser.set(s.userId, list);
  }

  const records = Array.from(descendantIds).map((uid) => {
    const u = all.find((x) => x.id === uid)!;
    const sx = byUser.get(uid) ?? [];
    let totalMs = 0;
    for (const s of sx) {
      const end = s.punchOutAt ? new Date(s.punchOutAt).getTime() : Date.now();
      totalMs += Math.max(0, end - new Date(s.punchInAt).getTime());
    }
    return {
      user: { id: u.id, name: u.name, employeeCode: u.employeeCode },
      sessions: sx,
      totalMinutes: Math.round(totalMs / 60000),
      isCurrentlyIn: sx.some((s) => s.punchOutAt == null),
    };
  });

  res.json({ records });
});

export default router;
