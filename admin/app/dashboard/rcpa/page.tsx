'use client';

import { useEffect, useState } from 'react';
import { api } from '@/lib/api';
import { getToken } from '@/lib/auth';
import {
  ResponsiveContainer, BarChart, Bar, XAxis, YAxis, Tooltip, CartesianGrid, Legend,
} from 'recharts';

type Entry = {
  id: string;
  date: string;
  ourBrand: string;
  ourQuantity: number;
  competitorBrand: string;
  competitorQuantity: number;
  remarks: string | null;
  client: { id: string; name: string } | null;
  user: { id: string; name: string };
};

type Share = {
  ours: { brand: string; units: number }[];
  competitors: { brand: string; units: number }[];
};

export default function RcpaPage() {
  const [entries, setEntries] = useState<Entry[]>([]);
  const [share, setShare] = useState<Share | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    Promise.all([
      api<{ entries: Entry[] }>('/api/rcpa', { token: getToken() }),
      api<Share>('/api/rcpa/market-share', { token: getToken() }),
    ]).then(([e, s]) => {
      setEntries(e.entries);
      setShare(s);
      setLoading(false);
    });
  }, []);

  // Combine ours + competitors into a single chart for direct comparison
  const combined = share ? mergeShare(share) : [];

  return (
    <div className="space-y-6">
      <h1 className="text-2xl font-semibold">RCPA — Retail Chemist Prescription Audit</h1>

      {combined.length > 0 && (
        <section className="bg-white border border-slate-200 rounded-lg p-5">
          <h2 className="font-semibold mb-3">Brand share (units captured at chemists)</h2>
          <div style={{ width: '100%', height: 280 }}>
            <ResponsiveContainer>
              <BarChart data={combined.slice(0, 10)}>
                <CartesianGrid strokeDasharray="3 3" />
                <XAxis dataKey="brand" />
                <YAxis />
                <Tooltip />
                <Legend />
                <Bar dataKey="ours" name="Our units" fill="#0F766E" />
                <Bar dataKey="competitor" name="Competitor units" fill="#94A3B8" />
              </BarChart>
            </ResponsiveContainer>
          </div>
        </section>
      )}

      <section className="bg-white border border-slate-200 rounded-lg overflow-hidden">
        <table className="w-full text-sm">
          <thead className="bg-slate-50 text-slate-600">
            <tr>
              <th className="text-left px-4 py-3">Date</th>
              <th className="text-left px-4 py-3">MR</th>
              <th className="text-left px-4 py-3">Chemist</th>
              <th className="text-left px-4 py-3">Our brand</th>
              <th className="text-left px-4 py-3">Comp. brand</th>
              <th className="text-left px-4 py-3">Remarks</th>
            </tr>
          </thead>
          <tbody>
            {loading ? (
              <tr><td colSpan={6} className="px-4 py-6 text-slate-500">Loading…</td></tr>
            ) : entries.length === 0 ? (
              <tr><td colSpan={6} className="px-4 py-6 text-slate-500">No RCPA entries yet</td></tr>
            ) : entries.map((e) => (
              <tr key={e.id} className="border-t border-slate-100">
                <td className="px-4 py-3 whitespace-nowrap">{e.date.slice(0, 10)}</td>
                <td className="px-4 py-3">{e.user.name}</td>
                <td className="px-4 py-3">{e.client?.name ?? '—'}</td>
                <td className="px-4 py-3">
                  <div className="font-medium">{e.ourBrand}</div>
                  <div className="text-xs text-slate-500">{e.ourQuantity} units</div>
                </td>
                <td className="px-4 py-3">
                  <div className="font-medium">{e.competitorBrand}</div>
                  <div className="text-xs text-slate-500">{e.competitorQuantity} units</div>
                </td>
                <td className="px-4 py-3 max-w-md truncate">{e.remarks ?? '—'}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </section>
    </div>
  );
}

function mergeShare(s: Share): { brand: string; ours: number; competitor: number }[] {
  const map: Record<string, { ours: number; competitor: number }> = {};
  for (const o of s.ours) map[o.brand] = { ours: o.units, competitor: map[o.brand]?.competitor ?? 0 };
  for (const c of s.competitors) {
    map[c.brand] = { ours: map[c.brand]?.ours ?? 0, competitor: c.units };
  }
  return Object.entries(map)
    .map(([brand, v]) => ({ brand, ...v }))
    .sort((a, b) => (b.ours + b.competitor) - (a.ours + a.competitor));
}
