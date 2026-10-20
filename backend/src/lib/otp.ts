import crypto from 'crypto';
import bcrypt from 'bcryptjs';
import { OtpPurpose } from '@prisma/client';
import { prisma } from './prisma';

const OTP_LENGTH = 6;
const OTP_TTL_MIN = 10;
const MAX_ATTEMPTS = 5;

export type OtpChannel = 'whatsapp' | 'sms' | 'dev';

export type OtpIssueResult = {
  id: string;
  expiresAt: Date;
  channel: OtpChannel;
  /** Dev-only: the raw OTP code, returned when no provider is configured so a local developer can continue the flow. */
  devCode?: string;
};

/** Generates a 6-digit numeric code using crypto.randomInt (not Math.random). */
function makeCode(): string {
  return String(crypto.randomInt(0, 10 ** OTP_LENGTH)).padStart(OTP_LENGTH, '0');
}

export async function issueOtp(phone: string, purpose: OtpPurpose): Promise<OtpIssueResult> {
  const code = makeCode();
  const codeHash = bcrypt.hashSync(code, 10);
  const expiresAt = new Date(Date.now() + OTP_TTL_MIN * 60 * 1000);

  const row = await prisma.otpChallenge.create({
    data: { phone: normalizePhone(phone), purpose, codeHash, expiresAt },
    select: { id: true, expiresAt: true },
  });

  const channel = await deliverOtp(phone, code);
  return {
    id: row.id,
    expiresAt: row.expiresAt,
    channel,
    devCode: channel === 'dev' ? code : undefined,
  };
}

export async function verifyOtp(
  phone: string,
  purpose: OtpPurpose,
  code: string,
): Promise<{ ok: true } | { ok: false; reason: string }> {
  const normalized = normalizePhone(phone);
  const row = await prisma.otpChallenge.findFirst({
    where: {
      phone: normalized,
      purpose,
      consumedAt: null,
      expiresAt: { gt: new Date() },
    },
    orderBy: { createdAt: 'desc' },
  });
  if (!row) return { ok: false, reason: 'EXPIRED_OR_UNKNOWN' };

  if (row.attempts >= MAX_ATTEMPTS) {
    return { ok: false, reason: 'TOO_MANY_ATTEMPTS' };
  }

  // Always bump attempts first so brute-force attempts count even on success.
  await prisma.otpChallenge.update({
    where: { id: row.id },
    data: { attempts: { increment: 1 } },
  });

  const match = bcrypt.compareSync(code, row.codeHash);
  if (!match) return { ok: false, reason: 'INCORRECT_CODE' };

  await prisma.otpChallenge.update({
    where: { id: row.id },
    data: { consumedAt: new Date() },
  });
  return { ok: true };
}

/** Strips spaces, hyphens and any leading "+91" so lookups are consistent. */
export function normalizePhone(raw: string): string {
  return raw.replace(/[\s-]/g, '').replace(/^(\+?91)?/, '');
}

/**
 * Dispatches the OTP. Prefers WhatsApp (via MSG91) when configured; falls back
 * to SMS; prints to the server console in dev when neither is set.
 *
 * Returns the channel actually used. "dev" means the OTP was NOT delivered
 * to the user — the caller then surfaces the raw code in the response.
 */
async function deliverOtp(phone: string, code: string): Promise<OtpChannel> {
  if (await sendOtpWhatsApp(phone, code)) return 'whatsapp';
  if (await sendOtpTextSms(phone, code)) return 'sms';
  // eslint-disable-next-line no-console
  console.log(`[otp:dev] phone=${phone} code=${code}`);
  return 'dev';
}

/**
 * MSG91 WhatsApp Business API. Requires:
 *   MSG91_WA_AUTH_KEY            — account auth key
 *   MSG91_WA_INTEGRATED_NUMBER   — your approved WABA sender number (digits only)
 *   MSG91_WA_TEMPLATE_NAME       — approved template name with ONE body variable (the OTP)
 *   MSG91_WA_LANGUAGE_CODE       — defaults to "en"
 *
 * Template body example: "Your FieldPharma verification code is {{1}}."
 * See https://docs.msg91.com/whatsapp
 */
async function sendOtpWhatsApp(phone: string, code: string): Promise<boolean> {
  const authKey = process.env.MSG91_WA_AUTH_KEY;
  const integratedNumber = process.env.MSG91_WA_INTEGRATED_NUMBER;
  const templateName = process.env.MSG91_WA_TEMPLATE_NAME;
  const languageCode = process.env.MSG91_WA_LANGUAGE_CODE || 'en';
  if (!authKey || !integratedNumber || !templateName) return false;

  const toNumber = `91${normalizePhone(phone)}`;
  const body = {
    integrated_number: integratedNumber,
    content_type: 'template',
    payload: {
      messaging_product: 'whatsapp',
      type: 'template',
      template: {
        name: templateName,
        language: { code: languageCode, policy: 'deterministic' },
        namespace: process.env.MSG91_WA_NAMESPACE || undefined,
        to_and_components: [
          {
            to: [toNumber],
            components: {
              body_1: { type: 'text', value: code },
              // Many MSG91 OTP templates also expect a URL button carrying
              // the code — harmless when the template doesn't declare one.
              button_1: { subtype: 'url', type: 'text', value: code },
            },
          },
        ],
      },
    },
  };

  try {
    const resp = await fetch(
      'https://control.msg91.com/api/v5/whatsapp/whatsapp-outbound-message/bulk/',
      {
        method: 'POST',
        headers: { authkey: authKey, 'content-type': 'application/json' },
        body: JSON.stringify(body),
      },
    );
    if (!resp.ok) {
      // eslint-disable-next-line no-console
      console.error(`[otp:wa] MSG91 WhatsApp send failed: ${resp.status} ${await resp.text()}`);
      return false;
    }
    return true;
  } catch (e) {
    // eslint-disable-next-line no-console
    console.error('[otp:wa] MSG91 WhatsApp send error', e);
    return false;
  }
}

/**
 * MSG91 OTP (SMS) endpoint — DLT-registered template with ##OTP## variable.
 * Used only when WhatsApp is unavailable (template rejected, user doesn't
 * have WhatsApp, etc).
 */
async function sendOtpTextSms(phone: string, code: string): Promise<boolean> {
  const authKey = process.env.MSG91_AUTH_KEY;
  const senderId = process.env.MSG91_SENDER_ID;
  const templateId = process.env.MSG91_OTP_TEMPLATE_ID;
  if (!authKey || !senderId || !templateId) return false;

  const url = new URL('https://control.msg91.com/api/v5/otp');
  url.searchParams.set('template_id', templateId);
  url.searchParams.set('mobile', `91${normalizePhone(phone)}`);
  url.searchParams.set('otp', code);
  url.searchParams.set('sender', senderId);

  try {
    const resp = await fetch(url, {
      method: 'POST',
      headers: { authkey: authKey, 'content-type': 'application/json' },
    });
    if (!resp.ok) {
      // eslint-disable-next-line no-console
      console.error(`[otp:sms] MSG91 send failed: ${resp.status} ${await resp.text()}`);
      return false;
    }
    return true;
  } catch (e) {
    // eslint-disable-next-line no-console
    console.error('[otp:sms] MSG91 send error', e);
    return false;
  }
}

/** Periodic cleanup — hit from a cron like the billing ones. */
export async function purgeExpiredOtps(): Promise<number> {
  const cutoff = new Date(Date.now() - 24 * 60 * 60 * 1000);
  const r = await prisma.otpChallenge.deleteMany({
    where: { expiresAt: { lt: cutoff } },
  });
  return r.count;
}
