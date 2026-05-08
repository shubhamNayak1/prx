'use client';

import { useEffect, useState } from 'react';
import { api } from '@/lib/api';
import { getToken } from '@/lib/auth';
import {
  ResponsiveContainer, LineChart, Line, BarChart, Bar,
  XAxis, YAxis, Tooltip, CartesianGrid, Legend,
} from 'recharts';

type Summary = {
  counts: {
    mrTotal: number;
    todayPunchedIn: number;
    monthVisits: number;
    monthPlanned: number;
    attainmentPct: number | null;
    pendingExpensesCount: number;
    pendingExpensesAmount: number;
    monthExpensesAmount: number;
    pendingTourPlans: number;
  };
};

type TrendPoint = { date: string; count: number };
type MarketShare = {
  ours: { brand: string; units: number }[];
  competitors: { brand: string; units: number }[];
};

export default function Overview() {
  const [summary, setSummary] = useState<Summary | null>(null);
  const [trend, setTrend] = useState<TrendPoint[]>([]);
  const [share, setShare] = useState<MarketShare | null>(null);

  useEffect(() => {
    Promise.all([
      api<Summary>('/api/dashboard/summary', { token: getToken() }),
      api<{ trend: TrendPoint[] }>('/api/dashboard/visits-trend', { token: getToken() }),
      api<MarketShare>('/api/rcpa/market-share', { token: getToken() }),
    ]).then(([s, t, m]) => {
      setSummary(s);
      setTrend(t.trend);
      setShare(m);
    });
  }, []);

  if (!summary) return <div className="text-slate-500">Loading…</div>;
  const c = summary.counts;
  const attainment = c.attainmentPct ?? 0;

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-semibold">360° Overview</h1>
        <p className="text-slate-500 text-sm">All metrics scoped to your downstream hierarchy.</p>
      </div>

      <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
        <Stat label="MRs in scope" value={c.mrTotal} />
        <Stat label="Punched in today" value={`${c.todayPunchedIn}/${c.mrTotal}`} />
        <Stat
          label="Plan attainment (month)"
          value={c.attainmentPct === null ? '—' : `${c.attainmentPct}%`}
          accent={attainment >= 80 ? 'good' : attainment >= 50 ? 'warn' : 'bad'}
        />
        <Stat label="Visits this month" value={c.monthVisits} sub={`of ${c.monthPlanned} planned`} />
        <Stat
          label="Pending expenses"
          value={c.pendingExpensesCount}
          sub={`₹${c.pendingExpensesAmount.toFixed(0)}`}
        />
        <Stat label="Spend this month" value={`₹${c.monthExpensesAmount.toFixed(0)}`} />
        <Stat label="Pending tour plans" value={c.pendingTourPlans} />
      </div>

      <section className="bg-white border border-slate-200 rounded-lg p-5">
        <h2 className="font-semibold mb-3">Visits trend (last 30 days)</h2>
        <div style={{ width: '100%', height: 240 }}>
          <ResponsiveContainer>
            <LineChart data={trend}>
              <CartesianGrid strokeDasharray="3 3" />
              <XAxis dataKey="date" tickFormatter={(d) => d.slice(5)} />
              <YAxis allowDecimals={false} />
              <Tooltip />
              <Line type="monotone" dataKey="count" stroke="#0F766E" strokeWidth={2} dot={false} />
            </LineChart>
          </ResponsiveContainer>
        </div>
      </section>

      {share && (share.ours.length > 0 || share.competitors.length > 0) && (
        <section className="grid grid-cols-1 lg:grid-cols-2 gap-4">
          <ShareCard title="Our brands (RCPA)" data={share.ours} color="#0F766E" />
          <ShareCard title="Competitor brands (RCPA)" data={share.competitors} color="#94A3B8" />
        </section>
      )}
    </div>
  );
}

function Stat({
  label, value, sub, accent,
}: {
  label: string;
  value: string | number;
  sub?: string;
  accent?: 'good' | 'warn' | 'bad';
}) {
  const accentClass =
    accent === 'good' ? 'text-emerald-600'
      : accent === 'warn' ? 'text-amber-600'
      : accent === 'bad' ? 'text-red-600'
      : '';
  return (
    <div className="bg-white border border-slate-200 rounded-lg p-5">
      <div className="text-xs text-slate-500 uppercase">{label}</div>
      <div className={`text-2xl font-semibold mt-1 ${accentClass}`}>{value}</div>
      {sub && <div className="text-xs text-slate-500 mt-1">{sub}</div>}
    </div>
  );
}

function ShareCard({
  title, data, color,
}: { title: string; data: { brand: string; units: number }[]; color: string }) {
  return (
    <div className="bg-white border border-slate-200 rounded-lg p-5">
      <h2 className="font-semibold mb-3">{title}</h2>
      {data.length === 0 ? (
        <div className="text-slate-500 text-sm">No data yet</div>
      ) : (
        <div style={{ width: '100%', height: 220 }}>
          <ResponsiveContainer>
            <BarChart data={data.slice(0, 8)} layout="vertical" margin={{ left: 30 }}>
              <CartesianGrid strokeDasharray="3 3" />
              <XAxis type="number" />
              <YAxis dataKey="brand" type="category" width={80} />
              <Tooltip />
              <Bar dataKey="units" fill={color} />
            </BarChart>
          </ResponsiveContainer>
        </div>
      )}
    </div>
  );
}
