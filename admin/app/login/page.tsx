'use client';

import { useEffect, useState } from 'react';
import { useRouter } from 'next/navigation';
import { api } from '@/lib/api';
import { saveAuth, AuthUser } from '@/lib/auth';

type Mode = 'signin' | 'signup';

export default function LoginPage() {
  const [mode, setMode] = useState<Mode>('signin');

  return (
    <div className="min-h-screen flex items-center justify-center px-4 bg-slate-50">
      <div className="w-full max-w-md bg-white rounded-xl shadow p-8 space-y-5">
        <div>
          <h1 className="text-xl font-semibold text-brand">Baseras FieldPharma</h1>
          <p className="text-sm text-slate-500">
            {mode === 'signin' ? 'Admin & Manager login' : 'Start your free 7-day trial'}
          </p>
        </div>

        <div className="grid grid-cols-2 bg-slate-100 rounded-md p-1 text-sm">
          <TabButton active={mode === 'signin'} onClick={() => setMode('signin')}>
            Sign in
          </TabButton>
          <TabButton active={mode === 'signup'} onClick={() => setMode('signup')}>
            Create account
          </TabButton>
        </div>

        {mode === 'signin' ? <SignInForm /> : <SignUpForm onSwitchToSignIn={() => setMode('signin')} />}

        <p className="text-[11px] text-slate-400 text-center">
          By continuing you agree to Baseras's Terms and Privacy policy.
        </p>
      </div>
    </div>
  );
}

function TabButton({
  active,
  onClick,
  children,
}: {
  active: boolean;
  onClick: () => void;
  children: React.ReactNode;
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      className={`py-1.5 rounded ${active ? 'bg-white shadow-sm font-medium text-slate-800' : 'text-slate-500 hover:text-slate-700'}`}
    >
      {children}
    </button>
  );
}

/* ───────────────── Sign in ───────────────── */

function SignInForm() {
  const router = useRouter();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [err, setErr] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    setErr(null);
    setLoading(true);
    try {
      const res = await api<{ token: string; user: AuthUser }>('/api/auth/login', {
        method: 'POST',
        body: JSON.stringify({ email, password }),
      });
      if (res.user.role === 'FIELD_PERSON') {
        setErr('Field Person accounts must use the mobile app.');
        setLoading(false);
        return;
      }
      saveAuth(res.token, res.user);
      router.push('/dashboard');
    } catch (e: any) {
      setErr(e.message);
      setLoading(false);
    }
  }

  return (
    <form onSubmit={handleSubmit} className="space-y-4">
      <Field label="Work email">
        <input
          type="email"
          required
          value={email}
          onChange={(e) => setEmail(e.target.value)}
          autoComplete="email"
          className={inputCls}
        />
      </Field>
      <Field label="Password">
        <input
          type="password"
          required
          value={password}
          onChange={(e) => setPassword(e.target.value)}
          autoComplete="current-password"
          className={inputCls}
        />
      </Field>

      {err && <ErrorBox>{err}</ErrorBox>}

      <SubmitButton loading={loading} label="Sign in" loadingLabel="Signing in…" />
    </form>
  );
}

/* ───────────────── Sign up (two-step with OTP) ───────────────── */

type Channel = 'whatsapp' | 'sms' | 'dev';

function SignUpForm({ onSwitchToSignIn }: { onSwitchToSignIn: () => void }) {
  const router = useRouter();
  const [step, setStep] = useState<'phone' | 'details'>('phone');

  // Phone step state
  const [adminPhone, setAdminPhone] = useState('');
  const [channel, setChannel] = useState<Channel | null>(null);
  const [otpExpiresAt, setOtpExpiresAt] = useState<string | null>(null);
  const [devCode, setDevCode] = useState<string | null>(null);
  const [otp, setOtp] = useState('');

  // Details step state
  const [companyName, setCompanyName] = useState('');
  const [gstin, setGstin] = useState('');
  const [adminName, setAdminName] = useState('');
  const [adminEmail, setAdminEmail] = useState('');
  const [adminPassword, setAdminPassword] = useState('');
  const [agreed, setAgreed] = useState(false);

  const [err, setErr] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  async function requestOtp(e?: React.FormEvent) {
    e?.preventDefault();
    setErr(null);
    if (adminPhone.trim().length < 7) {
      setErr('Enter a valid phone number.');
      return;
    }
    setLoading(true);
    try {
      const res = await api<{
        channel: Channel;
        expiresAt: string;
        devCode?: string;
      }>('/api/signup/request-otp', {
        method: 'POST',
        body: JSON.stringify({ phone: adminPhone.trim() }),
      });
      setChannel(res.channel);
      setOtpExpiresAt(res.expiresAt);
      setDevCode(res.devCode || null);
      setStep('details');
    } catch (e: any) {
      setErr(e.message || 'Could not send OTP');
    } finally {
      setLoading(false);
    }
  }

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    setErr(null);

    const gstinTrimmed = gstin.trim().toUpperCase();
    if (gstinTrimmed && gstinTrimmed.length !== 15) {
      setErr('GSTIN must be exactly 15 characters, or left blank.');
      return;
    }
    if (adminPassword.length < 6) {
      setErr('Password must be at least 6 characters.');
      return;
    }
    if (!/^\d{6}$/.test(otp)) {
      setErr('Enter the 6-digit code we sent you.');
      return;
    }
    if (!agreed) {
      setErr('Please accept the terms to continue.');
      return;
    }

    setLoading(true);
    try {
      const res = await api<{
        token: string;
        user: AuthUser;
        company: { id: string; name: string };
      }>('/api/signup', {
        method: 'POST',
        body: JSON.stringify({
          companyName: companyName.trim(),
          gstin: gstinTrimmed || undefined,
          adminName: adminName.trim(),
          adminEmail: adminEmail.trim().toLowerCase(),
          adminPhone: adminPhone.trim(),
          adminPassword,
          otp,
        }),
      });
      saveAuth(res.token, res.user);
      router.push('/dashboard/billing?welcome=1');
    } catch (e: any) {
      const msg = e.message || '';
      if (/already in use/i.test(msg)) {
        setErr('That email is already registered. Try signing in instead.');
      } else if (/OTP_INVALID|INCORRECT_CODE|EXPIRED|TOO_MANY/i.test(msg)) {
        setErr('That code is wrong or expired. Resend to try again.');
      } else {
        setErr(msg);
      }
      setLoading(false);
    }
  }

  // ───── Step 1: phone + request OTP ─────
  if (step === 'phone') {
    return (
      <form onSubmit={requestOtp} className="space-y-4">
        <TrialBadge />
        <Field label="Phone (WhatsApp)" hint="We'll send a one-time code">
          <input
            type="tel"
            required
            inputMode="tel"
            value={adminPhone}
            onChange={(e) => setAdminPhone(e.target.value)}
            placeholder="98765 43210"
            autoComplete="tel"
            minLength={7}
            maxLength={15}
            className={inputCls}
          />
        </Field>
        {err && <ErrorBox>{err}</ErrorBox>}
        <SubmitButton loading={loading} label="Send verification code" loadingLabel="Sending…" />
        <p className="text-[11px] text-slate-500 text-center">
          We send the code on WhatsApp first; SMS is a fallback.
        </p>
      </form>
    );
  }

  // ───── Step 2: fill rest, enter OTP, submit ─────
  return (
    <form onSubmit={handleSubmit} className="space-y-4">
      <OtpStatus
        phone={adminPhone}
        channel={channel}
        devCode={devCode}
        expiresAt={otpExpiresAt}
        onChangePhone={() => {
          setStep('phone');
          setOtp('');
          setErr(null);
        }}
        onResend={async () => {
          setErr(null);
          setLoading(true);
          try {
            const res = await api<{ channel: Channel; expiresAt: string; devCode?: string }>(
              '/api/signup/request-otp',
              {
                method: 'POST',
                body: JSON.stringify({ phone: adminPhone.trim() }),
              },
            );
            setChannel(res.channel);
            setOtpExpiresAt(res.expiresAt);
            setDevCode(res.devCode || null);
          } catch (e: any) {
            setErr(e.message || 'Could not resend OTP');
          } finally {
            setLoading(false);
          }
        }}
      />

      <Field label="6-digit code">
        <input
          type="text"
          required
          inputMode="numeric"
          pattern="\d{6}"
          maxLength={6}
          value={otp}
          onChange={(e) => setOtp(e.target.value.replace(/\D/g, '').slice(0, 6))}
          className={`${inputCls} tracking-[0.4em] font-mono text-center text-lg`}
          autoComplete="one-time-code"
        />
      </Field>

      <Field label="Company name">
        <input
          type="text"
          required
          minLength={2}
          maxLength={120}
          value={companyName}
          onChange={(e) => setCompanyName(e.target.value)}
          placeholder="Acme Pharmaceuticals Pvt Ltd"
          className={inputCls}
        />
      </Field>

      <Field label="GSTIN" hint="Optional — needed on paid invoices">
        <input
          type="text"
          value={gstin}
          onChange={(e) => setGstin(e.target.value)}
          placeholder="27AAAAA0000A1Z5"
          maxLength={15}
          className={`${inputCls} font-mono uppercase tracking-wide`}
        />
      </Field>

      <Field label="Your name">
        <input
          type="text"
          required
          value={adminName}
          onChange={(e) => setAdminName(e.target.value)}
          autoComplete="name"
          className={inputCls}
        />
      </Field>

      <Field label="Work email">
        <input
          type="email"
          required
          value={adminEmail}
          onChange={(e) => setAdminEmail(e.target.value)}
          autoComplete="email"
          className={inputCls}
        />
      </Field>

      <Field label="Password" hint="At least 6 characters">
        <input
          type="password"
          required
          minLength={6}
          value={adminPassword}
          onChange={(e) => setAdminPassword(e.target.value)}
          autoComplete="new-password"
          className={inputCls}
        />
      </Field>

      <label className="flex items-start gap-2 text-xs text-slate-600 cursor-pointer">
        <input
          type="checkbox"
          checked={agreed}
          onChange={(e) => setAgreed(e.target.checked)}
          className="mt-0.5"
        />
        <span>
          I accept the Terms of Service and the Privacy Policy, and I confirm I'm authorised to
          sign up on behalf of this company.
        </span>
      </label>

      {err && (
        <ErrorBox>
          {err}
          {/already registered/i.test(err) && (
            <button type="button" onClick={onSwitchToSignIn} className="ml-1 underline">
              Sign in →
            </button>
          )}
        </ErrorBox>
      )}

      <SubmitButton
        loading={loading}
        label="Create account & start trial"
        loadingLabel="Creating account…"
      />
    </form>
  );
}

function OtpStatus({
  phone,
  channel,
  devCode,
  expiresAt,
  onChangePhone,
  onResend,
}: {
  phone: string;
  channel: Channel | null;
  devCode: string | null;
  expiresAt: string | null;
  onChangePhone: () => void;
  onResend: () => void;
}) {
  const [now, setNow] = useState(Date.now());
  useEffect(() => {
    const t = setInterval(() => setNow(Date.now()), 1000);
    return () => clearInterval(t);
  }, []);
  const secLeft = expiresAt ? Math.max(0, Math.floor((+new Date(expiresAt) - now) / 1000)) : 0;
  const mm = String(Math.floor(secLeft / 60)).padStart(2, '0');
  const ss = String(secLeft % 60).padStart(2, '0');

  const channelLabel =
    channel === 'whatsapp' ? 'WhatsApp' : channel === 'sms' ? 'SMS' : 'the server console (dev)';

  return (
    <div className="rounded-md border border-brand/30 bg-brand/5 px-3 py-2.5 text-xs">
      <div className="flex items-center justify-between gap-2 flex-wrap">
        <div className="text-slate-700">
          Code sent on <span className="font-semibold">{channelLabel}</span> to{' '}
          <span className="font-mono">{phone}</span>
        </div>
        <div className="text-slate-500">
          {secLeft > 0 ? (
            <>
              expires in <span className="font-mono">{mm}:{ss}</span>
            </>
          ) : (
            'expired'
          )}
        </div>
      </div>
      <div className="mt-1.5 flex items-center gap-3 text-xs">
        <button type="button" onClick={onChangePhone} className="text-slate-500 underline underline-offset-2">
          Change number
        </button>
        <button type="button" onClick={onResend} className="text-brand-dark underline underline-offset-2">
          Resend
        </button>
      </div>
      {devCode && (
        <div className="mt-2 text-[11px] text-amber-700 bg-amber-50 border border-amber-200 rounded px-2 py-1">
          Dev mode (no SMS provider configured): code is <span className="font-mono font-semibold">{devCode}</span>
        </div>
      )}
    </div>
  );
}

function TrialBadge() {
  return (
    <div className="rounded-md border border-brand/30 bg-brand/5 px-3 py-2 text-xs text-brand-dark">
      <div className="font-semibold">7-day free trial — no credit card</div>
      <div className="text-slate-600">
        Up to 5 field users. Full access to attendance, visits, DCR, samples, expenses, RCPA and
        e-detailing. Upgrade any time from the Billing page.
      </div>
    </div>
  );
}

/* ───────────────── Shared bits ───────────────── */

const inputCls =
  'mt-1 w-full rounded-md border border-slate-300 px-3 py-2 text-sm focus:border-brand focus:ring-1 focus:ring-brand outline-none';

function Field({
  label,
  hint,
  children,
}: {
  label: string;
  hint?: string;
  children: React.ReactNode;
}) {
  return (
    <label className="block">
      <span className="flex items-baseline justify-between">
        <span className="text-sm text-slate-700">{label}</span>
        {hint && <span className="text-[11px] text-slate-400">{hint}</span>}
      </span>
      {children}
    </label>
  );
}

function ErrorBox({ children }: { children: React.ReactNode }) {
  return (
    <div className="rounded-md bg-red-50 text-red-700 text-sm px-3 py-2 border border-red-100">
      {children}
    </div>
  );
}

function SubmitButton({
  loading,
  label,
  loadingLabel,
}: {
  loading: boolean;
  label: string;
  loadingLabel: string;
}) {
  return (
    <button
      type="submit"
      disabled={loading}
      className="w-full rounded-md bg-brand text-white py-2 text-sm font-medium hover:bg-brand-dark disabled:opacity-50"
    >
      {loading ? loadingLabel : label}
    </button>
  );
}
