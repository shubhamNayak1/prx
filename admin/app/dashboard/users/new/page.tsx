'use client';

import { useEffect, useState } from 'react';
import { useRouter } from 'next/navigation';
import Link from 'next/link';
import { api } from '@/lib/api';
import { getToken, Role, ROLE_LABEL } from '@/lib/auth';

type Manager = { id: string; name: string; role: Role };

// Mirrors backend/src/lib/roles.ts#canReportTo so the dropdown only lists
// managers the chosen role is allowed to report to.
function parentRolesFor(role: Role): Role[] {
  switch (role) {
    case 'SENIOR_MANAGER': return ['ADMIN'];
    case 'REGIONAL_MANAGER': return ['ADMIN', 'SENIOR_MANAGER'];
    case 'FIELD_PERSON': return ['ADMIN', 'SENIOR_MANAGER', 'REGIONAL_MANAGER'];
    case 'ADMIN': return [];
  }
}

export default function NewUserPage() {
  const router = useRouter();
  const [allManagers, setAllManagers] = useState<Manager[]>([]);
  const [form, setForm] = useState({
    name: '',
    email: '',
    password: '',
    phone: '',
    role: 'FIELD_PERSON' as Role,
    grade: 'MR1',
    employeeCode: '',
    managerId: '',
  });
  const [err, setErr] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    api<{ users: Manager[] }>('/api/users', { token: getToken() }).then((r) => {
      setAllManagers(r.users.filter((u) => u.role !== 'FIELD_PERSON'));
    });
  }, []);

  const allowedParents = parentRolesFor(form.role);
  const managers = allMgrsVisible(allManagers, allowedParents);

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    setErr(null);
    setSaving(true);
    try {
      await api('/api/users', {
        method: 'POST',
        token: getToken(),
        body: JSON.stringify({
          ...form,
          managerId: form.managerId || null,
          grade: form.grade || undefined,
          employeeCode: form.employeeCode || undefined,
          phone: form.phone || undefined,
        }),
      });
      router.push('/dashboard/users');
    } catch (e: any) {
      setErr(e.message);
      setSaving(false);
    }
  }

  function field(key: keyof typeof form) {
    return {
      value: form[key],
      onChange: (e: React.ChangeEvent<HTMLInputElement | HTMLSelectElement>) =>
        setForm({ ...form, [key]: e.target.value }),
    };
  }

  return (
    <div className="max-w-2xl space-y-5">
      <div>
        <Link href="/dashboard/users" className="text-sm text-brand hover:underline">← Users</Link>
        <h1 className="text-2xl font-semibold mt-1">New user</h1>
      </div>

      <form onSubmit={submit} className="bg-white border border-slate-200 rounded-lg p-6 space-y-4">
        <Row label="Name *">
          <input required {...field('name')} className="input" />
        </Row>
        <Row label="Email *">
          <input type="email" required {...field('email')} className="input" />
        </Row>
        <Row label="Password *">
          <input type="password" required minLength={6} {...field('password')} className="input" />
        </Row>
        <Row label="Phone">
          <input {...field('phone')} className="input" />
        </Row>

        <div className="grid grid-cols-2 gap-4">
          <Row label="Role *">
            <select {...field('role')} className="input">
              <option value="FIELD_PERSON">Field Person</option>
              <option value="REGIONAL_MANAGER">Regional Manager</option>
              <option value="SENIOR_MANAGER">Senior Manager</option>
              <option value="ADMIN">Admin</option>
            </select>
          </Row>
          <Row label="Grade">
            <select {...field('grade')} className="input">
              <option value="">—</option>
              <option value="MR1">MR1</option>
              <option value="MR2">MR2</option>
              <option value="ASM">ASM</option>
              <option value="RSM">RSM</option>
              <option value="ZSM">ZSM</option>
            </select>
          </Row>
        </div>

        <Row label="Employee code">
          <input {...field('employeeCode')} className="input" />
        </Row>

        {form.role !== 'ADMIN' && (
          <Row label="Reports to *">
            <select {...field('managerId')} className="input" required>
              <option value="">— Pick a manager —</option>
              {managers.map((m) => (
                <option key={m.id} value={m.id}>
                  {m.name} ({ROLE_LABEL[m.role]})
                </option>
              ))}
            </select>
          </Row>
        )}

        {err && <div className="rounded-md bg-red-50 text-red-700 text-sm px-3 py-2">{err}</div>}

        <div className="flex gap-3 pt-2">
          <button
            type="submit"
            disabled={saving}
            className="rounded-md bg-brand text-white px-4 py-2 text-sm hover:bg-brand-dark disabled:opacity-50"
          >
            {saving ? 'Saving…' : 'Create user'}
          </button>
          <Link href="/dashboard/users" className="rounded-md border border-slate-300 px-4 py-2 text-sm">
            Cancel
          </Link>
        </div>
      </form>

      <style jsx>{`
        :global(.input) {
          width: 100%;
          border: 1px solid rgb(203 213 225);
          border-radius: 0.375rem;
          padding: 0.5rem 0.75rem;
          font-size: 0.875rem;
          outline: none;
        }
        :global(.input:focus) {
          border-color: #0f766e;
          box-shadow: 0 0 0 1px #0f766e;
        }
      `}</style>
    </div>
  );
}

function Row({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <label className="block">
      <span className="text-sm text-slate-700">{label}</span>
      <div className="mt-1">{children}</div>
    </label>
  );
}

function allMgrsVisible(all: Manager[], allowed: Role[]): Manager[] {
  return all.filter((m) => allowed.includes(m.role));
}
