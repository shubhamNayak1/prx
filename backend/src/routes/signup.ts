import { Router } from 'express';
import bcrypt from 'bcryptjs';
import { z } from 'zod';
import { OtpPurpose, Role } from '@prisma/client';
import { prisma } from '../lib/prisma';
import { signToken } from '../lib/jwt';
import { TRIAL_DAYS, addDays } from '../lib/billing';
import { issueOtp, verifyOtp, normalizePhone } from '../lib/otp';
import { clientIp, rateLimit } from '../middleware/rateLimit';

const router = Router();

/** Phone-keyed limiter — slows a single number being used to spam OTPs. */
const otpByPhone = rateLimit({
  windowMs: 60 * 60 * 1000,
  max: 5,
  keyBy: (req) => `otp:phone:${normalizePhone(String(req.body?.phone ?? ''))}`,
  message: 'Too many codes requested for this number. Try again in an hour.',
});

/** IP-keyed limiter — guards against a single host enumerating many numbers. */
const otpByIp = rateLimit({
  windowMs: 60 * 60 * 1000,
  max: 30,
  keyBy: (req) => `otp:ip:${clientIp(req)}`,
  message: 'Too many requests from this network. Please try again later.',
});

const signupByIp = rateLimit({
  windowMs: 60 * 60 * 1000,
  max: 10,
  keyBy: (req) => `signup:ip:${clientIp(req)}`,
  message: 'Too many signups from this network. Please try again later.',
});

/** ───────────── 1. Request OTP ───────────── */

const requestOtpSchema = z.object({
  phone: z.string().min(7).max(15),
});

router.post('/request-otp', otpByIp, otpByPhone, async (req, res) => {
  const parsed = requestOtpSchema.safeParse(req.body);
  if (!parsed.success) return res.status(400).json({ error: 'Invalid input' });

  const result = await issueOtp(parsed.data.phone, OtpPurpose.SIGNUP);
  res.status(201).json({
    challengeId: result.id,
    expiresAt: result.expiresAt,
    channel: result.channel, // "whatsapp" | "sms" | "dev"
    // In production (channel != "dev") the code is never returned.
    devCode: result.devCode,
  });
});

/** ───────────── 2. Verify OTP + create tenant ───────────── */

const signupSchema = z.object({
  companyName: z.string().min(2).max(120),
  gstin: z.string().length(15).optional(),
  adminName: z.string().min(1),
  adminEmail: z.string().email(),
  adminPhone: z.string().min(7).max(15),
  adminPassword: z.string().min(6),
  otp: z.string().regex(/^\d{6}$/, 'OTP must be 6 digits'),
});

router.post('/', signupByIp, async (req, res) => {
  const parsed = signupSchema.safeParse(req.body);
  if (!parsed.success) {
    return res.status(400).json({ error: 'Invalid input', details: parsed.error.flatten() });
  }
  const { companyName, gstin, adminName, adminEmail, adminPhone, adminPassword, otp } = parsed.data;

  // 1. Phone proof
  const check = await verifyOtp(adminPhone, OtpPurpose.SIGNUP, otp);
  if (!check.ok) {
    return res.status(400).json({ error: 'OTP_INVALID', reason: check.reason });
  }

  // 2. Email uniqueness
  const existing = await prisma.user.findUnique({ where: { email: adminEmail } });
  if (existing) return res.status(409).json({ error: 'Email already in use' });

  // 3. Provision tenant
  const now = new Date();
  const trialEndsAt = addDays(now, TRIAL_DAYS);

  const company = await prisma.company.create({
    data: {
      name: companyName,
      gstin,
      billingEmail: adminEmail,
      billingPhone: adminPhone,
      subscription: {
        create: {
          status: 'TRIAL',
          trialEndsAt,
          currentPeriodStart: now,
          currentPeriodEnd: trialEndsAt,
        },
      },
      users: {
        create: {
          name: adminName,
          email: adminEmail,
          phone: adminPhone,
          passwordHash: bcrypt.hashSync(adminPassword, 10),
          role: Role.ADMIN,
        },
      },
    },
    include: {
      users: { select: { id: true, email: true, name: true, role: true } },
      subscription: { select: { id: true, status: true, trialEndsAt: true } },
    },
  });

  const admin = company.users[0];
  const token = signToken({ userId: admin.id, companyId: company.id, role: admin.role });

  res.status(201).json({
    token,
    company: { id: company.id, name: company.name },
    user: { ...admin, companyId: company.id },
    subscription: company.subscription,
  });
});

export default router;
