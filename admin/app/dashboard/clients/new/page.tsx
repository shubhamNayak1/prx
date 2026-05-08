'use client';

import { useState } from 'react';
import { useRouter } from 'next/navigation';
import Link from 'next/link';
import { api } from '@/lib/api';
import { getToken } from '@/lib/auth';

export default function NewClientPage() {
  const router = useRouter();
  const [form, setForm] = useState({
    name: '',
    type: 'DOCTOR',
    speciality: '',
    address: '',
    city: '',
    pincode: '',
    phone: '',
    email: '',
  });
  const [err, setErr] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    setErr(null);
    setSaving(true);
    try {
      const payload: any = { ...form };
      Object.keys(payload).forEach((k) => payload[k] === '' && delete payload[k]);
      await api('/api/clients', {
        method: 'POST',
        token: getToken(),
        body: JSON.stringify(payload),
      });
      router.push('/dashboard/clients');
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
        <Link href="/dashboard/clients" className="text-sm text-brand hover:underline">← Clients</Link>
        <h1 className="text-2xl font-semibold mt-1">New client</h1>
      </div>

      <form onSubmit={submit} className="bg-white border border-slate-200 rounded-lg p-6 space-y-4">
        <Row label="Name *">
          <input required {...field('name')} className="input" />
        </Row>
        <Row label="Type *">
          <select {...field('type')} className="input">
            <option value="DOCTOR">Doctor</option>
            <option value="CHEMIST">Chemist</option>
            <option value="STOCKIST">Stockist</option>
            <option value="HOSPITAL">Hospital</option>
          </select>
        </Row>
        <Row label="Speciality (for doctors)">
          <input {...field('speciality')} className="input" placeholder="Cardiology, Pediatrics, etc." />
        </Row>
        <Row label="Address">
          <input {...field('address')} className="input" />
        </Row>
        <div className="grid grid-cols-2 gap-4">
          <Row label="City"><input {...field('city')} className="input" /></Row>
          <Row label="Pincode"><input {...field('pincode')} className="input" /></Row>
        </div>
        <div className="grid grid-cols-2 gap-4">
          <Row label="Phone"><input {...field('phone')} className="input" /></Row>
          <Row label="Email"><input type="email" {...field('email')} className="input" /></Row>
        </div>

        {err && <div className="rounded-md bg-red-50 text-red-700 text-sm px-3 py-2">{err}</div>}

        <div className="flex gap-3 pt-2">
          <button type="submit" disabled={saving}
            className="rounded-md bg-brand text-white px-4 py-2 text-sm hover:bg-brand-dark disabled:opacity-50">
            {saving ? 'Saving…' : 'Create'}
          </button>
          <Link href="/dashboard/clients" className="rounded-md border border-slate-300 px-4 py-2 text-sm">Cancel</Link>
        </div>
      </form>

      <style jsx>{`
        :global(.input) {
          width: 100%; border: 1px solid rgb(203 213 225); border-radius: 0.375rem;
          padding: 0.5rem 0.75rem; font-size: 0.875rem; outline: none;
        }
        :global(.input:focus) { border-color: #0f766e; box-shadow: 0 0 0 1px #0f766e; }
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
