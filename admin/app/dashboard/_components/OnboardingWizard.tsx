'use client';

import Link from 'next/link';
import { useEffect, useState } from 'react';
import { api } from '@/lib/api';
import { getToken, MANAGER_ROLES, Role } from '@/lib/auth';

type Step = {
  key: string;
  label: string;
  description: string;
  cta: string;
  href: string;
  done: boolean;
  optional?: boolean;
};

/**
 * Shows a first-run checklist on the dashboard overview. Fetches live counts
 * so it hides itself automatically once setup is complete. No state persisted
 * to localStorage — if the user removes all MRs the checklist re-appears,
 * which is the right behaviour.
 */
export default function OnboardingWizard() {
  const [steps, setSteps] = useState<Step[] | null>(null);
  const [collapsed, setCollapsed] = useState(false);

  useEffect(() => {
    void load();
  }, []);

  async function load() {
    try {
      const [users, clients, policies, decks] = await Promise.all([
        api<{ users: Array<{ role: Role; isActive: boolean }> }>('/api/users', { token: getToken() }),
        api<{ clients: Array<{ id: string }> }>('/api/clients', { token: getToken() }),
        api<{ policies: Array<{ id: string }> }>('/api/expense-policies', { token: getToken() })
          .catch(() => ({ policies: [] })),
        api<{ decks: Array<{ id: string }> }>('/api/edetail/decks', { token: getToken() })
          .catch(() => ({ decks: [] })),
      ]);

      const activeFieldPersons = users.users.filter((u) => u.isActive && u.role === 'FIELD_PERSON').length;
      const activeManagers = users.users.filter((u) => u.isActive && MANAGER_ROLES.includes(u.role)).length;

      setSteps([
        {
          key: 'field',
          label: 'Add your first Field Person',
          description: 'Field Persons sign in to the Android app and start logging visits.',
          cta: 'Add Field Person',
          href: '/dashboard/users/new',
          done: activeFieldPersons > 0,
        },
        {
          key: 'client',
          label: 'Add your first client',
          description: 'A doctor, chemist or stockist for MRs to visit.',
          cta: 'Add client',
          href: '/dashboard/clients/new',
          done: clients.clients.length > 0,
        },
        {
          key: 'policy',
          label: 'Set TA / DA rates',
          description: 'Per-grade travel + daily allowance so expenses auto-calculate.',
          cta: 'Open rates',
          href: '/dashboard/expense-policies',
          done: policies.policies.length > 0,
        },
        {
          key: 'manager',
          label: 'Invite a Regional or Senior Manager',
          description: 'Managers approve tour plans, expenses and new clients, and see team reports.',
          cta: 'Invite manager',
          href: '/dashboard/users/new',
          done: activeManagers > 0,
          optional: true,
        },
        {
          key: 'deck',
          label: 'Upload an e-detailing deck',
          description: 'Product slides MRs can show to doctors, offline.',
          cta: 'Upload deck',
          href: '/dashboard/edetail',
          done: decks.decks.length > 0,
          optional: true,
        },
      ]);
    } catch {
      // On error, don't show the wizard — avoids an empty state blocking the dashboard.
      setSteps([]);
    }
  }

  if (!steps || steps.length === 0) return null;

  const required = steps.filter((s) => !s.optional);
  const doneCount = required.filter((s) => s.done).length;
  const total = required.length;
  if (doneCount >= total) return null; // all required steps complete → hide for good

  const pct = Math.round((doneCount / total) * 100);

  return (
    <section className="bg-gradient-to-r from-brand/5 to-emerald-50 border border-brand/30 rounded-lg p-5">
      <div className="flex items-start justify-between gap-3">
        <div>
          <div className="flex items-center gap-2">
            <h2 className="font-semibold text-slate-900">Finish setting up FieldPharma</h2>
            <span className="text-xs text-slate-600">
              {doneCount} of {total} done
            </span>
          </div>
          <p className="text-sm text-slate-600 mt-0.5">
            A few quick steps to get your field team on the app.
          </p>
        </div>
        <button
          onClick={() => setCollapsed((c) => !c)}
          className="text-xs text-slate-500 hover:text-slate-800 underline underline-offset-2 shrink-0"
        >
          {collapsed ? 'Expand' : 'Hide'}
        </button>
      </div>

      <div className="mt-3 h-1.5 w-full bg-slate-200 rounded">
        <div
          className="h-full rounded bg-brand transition-all duration-500"
          style={{ width: `${pct}%` }}
        />
      </div>

      {!collapsed && (
        <ul className="mt-4 space-y-2">
          {steps.map((s) => (
            <li
              key={s.key}
              className="flex items-start gap-3 bg-white border border-slate-200 rounded-md px-4 py-3"
            >
              <StepCheck done={s.done} />
              <div className="flex-1 min-w-0">
                <div className="flex items-center gap-2 flex-wrap">
                  <span className={`text-sm font-medium ${s.done ? 'text-slate-500 line-through' : 'text-slate-900'}`}>
                    {s.label}
                  </span>
                  {s.optional && !s.done && (
                    <span className="text-[10px] font-semibold text-slate-500 uppercase tracking-wide">
                      optional
                    </span>
                  )}
                </div>
                <div className="text-xs text-slate-500 mt-0.5">{s.description}</div>
              </div>
              {!s.done && (
                <Link
                  href={s.href}
                  className="shrink-0 self-center text-xs rounded-md bg-brand text-white px-3 py-1.5 hover:bg-brand-dark"
                >
                  {s.cta}
                </Link>
              )}
            </li>
          ))}
        </ul>
      )}
    </section>
  );
}

function StepCheck({ done }: { done: boolean }) {
  return (
    <div
      className={`shrink-0 mt-0.5 w-5 h-5 rounded-full flex items-center justify-center text-[11px] font-bold ${
        done ? 'bg-brand text-white' : 'border-2 border-slate-300 text-transparent'
      }`}
    >
      ✓
    </div>
  );
}
