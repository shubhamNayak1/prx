'use client';

import { useEffect, useState } from 'react';
import { api } from '@/lib/api';
import { getToken } from '@/lib/auth';

type Visit = {
  id: string;
  checkInAt: string;
  checkOutAt: string | null;
  notes: string | null;
  client: { id: string; name: string; type: string } | null;
  user: { id: string; name: string };
};

export default function VisitsPage() {
  const [visits, setVisits] = useState<Visit[]>([]);
  const [loading, setLoading] = useState(true);

  async function reload() {
    setLoading(true);
    const r = await api<{ visits: Visit[] }>('/api/visits', { token: getToken() });
    setVisits(r.visits);
    setLoading(false);
  }

  useEffect(() => {
    reload();
  }, []);

  function durationMins(v: Visit) {
    if (!v.checkOutAt) return null;
    const ms = new Date(v.checkOutAt).getTime() - new Date(v.checkInAt).getTime();
    return Math.round(ms / 60000);
  }

  return (
    <div className="space-y-5">
      <h1 className="text-2xl font-semibold">Visits / DCR</h1>
      <div className="bg-white border border-slate-200 rounded-lg overflow-hidden">
        <table className="w-full text-sm">
          <thead className="bg-slate-50 text-slate-600">
            <tr>
              <th className="text-left px-4 py-3">Date / time</th>
              <th className="text-left px-4 py-3">MR</th>
              <th className="text-left px-4 py-3">Client</th>
              <th className="text-left px-4 py-3">Duration</th>
              <th className="text-left px-4 py-3">Notes</th>
            </tr>
          </thead>
          <tbody>
            {loading ? (
              <tr><td colSpan={5} className="px-4 py-6 text-slate-500">Loading…</td></tr>
            ) : visits.length === 0 ? (
              <tr><td colSpan={5} className="px-4 py-6 text-slate-500">No visits yet</td></tr>
            ) : (
              visits.map((v) => {
                const mins = durationMins(v);
                return (
                  <tr key={v.id} className="border-t border-slate-100">
                    <td className="px-4 py-3 whitespace-nowrap">{v.checkInAt.replace('T', ' ').slice(0, 19)}</td>
                    <td className="px-4 py-3">{v.user.name}</td>
                    <td className="px-4 py-3">{v.client?.name || '—'}</td>
                    <td className="px-4 py-3">
                      {mins !== null ? `${mins} min` : <span className="text-slate-400">in progress</span>}
                    </td>
                    <td className="px-4 py-3 max-w-md truncate">{v.notes || '—'}</td>
                  </tr>
                );
              })
            )}
          </tbody>
        </table>
      </div>
    </div>
  );
}
