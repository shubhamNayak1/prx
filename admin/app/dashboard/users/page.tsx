'use client';

import { useEffect, useState } from 'react';
import Link from 'next/link';
import { api } from '@/lib/api';
import { getToken } from '@/lib/auth';

type User = {
  id: string;
  name: string;
  email: string;
  role: 'ADMIN' | 'MANAGER' | 'MR';
  grade: string | null;
  employeeCode: string | null;
  isActive: boolean;
  manager: { id: string; name: string } | null;
};

export default function UsersPage() {
  const [users, setUsers] = useState<User[]>([]);
  const [loading, setLoading] = useState(true);
  const [filter, setFilter] = useState<'ALL' | 'ADMIN' | 'MANAGER' | 'MR'>('ALL');

  async function reload() {
    setLoading(true);
    const r = await api<{ users: User[] }>('/api/users', { token: getToken() });
    setUsers(r.users);
    setLoading(false);
  }

  useEffect(() => {
    reload();
  }, []);

  async function deactivate(id: string) {
    if (!confirm('Deactivate this user?')) return;
    await api(`/api/users/${id}`, { method: 'DELETE', token: getToken() });
    reload();
  }

  const filtered = filter === 'ALL' ? users : users.filter((u) => u.role === filter);

  return (
    <div className="space-y-5">
      <div className="flex items-center justify-between">
        <h1 className="text-2xl font-semibold">Users</h1>
        <Link
          href="/dashboard/users/new"
          className="rounded-md bg-brand px-4 py-2 text-sm text-white hover:bg-brand-dark"
        >
          + New user
        </Link>
      </div>

      <div className="flex gap-2">
        {(['ALL', 'ADMIN', 'MANAGER', 'MR'] as const).map((f) => (
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
              <th className="text-left px-4 py-3">Email</th>
              <th className="text-left px-4 py-3">Role</th>
              <th className="text-left px-4 py-3">Grade</th>
              <th className="text-left px-4 py-3">Manager</th>
              <th className="text-left px-4 py-3">Status</th>
              <th className="px-4 py-3" />
            </tr>
          </thead>
          <tbody>
            {loading ? (
              <tr><td colSpan={7} className="px-4 py-6 text-slate-500">Loading…</td></tr>
            ) : filtered.length === 0 ? (
              <tr><td colSpan={7} className="px-4 py-6 text-slate-500">No users</td></tr>
            ) : (
              filtered.map((u) => (
                <tr key={u.id} className="border-t border-slate-100">
                  <td className="px-4 py-3">
                    <div className="font-medium">{u.name}</div>
                    <div className="text-xs text-slate-500">{u.employeeCode || '—'}</div>
                  </td>
                  <td className="px-4 py-3 text-slate-600">{u.email}</td>
                  <td className="px-4 py-3">
                    <span className="text-xs px-2 py-1 rounded bg-slate-100">{u.role}</span>
                  </td>
                  <td className="px-4 py-3">{u.grade || '—'}</td>
                  <td className="px-4 py-3">{u.manager?.name || '—'}</td>
                  <td className="px-4 py-3">
                    <span className={`text-xs px-2 py-1 rounded ${u.isActive ? 'bg-green-100 text-green-700' : 'bg-slate-100 text-slate-500'}`}>
                      {u.isActive ? 'Active' : 'Inactive'}
                    </span>
                  </td>
                  <td className="px-4 py-3 text-right">
                    {u.isActive && (
                      <button onClick={() => deactivate(u.id)} className="text-xs text-red-600 hover:underline">
                        Deactivate
                      </button>
                    )}
                  </td>
                </tr>
              ))
            )}
          </tbody>
        </table>
      </div>
    </div>
  );
}
