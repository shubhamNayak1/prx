import { Router } from 'express';
import bcrypt from 'bcryptjs';
import jwt from 'jsonwebtoken';
import { z } from 'zod';
import { OtpPurpose } from '@prisma/client';
import { prisma } from '../lib/prisma';
import { signToken, verifyToken } from '../lib/jwt';
import { requireAuth } from '../middleware/auth';
import { clientIp, rateLimit } from '../middleware/rateLimit';
import { issueOtp, verifyOtp, normalizePhone } from '../lib/otp';

const router = Router();

/* ─────────────── Rate limits ─────────────── */

const loginLimiter = rateLimit({
  windowMs: 15 * 60 * 1000, max: 20,
  keyBy: (req) => `login:ip:${clientIp(req)}`,
  message: 'Too many login attempts from this network. Please try again in 15 minutes.',
});

const otpByPhone = rateLimit({
  windowMs: 60 * 60 * 1000, max: 5,
  keyBy: (req) => `auth-otp:phone:${normalizePhone(String(req.body?.phone ?? ''))}`,
  message: 'Too many codes requested for this number. Try again in an hour.',
});

const otpByIp = rateLimit({
  windowMs: 60 * 60 * 1000, max: 30,
  keyBy: (req) => `auth-otp:ip:${clientIp(req)}`,
  message: 'Too many OTP requests from this network. Please try again later.',
});

/* ─────────────── Email + password login (legacy, still supported) ─────────────── */

const loginSchema = z.object({
  email: z.string().email(),
  password: z.string().min(1),
});

router.post('/login', loginLimiter, async (req, res) => {
  const parsed = loginSchema.safeParse(req.body);
  if (!parsed.success) return res.status(400).json({ error: 'Invalid input' });

  const { email, password } = parsed.data;
  const user = await prisma.user.findUnique({ where: { email } });
  if (!user || !user.isActive) return res.status(401).json({ error: 'Invalid credentials' });

  const ok = bcrypt.compareSync(password, user.passwordHash);
  if (!ok) return res.status(401).json({ error: 'Invalid credentials' });

  const token = signToken({ userId: user.id, companyId: user.companyId, role: user.role });
  res.json({
    token,
    user: { id: user.id, email: user.email, name: user.name, role: user.role, companyId: user.companyId },
  });
});

/* ─────────────── Phone + PIN login (primary) ─────────────── */

const pinLoginSchema = z.object({
  phone: z.string().min(7).max(15),
  pin: z.string().regex(/^\d{4}$/, 'PIN must be 4 digits'),
});

router.post('/pin-login', loginLimiter, async (req, res) => {
  const parsed = pinLoginSchema.safeParse(req.body);
  if (!parsed.success) return res.status(400).json({ error: 'Invalid input' });
  const { phone, pin } = parsed.data;

  const user = await prisma.user.findUnique({ where: { phone: normalizePhone(phone) } });
  if (!user || !user.isActive) return res.status(401).json({ error: 'Invalid credentials' });
  if (!user.pinHash) {
    return res.status(400).json({ error: 'PIN_NOT_SET', message: 'Use WhatsApp OTP to set your PIN.' });
  }
  if (!bcrypt.compareSync(pin, user.pinHash)) {
    return res.status(401).json({ error: 'Invalid credentials' });
  }

  const token = signToken({ userId: user.id, companyId: user.companyId, role: user.role });
  res.json({
    token,
    user: { id: user.id, email: user.email, name: user.name, role: user.role, companyId: user.companyId },
  });
});

/* ─────────────── Phone-OTP first-time login / PIN reset ─────────────── */

const requestOtpSchema = z.object({
  phone: z.string().min(7).max(15),
  purpose: z.enum(['LOGIN', 'RESET_PIN']).default('LOGIN'),
});

/**
 * Sends a WhatsApp OTP to the given phone. Works for any phone — the
 * response does NOT reveal whether the user exists, so an attacker can't
 * enumerate numbers. The caller finds out at verify-otp time.
 */
router.post('/request-otp', otpByIp, otpByPhone, async (req, res) => {
  const parsed = requestOtpSchema.safeParse(req.body);
  if (!parsed.success) return res.status(400).json({ error: 'Invalid input' });
  const { phone, purpose } = parsed.data;

  const result = await issueOtp(phone, purpose as OtpPurpose);
  res.status(201).json({
    challengeId: result.id,
    expiresAt: result.expiresAt,
    channel: result.channel,
    devCode: result.devCode,
  });
});

const verifyOtpSchema = z.object({
  phone: z.string().min(7).max(15),
  otp: z.string().regex(/^\d{6}$/, 'OTP must be 6 digits'),
  purpose: z.enum(['LOGIN', 'RESET_PIN']).default('LOGIN'),
});

const SETUP_TOKEN_TTL = '15m';
type SetupPayload = { phone: string; userId?: string; purpose: 'LOGIN' | 'RESET_PIN'; use: 'setup' };

/**
 * Verifies the OTP, then returns:
 *   - exists:   true if a user with that phone is in the system
 *   - hasPin:   true if an existing user already has a PIN set
 *   - setupToken: short-lived JWT (15 min) used to set-pin or complete-signup
 *   - user:     name/role, if existing
 */
router.post('/verify-otp', async (req, res) => {
  const parsed = verifyOtpSchema.safeParse(req.body);
  if (!parsed.success) return res.status(400).json({ error: 'Invalid input' });
  const { phone, otp, purpose } = parsed.data;

  const check = await verifyOtp(phone, purpose as OtpPurpose, otp);
  if (!check.ok) return res.status(400).json({ error: 'OTP_INVALID', reason: check.reason });

  const normalized = normalizePhone(phone);
  const user = await prisma.user.findUnique({ where: { phone: normalized } });

  const setupPayload: SetupPayload = {
    phone: normalized,
    userId: user?.id,
    purpose: purpose as 'LOGIN' | 'RESET_PIN',
    use: 'setup',
  };
  const setupToken = jwt.sign(setupPayload, process.env.JWT_SECRET || 'dev-secret-change-me', {
    expiresIn: SETUP_TOKEN_TTL,
  });

  res.json({
    exists: !!user,
    hasPin: !!(user && user.pinHash),
    setupToken,
    user: user ? { id: user.id, name: user.name, role: user.role } : null,
  });
});

/* ─────────────── Set / reset PIN (requires setupToken) ─────────────── */

const setPinSchema = z.object({
  setupToken: z.string().min(10),
  pin: z.string().regex(/^\d{4}$/, 'PIN must be 4 digits'),
  confirmPin: z.string().regex(/^\d{4}$/),
});

router.post('/set-pin', async (req, res) => {
  const parsed = setPinSchema.safeParse(req.body);
  if (!parsed.success) return res.status(400).json({ error: 'Invalid input' });
  const { setupToken, pin, confirmPin } = parsed.data;
  if (pin !== confirmPin) return res.status(400).json({ error: 'PIN_MISMATCH' });

  let payload: SetupPayload;
  try {
    payload = jwt.verify(setupToken, process.env.JWT_SECRET || 'dev-secret-change-me') as SetupPayload;
    if (payload.use !== 'setup') throw new Error('bad use');
  } catch {
    return res.status(401).json({ error: 'INVALID_SETUP_TOKEN' });
  }

  if (!payload.userId) {
    // No account yet — frontend should call /signup/complete instead.
    return res.status(400).json({ error: 'USER_NOT_FOUND', message: 'Call /auth/signup-complete to create company first.' });
  }

  const user = await prisma.user.update({
    where: { id: payload.userId },
    data: { pinHash: bcrypt.hashSync(pin, 10), pinSetAt: new Date() },
  });

  const token = signToken({ userId: user.id, companyId: user.companyId, role: user.role });
  res.json({
    token,
    user: { id: user.id, email: user.email, name: user.name, role: user.role, companyId: user.companyId },
  });
});

/**
 * For NEW phones: create company + admin user with the just-set PIN.
 * The frontend calls this after verify-otp returns exists:false.
 */
const signupCompleteSchema = z.object({
  setupToken: z.string().min(10),
  companyName: z.string().min(2).max(120),
  gstin: z.string().length(15).optional(),
  adminName: z.string().min(1),
  adminEmail: z.string().email(),
  pin: z.string().regex(/^\d{4}$/),
});

router.post('/signup-complete', async (req, res) => {
  const parsed = signupCompleteSchema.safeParse(req.body);
  if (!parsed.success) {
    return res.status(400).json({ error: 'Invalid input', details: parsed.error.flatten() });
  }
  const { setupToken, companyName, gstin, adminName, adminEmail, pin } = parsed.data;

  let payload: SetupPayload;
  try {
    payload = jwt.verify(setupToken, process.env.JWT_SECRET || 'dev-secret-change-me') as SetupPayload;
    if (payload.use !== 'setup') throw new Error('bad use');
  } catch {
    return res.status(401).json({ error: 'INVALID_SETUP_TOKEN' });
  }
  if (payload.userId) {
    return res.status(400).json({ error: 'USER_ALREADY_EXISTS' });
  }

  const existingByEmail = await prisma.user.findUnique({ where: { email: adminEmail } });
  if (existingByEmail) return res.status(409).json({ error: 'Email already in use' });

  const now = new Date();
  const trialEndsAt = new Date(now.getTime() + 7 * 86_400_000);

  const company = await prisma.company.create({
    data: {
      name: companyName,
      gstin,
      billingEmail: adminEmail,
      billingPhone: payload.phone,
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
          phone: payload.phone,
          passwordHash: bcrypt.hashSync(pin, 10), // mirror PIN as password for the legacy /login path
          pinHash: bcrypt.hashSync(pin, 10),
          pinSetAt: now,
          role: 'ADMIN',
        },
      },
    },
    include: { users: true },
  });

  const admin = company.users[0];
  const token = signToken({ userId: admin.id, companyId: company.id, role: admin.role });
  res.status(201).json({
    token,
    company: { id: company.id, name: company.name },
    user: { ...admin, companyId: company.id, passwordHash: undefined, pinHash: undefined },
  });
});

/* ─────────────── Current user ─────────────── */

router.get('/me', requireAuth, async (req, res) => {
  const user = await prisma.user.findUnique({
    where: { id: req.auth!.userId },
    select: {
      id: true, email: true, name: true, role: true, phone: true,
      companyId: true, grade: true, employeeCode: true, managerId: true,
      pinSetAt: true,
    },
  });
  res.json({ user });
});

export default router;
