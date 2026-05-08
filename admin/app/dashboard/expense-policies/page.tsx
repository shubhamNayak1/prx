'use client';

import { useEffect, useState } from 'react';
import { api } from '@/lib/api';
import { getToken } from '@/lib/auth';

type Policy = {
  id?: string;
  grade: string;
  taRatePerKm: number;
  daFlatRate: number;
};

export default function ExpensePoliciesPage() {
  const [policies, setPolicies] = useState<Policy[]>([]);
  const [loading, setLoading] = useState(true);
  const [draft, setDraft] = useState<Policy>({ grade: '', taRatePerKm: 0, daFlatRate: 0 });
  const [err, setErr] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);

  async function reload() {
    setLoading(true);
    const r = await api<{ policies: Policy[] }>('/api/expense-policies', { token: getToken() });
    setPolicies(r.policies);
    setLoading(false);
  }

  useEffect(() => {
    reload();
  }, []);

  async function save(p: Policy) {
    setSaving(true);
    setErr(null);
    try {
      await api('/api/expense-policies', {
        method: 'PUT',
        token: getToken(),
        body: JSON.stringify({
          grade: p.grade,
          taRatePerKm: Number(p.taRatePerKm),
          daFlatRate: Number(p.daFlatRate),
        }),
      });
      setDraft({ grade: '', taRatePerKm: 0, daFlatRate: 0 });
      await reload();
    } catch (e: any) {
      setErr(e.message);
    } finally {
      setSaving(false);
    }
  }

  return (
    <div className="space-y-6">
      <h1 className="text-2xl font-semibold">Expense rates by grade</h1>
      <p className="text-slate-600 text-sm">
        Travel allowance (₹/km) and daily allowance (flat ₹/day) per employee grade.
        MRs see their grade's rates pre-filled when claiming TA/DA.
      </p>

      <div className="bg-white border border-slate-200 rounded-lg overflow-hidden">
        <table className="w-full text-sm">
          <thead className="bg-slate-50 text-slate-600">
            <tr>
              <th className="text-left px-4 py-3">Grade</th>
              <th className="text-left px-4 py-3">TA (₹/km)</th>
              <th className="text-left px-4 py-3">DA (₹/day)</th>
              <th className="px-4 py-3" />
            </tr>
          </thead>
          <tbody>
            {loading ? (
              <tr><td colSpan={4} className="px-4 py-6 text-slate-500">Loading…</td></tr>
            ) : policies.length === 0 ? (
              <tr><td colSpan={4} className="px-4 py-6 text-slate-500">No policies set yet</td></tr>
            ) : policies.map((p) => (
              <PolicyRow key={p.grade} policy={p} onSave={save} />
            ))}
          </tbody>
        </table>
      </div>

      <div className="bg-white border border-slate-200 rounded-lg p-5 max-w-xl">
        <div className="text-sm font-medium mb-3">Add / update a grade</div>
        <div className="grid grid-cols-3 gap-3">
          <input
            placeholder="Grade (MR1, ASM…)"
            value={draft.grade}
            onChange={(e) => setDraft({ ...draft, grade: e.target.value })}
            className="rounded-md border border-slate-300 px-3 py-2 text-sm"
          />
          <input
            type="number"
            step="0.01"
            placeholder="₹/km"
            value={draft.taRatePerKm}
            onChange={(e) => setDraft({ ...draft, taRatePerKm: Number(e.target.value) })}
            className="rounded-md border border-slate-300 px-3 py-2 text-sm"
          />
          <input
            type="number"
            step="0.01"
            placeholder="₹/day"
            value={draft.daFlatRate}
            onChange={(e) => setDraft({ ...draft, daFlatRate: Number(e.target.value) })}
            className="rounded-md border border-slate-300 px-3 py-2 text-sm"
          />
        </div>
        {err && <div className="mt-2 text-sm text-red-600">{err}</div>}
        <button
          onClick={() => draft.grade && save(draft)}
          disabled={saving || !draft.grade}
          className="mt-3 rounded-md bg-brand text-white px-4 py-2 text-sm hover:bg-brand-dark disabled:opacity-50"
        >
          {saving ? 'Saving…' : 'Save policy'}
        </button>
      </div>
    </div>
  );
}

function PolicyRow({ policy, onSave }: { policy: Policy; onSave: (p: Policy) => void }) {
  const [ta, setTa] = useState(policy.taRatePerKm);
  const [da, setDa] = useState(policy.daFlatRate);
  const dirty = ta !== policy.taRatePerKm || da !== policy.daFlatRate;
  return (
    <tr className="border-t border-slate-100">
      <td className="px-4 py-3 font-medium">{policy.grade}</td>
      <td className="px-4 py-3">
        <input type="number" step="0.01" value={ta} onChange={(e) => setTa(Number(e.target.value))}
          className="w-24 rounded border border-slate-300 px-2 py-1 text-sm" />
      </td>
      <td className="px-4 py-3">
        <input type="number" step="0.01" value={da} onChange={(e) => setDa(Number(e.target.value))}
          className="w-24 rounded border border-slate-300 px-2 py-1 text-sm" />
      </td>
      <td className="px-4 py-3 text-right">
        <button
          onClick={() => onSave({ ...policy, taRatePerKm: ta, daFlatRate: da })}
          disabled={!dirty}
          className="text-xs text-brand hover:underline disabled:text-slate-400"
        >Save</button>
      </td>
    </tr>
  );
}
