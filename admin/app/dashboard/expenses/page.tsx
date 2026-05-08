'use client';

import { useEffect, useState } from 'react';
import { api } from '@/lib/api';
import { getToken } from '@/lib/auth';

type Expense = {
  id: string;
  date: string;
  type: 'TA' | 'DA' | 'ACTUAL';
  amount: number;
  status: 'PENDING' | 'APPROVED' | 'REJECTED';
  category: string | null;
  fromLocation: string | null;
  toLocation: string | null;
  distanceKm: number | null;
  billPhoto: string | null;
  remarks: string | null;
  user: { id: string; name: string; employeeCode: string | null };
};

const API = process.env.NEXT_PUBLIC_API_URL || 'http://localhost:4000';

export default function ExpensesPage() {
  const [items, setItems] = useState<Expense[]>([]);
  const [loading, setLoading] = useState(true);
  const [filter, setFilter] = useState<'PENDING' | 'ALL'>('PENDING');

  async function reload() {
    setLoading(true);
    const r = await api<{ expenses: Expense[] }>('/api/expenses', { token: getToken() });
    setItems(r.expenses);
    setLoading(false);
  }

  useEffect(() => {
    reload();
  }, []);

  async function review(id: string, status: 'APPROVED' | 'REJECTED') {
    await api(`/api/expenses/${id}/review`, {
      method: 'PATCH',
      token: getToken(),
      body: JSON.stringify({ status }),
    });
    reload();
  }

  const visible = filter === 'ALL' ? items : items.filter((e) => e.status === 'PENDING');
  const pendingTotal = visible.reduce((s, e) => s + Number(e.amount), 0);

  return (
    <div className="space-y-5">
      <div className="flex items-center justify-between">
        <h1 className="text-2xl font-semibold">Expenses</h1>
        <div className="flex gap-2">
          {(['PENDING', 'ALL'] as const).map((f) => (
            <button
              key={f}
              onClick={() => setFilter(f)}
              className={`px-3 py-1 rounded-md text-sm ${
                filter === f ? 'bg-brand text-white' : 'bg-white border border-slate-200 text-slate-700'
              }`}
            >
              {f === 'PENDING' ? 'Pending' : 'All'}
            </button>
          ))}
        </div>
      </div>

      {visible.length > 0 && (
        <div className="bg-white border border-slate-200 rounded-lg px-4 py-3 text-sm">
          {visible.length} item(s) • total ₹{pendingTotal.toFixed(2)}
        </div>
      )}

      <div className="bg-white border border-slate-200 rounded-lg overflow-hidden">
        <table className="w-full text-sm">
          <thead className="bg-slate-50 text-slate-600">
            <tr>
              <th className="text-left px-4 py-3">Date</th>
              <th className="text-left px-4 py-3">MR</th>
              <th className="text-left px-4 py-3">Type</th>
              <th className="text-left px-4 py-3">Details</th>
              <th className="text-left px-4 py-3">Amount</th>
              <th className="text-left px-4 py-3">Bill</th>
              <th className="text-left px-4 py-3">Status</th>
              <th className="px-4 py-3" />
            </tr>
          </thead>
          <tbody>
            {loading ? (
              <tr><td colSpan={8} className="px-4 py-6 text-slate-500">Loading…</td></tr>
            ) : visible.length === 0 ? (
              <tr><td colSpan={8} className="px-4 py-6 text-slate-500">Nothing here</td></tr>
            ) : visible.map((e) => (
              <tr key={e.id} className="border-t border-slate-100">
                <td className="px-4 py-3 whitespace-nowrap">{e.date.slice(0, 10)}</td>
                <td className="px-4 py-3">{e.user.name}</td>
                <td className="px-4 py-3">
                  <span className="text-xs px-2 py-1 rounded bg-slate-100">{e.type}</span>
                </td>
                <td className="px-4 py-3 max-w-xs">
                  {e.type === 'TA' && (
                    <span className="text-slate-600 text-xs">
                      {e.fromLocation} → {e.toLocation} ({e.distanceKm ?? '—'} km)
                    </span>
                  )}
                  {e.type === 'ACTUAL' && (
                    <span className="text-slate-600 text-xs">{e.category}</span>
                  )}
                  {e.remarks && <div className="text-slate-500 text-xs italic">{e.remarks}</div>}
                </td>
                <td className="px-4 py-3 font-medium">₹{Number(e.amount).toFixed(2)}</td>
                <td className="px-4 py-3">
                  {e.billPhoto ? (
                    <a href={API + e.billPhoto} target="_blank" rel="noopener" className="text-brand text-xs hover:underline">
                      View
                    </a>
                  ) : '—'}
                </td>
                <td className="px-4 py-3">
                  <StatusBadge status={e.status} />
                </td>
                <td className="px-4 py-3 text-right whitespace-nowrap">
                  {e.status === 'PENDING' && (
                    <>
                      <button
                        onClick={() => review(e.id, 'APPROVED')}
                        className="text-xs text-brand hover:underline mr-3"
                      >Approve</button>
                      <button
                        onClick={() => review(e.id, 'REJECTED')}
                        className="text-xs text-red-600 hover:underline"
                      >Reject</button>
                    </>
                  )}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}

function StatusBadge({ status }: { status: Expense['status'] }) {
  const colors = {
    PENDING: 'bg-amber-100 text-amber-800',
    APPROVED: 'bg-green-100 text-green-700',
    REJECTED: 'bg-red-100 text-red-700',
  };
  return <span className={`text-xs px-2 py-0.5 rounded ${colors[status]}`}>{status}</span>;
}
