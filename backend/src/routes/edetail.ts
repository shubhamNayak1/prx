import { Router } from 'express';
import { z } from 'zod';
import { Role } from '@prisma/client';
import { prisma } from '../lib/prisma';
import { requireAuth, requireRole } from '../middleware/auth';

const router = Router();
router.use(requireAuth);

// ───────── Decks ─────────

router.get('/decks', async (req, res) => {
  const decks = await prisma.edetailDeck.findMany({
    where: { companyId: req.auth!.companyId },
    orderBy: { createdAt: 'desc' },
    include: { slides: { orderBy: { order: 'asc' } } },
  });
  res.json({ decks });
});

router.get('/decks/:id', async (req, res) => {
  const deck = await prisma.edetailDeck.findFirst({
    where: { id: req.params.id, companyId: req.auth!.companyId },
    include: { slides: { orderBy: { order: 'asc' } } },
  });
  if (!deck) return res.status(404).json({ error: 'Not found' });
  res.json({ deck });
});

const deckSchema = z.object({
  name: z.string().min(1),
  product: z.string().optional(),
});

router.post('/decks', requireRole(Role.ADMIN, Role.MANAGER), async (req, res) => {
  const parsed = deckSchema.safeParse(req.body);
  if (!parsed.success) return res.status(400).json({ error: 'Invalid input' });
  const deck = await prisma.edetailDeck.create({
    data: { ...parsed.data, companyId: req.auth!.companyId },
  });
  res.status(201).json({ deck });
});

// ───────── Slides ─────────

const slideSchema = z.object({
  order: z.number().int(),
  title: z.string().optional(),
  imageUrl: z.string().min(1),
});

router.post('/decks/:deckId/slides', requireRole(Role.ADMIN, Role.MANAGER), async (req, res) => {
  const deck = await prisma.edetailDeck.findFirst({
    where: { id: req.params.deckId, companyId: req.auth!.companyId },
  });
  if (!deck) return res.status(404).json({ error: 'Deck not found' });

  const parsed = slideSchema.safeParse(req.body);
  if (!parsed.success) return res.status(400).json({ error: 'Invalid input' });

  const slide = await prisma.edetailSlide.create({
    data: { ...parsed.data, deckId: deck.id },
  });
  res.status(201).json({ slide });
});

router.delete('/slides/:id', requireRole(Role.ADMIN, Role.MANAGER), async (req, res) => {
  const slide = await prisma.edetailSlide.findFirst({
    where: { id: req.params.id },
    include: { deck: true },
  });
  if (!slide || slide.deck.companyId !== req.auth!.companyId) {
    return res.status(404).json({ error: 'Not found' });
  }
  await prisma.edetailSlide.delete({ where: { id: slide.id } });
  res.status(204).end();
});

// ───────── View tracking ─────────
// MR sends {visitId?, slides: {slideId: durationSeconds}} after viewing a deck.

const viewSchema = z.object({
  visitId: z.string().optional(),
  slides: z.record(z.string(), z.number().nonnegative()),
});

router.post('/views', async (req, res) => {
  const parsed = viewSchema.safeParse(req.body);
  if (!parsed.success) return res.status(400).json({ error: 'Invalid input' });

  if (parsed.data.visitId) {
    // Patch the visit's edetailViewedSlides JSON. Merge with existing if any.
    const visit = await prisma.visit.findFirst({
      where: { id: parsed.data.visitId, userId: req.auth!.userId },
    });
    if (visit) {
      const existing = (visit.edetailViewedSlides as Record<string, number> | null) ?? {};
      for (const [slideId, dur] of Object.entries(parsed.data.slides)) {
        existing[slideId] = (existing[slideId] ?? 0) + dur;
      }
      await prisma.visit.update({
        where: { id: visit.id },
        data: { edetailViewedSlides: existing },
      });
    }
  }
  // Always 200 — the slides record itself is durable on the visit row;
  // we don't currently store a separate "view session" row.
  res.json({ ok: true });
});

export default router;
