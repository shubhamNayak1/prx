'use client';

import { useEffect, useState } from 'react';
import Link from 'next/link';
import { api } from '@/lib/api';
import { getToken } from '@/lib/auth';

type Client = {
  id: string;
  name: string;
  type: 'DOCTOR' | 'CHEMIST' | 'STOCKIST' | 'HOSPITAL';
  speciality: string | null;
  city: string | null;
  phone: string | null;
};

export default function ClientsPage() {
  const [items, setItems] = useState<Client[]>([]);
  const [loading, setLoading] = useState(true);
  const [filter, setFilter] = useState<'ALL' | Client['type']>('ALL');
  const [q, setQ] = useState('');

  async function reload() {
    setLoading(true);
    const params = new URLSearchParams();
    if (q) params.set('q', q);
    if (filter !== 'ALL') params.set('type', filter);
    const url = '/api/clients' + (params.toString() ? `?${params}` : '');
    const r = await api<{ clients: Client[] }>(url, { token: getToken() });
    setItems(r.clients);
    setLoading(false);
  }

  useEffect(() => {
    reload();
  }, [filter]);

  return (
    <div className="space-y-5">
      <div className="flex items-center justify-between">
        <h1 className="text-2xl font-semibold">Clients</h1>
        <Link
          href="/dashboard/clients/new"
          className="rounded-md bg-brand px-4 py-2 text-sm text-white hover:bg-brand-dark"
        >
          + New client
        </Link>
      </div>

      <div className="flex gap-2 items-center">
        <input
          value={q}
          onChange={(e) => setQ(e.target.value)}
          onKeyDown={(e) => e.key === 'Enter' && reload()}
          placeholder="Search name…"
          className="rounded-md border border-slate-300 px-3 py-2 text-sm w-72"
        />
        {(['ALL', 'DOCTOR', 'CHEMIST', 'STOCKIST', 'HOSPITAL'] as const).map((f) => (
          <button
            key={f}
            onClick={() => setFilter(f)}
            className={`px-3 py-1 rounded-md text-sm ${
              filter === f ? 'bg-brand text-white' : 'bg-white border border-slate-200 text-slate-700'
            }`}
          >
            {f}
          </button>
        ))}
      </div>

      <div className="bg-white border border-slate-200 rounded-lg overflow-hidden">
        <table className="w-full text-sm">
          <thead className="bg-slate-50 text-slate-600">
            <tr>
              <th className="text-left px-4 py-3">Name</th>
              <th className="text-left px-4 py-3">Type</th>
              <th className="text-left px-4 py-3">Speciality</th>
              <th className="text-left px-4 py-3">City</th>
              <th className="text-left px-4 py-3">Phone</th>
            </tr>
          </thead>
          <tbody>
            {loading ? (
              <tr><td colSpan={5} className="px-4 py-6 text-slate-500">Loading…</td></tr>
            ) : items.length === 0 ? (
              <tr><td colSpan={5} className="px-4 py-6 text-slate-500">No clients</td></tr>
            ) : (
              items.map((c) => (
                <tr key={c.id} className="border-t border-slate-100">
                  <td className="px-4 py-3 font-medium">{c.name}</td>
                  <td className="px-4 py-3">
                    <span className="text-xs px-2 py-1 rounded bg-slate-100">{c.type}</span>
                  </td>
                  <td className="px-4 py-3">{c.speciality || '—'}</td>
                  <td className="px-4 py-3">{c.city || '—'}</td>
                  <td className="px-4 py-3">{c.phone || '—'}</td>
                </tr>
              ))
            )}
          </tbody>
        </table>
      </div>
    </div>
  );
}
