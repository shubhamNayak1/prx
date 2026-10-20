import { Router } from 'express';
import crypto from 'crypto';
import { z } from 'zod';
import { BillingCycle, Role } from '@prisma/client';
import { prisma } from '../lib/prisma';
import { requireAuth, requireRole } from '../middleware/auth';
import {
  CYCLE_MONTHS,
  TRIAL_REP_CAP,
  TRIAL_DAYS,
  DEFAULT_PRICE_PER_REP_PAISE,
  addDays,
  addMonths,
  countActiveReps,
  priceFor,
} from '../lib/billing';
import { nextInvoiceNumber } from '../lib/invoiceNumber';

const router = Router();

/**
 * GET /api/billing/status
 * Any authed user can read. Android reads this at login to decide feature access.
 */
router.get('/status', requireAuth, async (req, res) => {
  const sub = await prisma.subscription.findUnique({
    where: { companyId: req.auth!.companyId },
  });
  if (!sub) return res.status(404).json({ error: 'NO_SUBSCRIPTION' });
  res.json({
    subscription: {
      status: sub.status,
      cycle: sub.cycle,
      trialEndsAt: sub.trialEndsAt,
      currentPeriodStart: sub.currentPeriodStart,
      currentPeriodEnd: sub.currentPeriodEnd,
      pricePerRepPaise: sub.pricePerRepPaise,
      trialRepCap: sub.status === 'TRIAL' ? TRIAL_REP_CAP : null,
    },
  });
});

/**
 * GET /api/billing/invoices
 * Admin-only. Lists this company's invoices, newest first.
 */
router.get('/invoices', requireAuth, requireRole(Role.ADMIN), async (req, res) => {
  const sub = await prisma.subscription.findUnique({
    where: { companyId: req.auth!.companyId },
    select: { id: true },
  });
  if (!sub) return res.json({ invoices: [] });

  const invoices = await prisma.invoice.findMany({
    where: { subscriptionId: sub.id },
    orderBy: { createdAt: 'desc' },
    select: {
      id: true,
      number: true,
      cycle: true,
      periodStart: true,
      periodEnd: true,
      activeRepCount: true,
      pricePerRepPaise: true,
      discountPct: true,
      subtotalPaise: true,
      gstPaise: true,
      totalPaise: true,
      status: true,
      dueAt: true,
      paidAt: true,
      pdfUrl: true,
    },
  });
  res.json({ invoices });
});

const upgradeSchema = z.object({ cycle: z.nativeEnum(BillingCycle) });

/**
 * POST /api/billing/upgrade
 * Admin picks a billing cycle. Generates the first paid invoice using the
 * current active-rep count. Returns a Razorpay payment-link URL (stubbed if
 * credentials are not configured).
 */
router.post('/upgrade', requireAuth, requireRole(Role.ADMIN), async (req, res) => {
  const parsed = upgradeSchema.safeParse(req.body);
  if (!parsed.success) return res.status(400).json({ error: 'Invalid input' });
  const { cycle } = parsed.data;

  const sub = await prisma.subscription.findUnique({
    where: { companyId: req.auth!.companyId },
  });
  if (!sub) return res.status(404).json({ error: 'NO_SUBSCRIPTION' });

  const activeReps = await countActiveReps(req.auth!.companyId, TRIAL_DAYS);
  const breakdown = priceFor(cycle, activeReps, sub.pricePerRepPaise || DEFAULT_PRICE_PER_REP_PAISE);

  const now = new Date();
  const periodEnd = addMonths(now, CYCLE_MONTHS[cycle]);

  const invoice = await prisma.invoice.create({
    data: {
      subscriptionId: sub.id,
      number: await nextInvoiceNumber(now),
      cycle,
      periodStart: now,
      periodEnd,
      activeRepCount: activeReps,
      pricePerRepPaise: breakdown.pricePerRepPaise,
      discountPct: breakdown.discountPct,
      subtotalPaise: breakdown.subtotalPaise,
      gstPaise: breakdown.gstPaise,
      totalPaise: breakdown.totalPaise,
      status: 'SENT',
      dueAt: addDays(now, 7),
    },
  });

  const paymentLink = await createRazorpayPaymentLink({
    invoiceId: invoice.id,
    amountPaise: breakdown.totalPaise,
    companyId: req.auth!.companyId,
  });

  res.status(201).json({
    invoice: {
      id: invoice.id,
      number: invoice.number,
      totalPaise: invoice.totalPaise,
      activeRepCount: invoice.activeRepCount,
      discountPct: invoice.discountPct,
      cycle: invoice.cycle,
    },
    paymentLink,
  });
});

/**
 * POST /api/billing/webhook
 * Razorpay payment-captured webhook. On success, marks the invoice PAID and
 * activates/extends the subscription period.
 *
 * Verify the X-Razorpay-Signature header against RAZORPAY_WEBHOOK_SECRET.
 */
router.post('/webhook', async (req, res) => {
  const secret = process.env.RAZORPAY_WEBHOOK_SECRET;
  const sig = req.header('x-razorpay-signature');
  if (secret) {
    const expected = crypto
      .createHmac('sha256', secret)
      .update(JSON.stringify(req.body))
      .digest('hex');
    if (sig !== expected) return res.status(401).json({ error: 'bad signature' });
  }

  const event = req.body?.event as string | undefined;
  if (event !== 'payment_link.paid' && event !== 'payment.captured') {
    return res.json({ ignored: true });
  }

  const invoiceId: string | undefined =
    req.body?.payload?.payment_link?.entity?.reference_id ||
    req.body?.payload?.payment?.entity?.notes?.invoiceId;
  const gatewayRef: string | undefined =
    req.body?.payload?.payment?.entity?.id ||
    req.body?.payload?.payment_link?.entity?.id;

  if (!invoiceId) return res.status(400).json({ error: 'missing reference_id' });

  const invoice = await prisma.invoice.findUnique({
    where: { id: invoiceId },
    include: { subscription: true },
  });
  if (!invoice) return res.status(404).json({ error: 'invoice not found' });
  if (invoice.status === 'PAID') return res.json({ alreadyPaid: true });

  await prisma.$transaction([
    prisma.invoice.update({
      where: { id: invoice.id },
      data: { status: 'PAID', paidAt: new Date(), gatewayRef },
    }),
    prisma.subscription.update({
      where: { id: invoice.subscriptionId },
      data: {
        status: 'ACTIVE',
        cycle: invoice.cycle,
        currentPeriodStart: invoice.periodStart,
        currentPeriodEnd: invoice.periodEnd,
        lockedAt: null,
      },
    }),
  ]);

  res.json({ ok: true });
});

/**
 * POST /api/billing/cron/trial-lock
 * Hit this from an external scheduler (Render cron, GitHub Action, cron-job.org)
 * once a day. Flips expired TRIAL subs to LOCKED.
 *
 * Protected by header "x-cron-key: $CRON_KEY" so public internet can't fire it.
 */
router.post('/cron/trial-lock', async (req, res) => {
  if (req.header('x-cron-key') !== process.env.CRON_KEY) {
    return res.status(401).json({ error: 'unauthorized' });
  }
  const now = new Date();
  const expired = await prisma.subscription.findMany({
    where: { status: 'TRIAL', trialEndsAt: { lt: now } },
    select: { id: true },
  });
  if (!expired.length) return res.json({ locked: 0 });

  await prisma.subscription.updateMany({
    where: { id: { in: expired.map((e) => e.id) } },
    data: { status: 'LOCKED', lockedAt: now },
  });
  // TODO: enqueue "trial ended" email + WhatsApp to billing contact.
  res.json({ locked: expired.length });
});

/**
 * POST /api/billing/cron/rebill
 * Daily. For every ACTIVE subscription whose currentPeriodEnd is today or past,
 * compute the next invoice for its cycle and send a payment link. Does NOT
 * extend the period — the period extends only when the invoice is paid.
 */
router.post('/cron/rebill', async (req, res) => {
  if (req.header('x-cron-key') !== process.env.CRON_KEY) {
    return res.status(401).json({ error: 'unauthorized' });
  }
  const now = new Date();
  const dueSubs = await prisma.subscription.findMany({
    where: {
      status: 'ACTIVE',
      cycle: { not: null },
      currentPeriodEnd: { lte: now },
      cancelAtPeriodEnd: false,
    },
  });

  const results: Array<{ companyId: string; invoiceId: string; totalPaise: number }> = [];
  for (const sub of dueSubs) {
    const cycle = sub.cycle!;
    const activeReps = await countActiveReps(sub.companyId, CYCLE_MONTHS[cycle] * 30);
    const breakdown = priceFor(cycle, activeReps, sub.pricePerRepPaise);
    const periodEnd = addMonths(now, CYCLE_MONTHS[cycle]);

    const invoice = await prisma.invoice.create({
      data: {
        subscriptionId: sub.id,
        number: await nextInvoiceNumber(now),
        cycle,
        periodStart: now,
        periodEnd,
        activeRepCount: activeReps,
        pricePerRepPaise: breakdown.pricePerRepPaise,
        discountPct: breakdown.discountPct,
        subtotalPaise: breakdown.subtotalPaise,
        gstPaise: breakdown.gstPaise,
        totalPaise: breakdown.totalPaise,
        status: 'SENT',
        dueAt: addDays(now, 7),
      },
    });
    await createRazorpayPaymentLink({
      invoiceId: invoice.id,
      amountPaise: breakdown.totalPaise,
      companyId: sub.companyId,
    });
    results.push({ companyId: sub.companyId, invoiceId: invoice.id, totalPaise: breakdown.totalPaise });
  }
  res.json({ generated: results.length, results });
});

// ───────────────── Razorpay payment-link helper ─────────────────

async function createRazorpayPaymentLink(args: {
  invoiceId: string;
  amountPaise: number;
  companyId: string;
}): Promise<string> {
  const keyId = process.env.RAZORPAY_KEY_ID;
  const keySecret = process.env.RAZORPAY_KEY_SECRET;
  const appUrl = process.env.APP_URL || 'http://localhost:3000';

  // Dev stub — avoids forcing a Razorpay account during local work.
  if (!keyId || !keySecret) {
    return `${appUrl}/dashboard/billing?inv=${args.invoiceId}&stub=1`;
  }

  const company = await prisma.company.findUnique({
    where: { id: args.companyId },
    select: { name: true, billingEmail: true, billingPhone: true },
  });

  const body = {
    amount: args.amountPaise,
    currency: 'INR',
    accept_partial: false,
    reference_id: args.invoiceId,
    description: `FieldPharma invoice ${args.invoiceId}`,
    customer: {
      name: company?.name,
      email: company?.billingEmail || undefined,
      contact: company?.billingPhone || undefined,
    },
    notify: { sms: true, email: true },
    reminder_enable: true,
    callback_url: `${appUrl}/dashboard/billing?inv=${args.invoiceId}`,
    callback_method: 'get',
  };

  const auth = Buffer.from(`${keyId}:${keySecret}`).toString('base64');
  const resp = await fetch('https://api.razorpay.com/v1/payment_links', {
    method: 'POST',
    headers: {
      'content-type': 'application/json',
      authorization: `Basic ${auth}`,
    },
    body: JSON.stringify(body),
  });
  if (!resp.ok) {
    const text = await resp.text();
    throw new Error(`Razorpay link failed: ${resp.status} ${text}`);
  }
  const data = (await resp.json()) as { short_url: string };
  return data.short_url;
}

export default router;
