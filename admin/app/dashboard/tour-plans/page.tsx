'use client';

import { useEffect, useState } from 'react';
import { api } from '@/lib/api';
import { getToken } from '@/lib/auth';

type Entry = {
  id: string;
  area: string | null;
  client: { id: string; name: string; type: string } | null;
};

type Plan = {
  id: string;
  date: string;
  status: 'PLANNED' | 'APPROVED' | 'REJECTED' | 'COMPLETED';
  notes: string | null;
  user: { id: string; name: string; employeeCode: string | null };
  entries: Entry[];
};

export default function TourPlansPage() {
  const [plans, setPlans] = useState<Plan[]>([]);
  const [loading, setLoading] = useState(true);
  const [filter, setFilter] = useState<'PLANNED' | 'ALL'>('PLANNED');

  async function reload() {
    setLoading(true);
    const r = await api<{ plans: Plan[] }>('/api/tour-plans', { token: getToken() });
    setPlans(r.plans);
    setLoading(false);
  }

  useEffect(() => {
    reload();
  }, []);

  async function review(id: string, status: 'APPROVED' | 'REJECTED') {
    await api(`/api/tour-plans/${id}/review`, {
      method: 'PATCH',
      token: getToken(),
      body: JSON.stringify({ status }),
    });
    reload();
  }

  const visible = filter === 'ALL' ? plans : plans.filter((p) => p.status === 'PLANNED');

  return (
    <div className="space-y-5">
      <div className="flex items-center justify-between">
        <h1 className="text-2xl font-semibold">Tour plans</h1>
        <div className="flex gap-2">
          {(['PLANNED', 'ALL'] as const).map((f) => (
            <button
              key={f}
              onClick={() => setFilter(f)}
              className={`px-3 py-1 rounded-md text-sm ${
                filter === f ? 'bg-brand text-white' : 'bg-white border border-slate-200 text-slate-700'
              }`}
            >
              {f === 'PLANNED' ? 'Pending review' : 'All'}
            </button>
          ))}
        </div>
      </div>

      {loading ? (
        <div className="text-slate-500">Loading…</div>
      ) : visible.length === 0 ? (
        <div className="bg-white border border-slate-200 rounded-lg p-8 text-center text-slate-500">
          {filter === 'PLANNED' ? 'No pending tour plans' : 'No tour plans yet'}
        </div>
      ) : (
        <div className="space-y-3">
          {visible.map((p) => (
            <div key={p.id} className="bg-white border border-slate-200 rounded-lg p-5">
              <div className="flex items-start justify-between gap-4">
                <div>
                  <div className="flex items-center gap-2">
                    <div className="font-medium">{p.user.name}</div>
                    {p.user.employeeCode && <div className="text-xs text-slate-500">({p.user.employeeCode})</div>}
                    <StatusChip status={p.status} />
                  </div>
                  <div className="text-sm text-slate-600">{p.date}</div>
                  {p.notes && <div className="text-sm text-slate-700 mt-1">{p.notes}</div>}
                  <ul className="mt-3 text-sm space-y-1">
                    {p.entries.length === 0 && <li className="text-slate-400">(no entries)</li>}
                    {p.entries.map((e) => (
                      <li key={e.id}>
                        • {e.client ? `${e.client.name} (${e.client.type})` : e.area || '—'}
                      </li>
                    ))}
                  </ul>
                </div>
                {p.status === 'PLANNED' && (
                  <div className="flex flex-col gap-2 shrink-0">
                    <button
                      onClick={() => review(p.id, 'APPROVED')}
                      className="rounded-md bg-brand px-3 py-1.5 text-xs text-white hover:bg-brand-dark"
                    >
                      Approve
                    </button>
                    <button
                      onClick={() => review(p.id, 'REJECTED')}
                      className="rounded-md border border-red-300 text-red-700 px-3 py-1.5 text-xs hover:bg-red-50"
                    >
                      Reject
                    </button>
                  </div>
                )}
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}

function StatusChip({ status }: { status: Plan['status'] }) {
  const colors: Record<Plan['status'], string> = {
    PLANNED: 'bg-amber-100 text-amber-800',
    APPROVED: 'bg-green-100 text-green-700',
    REJECTED: 'bg-red-100 text-red-700',
    COMPLETED: 'bg-slate-100 text-slate-700',
  };
  return <span className={`text-xs px-2 py-0.5 rounded ${colors[status]}`}>{status}</span>;
}
