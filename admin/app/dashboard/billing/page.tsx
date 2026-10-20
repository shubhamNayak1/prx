'use client';

import { useEffect, useState } from 'react';
import { useSearchParams } from 'next/navigation';
import Link from 'next/link';
import { api } from '@/lib/api';
import { getToken, BILLABLE_ROLES, Role } from '@/lib/auth';

type Cycle = 'MONTHLY' | 'QUARTERLY' | 'ANNUAL';
type SubStatus = 'TRIAL' | 'ACTIVE' | 'PAST_DUE' | 'LOCKED' | 'CANCELLED';
type InvStatus = 'DRAFT' | 'SENT' | 'PAID' | 'OVERDUE' | 'VOID';

type Subscription = {
  status: SubStatus;
  cycle: Cycle | null;
  trialEndsAt: string | null;
  currentPeriodStart: string;
  currentPeriodEnd: string;
  pricePerRepPaise: number;
  trialRepCap: number | null;
};

type Invoice = {
  id: string;
  number: string;
  cycle: Cycle;
  periodStart: string;
  periodEnd: string;
  activeRepCount: number;
  pricePerRepPaise: number;
  discountPct: number;
  subtotalPaise: number;
  gstPaise: number;
  totalPaise: number;
  status: InvStatus;
  dueAt: string;
  paidAt: string | null;
  pdfUrl: string | null;
};

const CYCLE_MONTHS: Record<Cycle, number> = { MONTHLY: 1, QUARTERLY: 3, ANNUAL: 12 };
const CYCLE_DISCOUNT: Record<Cycle, number> = { MONTHLY: 0, QUARTERLY: 10, ANNUAL: 20 };
const GST_PCT = 18;

export default function BillingPage() {
  const search = useSearchParams();
  const [sub, setSub] = useState<Subscription | null>(null);
  const [invoices, setInvoices] = useState<Invoice[]>([]);
  const [activeReps, setActiveReps] = useState<number>(1);
  const [loading, setLoading] = useState(true);
  const [selectedCycle, setSelectedCycle] = useState<Cycle>('ANNUAL');
  const [upgrading, setUpgrading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [welcomeDismissed, setWelcomeDismissed] = useState(false);

  async function reload() {
    setLoading(true);
    setError(null);
    try {
      const [statusRes, invRes, usersRes] = await Promise.all([
        api<{ subscription: Subscription }>('/api/billing/status', { token: getToken() }),
        api<{ invoices: Invoice[] }>('/api/billing/invoices', { token: getToken() }),
        api<{ users: Array<{ role: Role; isActive: boolean }> }>('/api/users', { token: getToken() }),
      ]);
      setSub(statusRes.subscription);
      setInvoices(invRes.invoices);
      const billable = usersRes.users.filter(
        (u) => u.isActive && BILLABLE_ROLES.includes(u.role),
      ).length;
      setActiveReps(Math.max(1, billable));
    } catch (e: any) {
      setError(e.message || 'Failed to load billing');
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    reload();
    // If Razorpay bounced us back with ?inv=..., poll once after a short delay
    // in case the webhook hasn't landed yet.
    if (search.get('inv')) {
      const t = setTimeout(reload, 3000);
      return () => clearTimeout(t);
    }
  }, []); // eslint-disable-line react-hooks/exhaustive-deps

  async function upgrade() {
    if (!sub) return;
    setUpgrading(true);
    setError(null);
    try {
      const r = await api<{ invoice: { id: string }; paymentLink: string }>(
        '/api/billing/upgrade',
        {
          method: 'POST',
          token: getToken(),
          body: JSON.stringify({ cycle: selectedCycle }),
        },
      );
      window.open(r.paymentLink, '_blank', 'noopener');
      await reload();
    } catch (e: any) {
      setError(e.message || 'Upgrade failed');
    } finally {
      setUpgrading(false);
    }
  }

  if (loading && !sub) return <div className="text-slate-500">Loading billing…</div>;
  if (!sub) return <div className="text-red-600">{error || 'No subscription found.'}</div>;

  const preview = previewPrice(selectedCycle, activeReps, sub.pricePerRepPaise);
  const showWelcome = search.get('welcome') === '1' && !welcomeDismissed;

  return (
    <div className="space-y-6 max-w-5xl">
      {showWelcome && <WelcomeBanner sub={sub} onDismiss={() => setWelcomeDismissed(true)} />}

      <div className="flex items-center justify-between">
        <h1 className="text-2xl font-semibold">Billing</h1>
        <button
          onClick={reload}
          className="text-xs text-slate-500 hover:text-slate-800 underline underline-offset-2"
        >
          Refresh
        </button>
      </div>

      {error && (
        <div className="rounded-md border border-red-200 bg-red-50 text-red-700 text-sm px-4 py-2">
          {error}
        </div>
      )}

      {/* Status + usage */}
      <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
        <StatusCard sub={sub} />
        <UsageCard sub={sub} activeReps={activeReps} />
        <NextBillCard sub={sub} activeReps={activeReps} />
      </div>

      {/* Upgrade / change cycle */}
      {sub.status !== 'CANCELLED' && (
        <section className="bg-white border border-slate-200 rounded-lg p-6">
          <div className="flex items-start justify-between gap-6 flex-wrap">
            <div>
              <h2 className="text-lg font-semibold">
                {sub.status === 'TRIAL' || sub.status === 'LOCKED' ? 'Choose a plan' : 'Change billing cycle'}
              </h2>
              <p className="text-sm text-slate-600 mt-1">
                You're billed per active field user per month. Quarterly saves 10%, annual saves 20%.
              </p>
            </div>
            <button
              onClick={upgrade}
              disabled={upgrading}
              className="rounded-md bg-brand px-5 py-2 text-sm font-medium text-white hover:bg-brand-dark disabled:opacity-50"
            >
              {upgrading
                ? 'Opening payment…'
                : sub.status === 'TRIAL' || sub.status === 'LOCKED'
                  ? `Pay ${formatInr(preview.totalPaise)} and activate`
                  : `Switch & pay ${formatInr(preview.totalPaise)}`}
            </button>
          </div>

          <div className="mt-5 grid grid-cols-1 md:grid-cols-3 gap-3">
            {(['MONTHLY', 'QUARTERLY', 'ANNUAL'] as const).map((c) => (
              <CycleCard
                key={c}
                cycle={c}
                selected={selectedCycle === c}
                onSelect={() => setSelectedCycle(c)}
                activeReps={activeReps}
                pricePerRepPaise={sub.pricePerRepPaise}
              />
            ))}
          </div>

          <div className="mt-5 rounded-md bg-slate-50 border border-slate-200 p-4 text-sm">
            <div className="font-medium text-slate-700 mb-2">Invoice preview</div>
            <PriceBreakdown pb={preview} />
          </div>
        </section>
      )}

      {/* Invoices */}
      <section className="bg-white border border-slate-200 rounded-lg">
        <div className="px-6 py-4 border-b border-slate-200 flex items-center justify-between">
          <h2 className="text-lg font-semibold">Invoices</h2>
          <div className="text-xs text-slate-500">{invoices.length} total</div>
        </div>
        {invoices.length === 0 ? (
          <div className="p-8 text-center text-slate-500 text-sm">No invoices yet.</div>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-sm">
              <thead className="bg-slate-50 text-slate-600">
                <tr>
                  <th className="text-left px-6 py-2 font-medium">Number</th>
                  <th className="text-left px-4 py-2 font-medium">Period</th>
                  <th className="text-right px-4 py-2 font-medium">Reps</th>
                  <th className="text-right px-4 py-2 font-medium">Total</th>
                  <th className="text-left px-4 py-2 font-medium">Status</th>
                  <th className="text-left px-4 py-2 font-medium">Due</th>
                  <th className="px-4 py-2" />
                </tr>
              </thead>
              <tbody>
                {invoices.map((inv) => (
                  <tr key={inv.id} className="border-t border-slate-100">
                    <td className="px-6 py-3 font-mono text-xs">{inv.number}</td>
                    <td className="px-4 py-3">
                      {fmtDate(inv.periodStart)} → {fmtDate(inv.periodEnd)}
                      <div className="text-xs text-slate-400">{inv.cycle.toLowerCase()}</div>
                    </td>
                    <td className="px-4 py-3 text-right">{inv.activeRepCount}</td>
                    <td className="px-4 py-3 text-right font-medium">{formatInr(inv.totalPaise)}</td>
                    <td className="px-4 py-3">
                      <InvoiceStatusChip status={inv.status} />
                    </td>
                    <td className="px-4 py-3 text-slate-600">
                      {inv.paidAt ? `Paid ${fmtDate(inv.paidAt)}` : fmtDate(inv.dueAt)}
                    </td>
                    <td className="px-4 py-3 text-right">
                      {inv.pdfUrl && (
                        <a
                          href={inv.pdfUrl}
                          target="_blank"
                          rel="noopener"
                          className="text-xs text-brand hover:underline"
                        >
                          PDF
                        </a>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </section>

      <p className="text-xs text-slate-500">
        Prices in INR. GST (18%) is applied on every invoice. Payments processed by Razorpay.
      </p>
    </div>
  );
}

/* ───────────────── sub-components ───────────────── */

function WelcomeBanner({ sub, onDismiss }: { sub: Subscription; onDismiss: () => void }) {
  const daysLeft =
    sub.status === 'TRIAL' && sub.trialEndsAt
      ? Math.max(0, Math.ceil((+new Date(sub.trialEndsAt) - Date.now()) / 86_400_000))
      : null;
  return (
    <div className="rounded-lg border border-brand/30 bg-gradient-to-r from-brand/10 to-emerald-50 p-5">
      <div className="flex items-start justify-between gap-4">
        <div>
          <h2 className="text-lg font-semibold text-slate-900">Welcome to FieldPharma 🎉</h2>
          <p className="text-sm text-slate-700 mt-1">
            Your 7-day free trial is active
            {daysLeft !== null && (
              <span className="font-medium"> — {daysLeft} day{daysLeft === 1 ? '' : 's'} left</span>
            )}
            . Finish setting up your field team from the{' '}
            <Link href="/dashboard" className="underline underline-offset-2 text-brand-dark">
              Overview
            </Link>
            . Pick a plan below any time; your data is kept whichever you choose.
          </p>
        </div>
        <button
          onClick={onDismiss}
          aria-label="Dismiss"
          className="shrink-0 text-slate-400 hover:text-slate-700 text-xl leading-none"
        >
          ×
        </button>
      </div>
    </div>
  );
}

function StatusCard({ sub }: { sub: Subscription }) {
  const daysLeft =
    sub.status === 'TRIAL' && sub.trialEndsAt
      ? Math.max(0, Math.ceil((+new Date(sub.trialEndsAt) - Date.now()) / 86_400_000))
      : null;
  return (
    <div className="bg-white border border-slate-200 rounded-lg p-5">
      <div className="text-xs text-slate-500 uppercase tracking-wide">Subscription</div>
      <div className="mt-1 flex items-center gap-2">
        <SubStatusChip status={sub.status} />
        {sub.cycle && <span className="text-sm text-slate-600">{sub.cycle.toLowerCase()}</span>}
      </div>
      {sub.status === 'TRIAL' && daysLeft !== null && (
        <div className="mt-3 text-sm">
          <span className={daysLeft <= 2 ? 'text-red-600 font-medium' : 'text-slate-700'}>
            {daysLeft} {daysLeft === 1 ? 'day' : 'days'} left in trial
          </span>
        </div>
      )}
      {sub.status === 'ACTIVE' && (
        <div className="mt-3 text-sm text-slate-700">
          Renews on <span className="font-medium">{fmtDate(sub.currentPeriodEnd)}</span>
        </div>
      )}
      {sub.status === 'LOCKED' && (
        <div className="mt-3 text-sm text-red-700">
          Your account is locked. Pay to restore access for your team.
        </div>
      )}
    </div>
  );
}

function UsageCard({ sub, activeReps }: { sub: Subscription; activeReps: number }) {
  const cap = sub.trialRepCap;
  const pct = cap ? Math.min(100, (activeReps / cap) * 100) : null;
  return (
    <div className="bg-white border border-slate-200 rounded-lg p-5">
      <div className="text-xs text-slate-500 uppercase tracking-wide">Billable users</div>
      <div className="mt-1 text-2xl font-semibold">
        {activeReps}
        {cap && <span className="text-sm font-normal text-slate-500"> / {cap}</span>}
      </div>
      {pct !== null && (
        <div className="mt-3 h-1.5 w-full bg-slate-100 rounded">
          <div
            className={`h-full rounded ${pct >= 100 ? 'bg-red-500' : pct >= 80 ? 'bg-amber-500' : 'bg-brand'}`}
            style={{ width: `${pct}%` }}
          />
        </div>
      )}
      <div className="mt-3 text-xs text-slate-500">
        MR + Manager roles with an active account.
      </div>
    </div>
  );
}

function NextBillCard({ sub, activeReps }: { sub: Subscription; activeReps: number }) {
  const cycle = sub.cycle || 'MONTHLY';
  const pb = previewPrice(cycle, activeReps, sub.pricePerRepPaise);
  return (
    <div className="bg-white border border-slate-200 rounded-lg p-5">
      <div className="text-xs text-slate-500 uppercase tracking-wide">
        {sub.status === 'ACTIVE' ? 'Next bill estimate' : 'Starting at'}
      </div>
      <div className="mt-1 text-2xl font-semibold">{formatInr(pb.totalPaise)}</div>
      <div className="mt-1 text-xs text-slate-500">
        {activeReps} × {formatInr(sub.pricePerRepPaise)}/mo × {CYCLE_MONTHS[cycle]}mo
        {CYCLE_DISCOUNT[cycle] > 0 && ` − ${CYCLE_DISCOUNT[cycle]}%`} + GST
      </div>
    </div>
  );
}

function CycleCard({
  cycle,
  selected,
  onSelect,
  activeReps,
  pricePerRepPaise,
}: {
  cycle: Cycle;
  selected: boolean;
  onSelect: () => void;
  activeReps: number;
  pricePerRepPaise: number;
}) {
  const discount = CYCLE_DISCOUNT[cycle];
  const effectivePerRep = Math.round((pricePerRepPaise * (100 - discount)) / 100);
  const months = CYCLE_MONTHS[cycle];
  const label = cycle === 'MONTHLY' ? 'Monthly' : cycle === 'QUARTERLY' ? 'Quarterly' : 'Annual';
  const total = Math.round(effectivePerRep * activeReps * months * (100 + GST_PCT) / 100);
  return (
    <button
      type="button"
      onClick={onSelect}
      className={`text-left rounded-lg border p-4 transition ${
        selected ? 'border-brand ring-2 ring-brand/30 bg-brand/5' : 'border-slate-200 bg-white hover:border-slate-300'
      }`}
    >
      <div className="flex items-center justify-between">
        <div className="font-medium">{label}</div>
        {discount > 0 && (
          <span className="text-[10px] font-semibold px-1.5 py-0.5 rounded bg-green-100 text-green-700">
            SAVE {discount}%
          </span>
        )}
      </div>
      <div className="mt-2 text-xl font-semibold">
        {formatInr(effectivePerRep)}
        <span className="text-sm font-normal text-slate-500">/user/month</span>
      </div>
      <div className="mt-1 text-xs text-slate-500">
        billed {cycle === 'MONTHLY' ? 'every month' : cycle === 'QUARTERLY' ? 'every 3 months' : 'yearly'}
      </div>
      <div className="mt-3 text-xs text-slate-600">
        {activeReps} user{activeReps === 1 ? '' : 's'} → {formatInr(total)} incl. GST
      </div>
    </button>
  );
}

function PriceBreakdown({ pb }: { pb: ReturnType<typeof previewPrice> }) {
  return (
    <table className="w-full text-sm">
      <tbody>
        <Row label={`${pb.activeReps} user${pb.activeReps === 1 ? '' : 's'} × ${formatInr(pb.pricePerRepPaise)} × ${pb.months} month${pb.months === 1 ? '' : 's'}`}>
          {formatInr(pb.grossPaise)}
        </Row>
        {pb.discountPct > 0 && (
          <Row label={`Cycle discount (${pb.discountPct}%)`}>
            <span className="text-green-700">− {formatInr(pb.grossPaise - pb.subtotalPaise)}</span>
          </Row>
        )}
        <Row label="Subtotal">{formatInr(pb.subtotalPaise)}</Row>
        <Row label="GST (18%)">{formatInr(pb.gstPaise)}</Row>
        <tr className="border-t border-slate-200">
          <td className="pt-2 font-semibold">Total</td>
          <td className="pt-2 text-right font-semibold">{formatInr(pb.totalPaise)}</td>
        </tr>
      </tbody>
    </table>
  );
}

function Row({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <tr>
      <td className="py-0.5 text-slate-600">{label}</td>
      <td className="py-0.5 text-right text-slate-800">{children}</td>
    </tr>
  );
}

function SubStatusChip({ status }: { status: SubStatus }) {
  const map: Record<SubStatus, string> = {
    TRIAL: 'bg-amber-100 text-amber-800',
    ACTIVE: 'bg-green-100 text-green-700',
    PAST_DUE: 'bg-orange-100 text-orange-700',
    LOCKED: 'bg-red-100 text-red-700',
    CANCELLED: 'bg-slate-100 text-slate-600',
  };
  return <span className={`text-xs font-medium px-2 py-0.5 rounded ${map[status]}`}>{status}</span>;
}

function InvoiceStatusChip({ status }: { status: InvStatus }) {
  const map: Record<InvStatus, string> = {
    DRAFT: 'bg-slate-100 text-slate-700',
    SENT: 'bg-blue-100 text-blue-700',
    PAID: 'bg-green-100 text-green-700',
    OVERDUE: 'bg-red-100 text-red-700',
    VOID: 'bg-slate-100 text-slate-500 line-through',
  };
  return <span className={`text-xs font-medium px-2 py-0.5 rounded ${map[status]}`}>{status}</span>;
}

/* ───────────────── helpers ───────────────── */

function previewPrice(cycle: Cycle, activeReps: number, pricePerRepPaise: number) {
  const months = CYCLE_MONTHS[cycle];
  const discountPct = CYCLE_DISCOUNT[cycle];
  const gross = pricePerRepPaise * activeReps * months;
  const subtotal = Math.round((gross * (100 - discountPct)) / 100);
  const gst = Math.round((subtotal * GST_PCT) / 100);
  return {
    cycle,
    activeReps,
    months,
    pricePerRepPaise,
    discountPct,
    grossPaise: gross,
    subtotalPaise: subtotal,
    gstPaise: gst,
    totalPaise: subtotal + gst,
  };
}

function formatInr(paise: number): string {
  const rupees = paise / 100;
  return rupees.toLocaleString('en-IN', {
    style: 'currency',
    currency: 'INR',
    maximumFractionDigits: rupees % 1 === 0 ? 0 : 2,
  });
}

function fmtDate(iso: string): string {
  const d = new Date(iso);
  return d.toLocaleDateString('en-IN', { day: '2-digit', month: 'short', year: 'numeric' });
}
