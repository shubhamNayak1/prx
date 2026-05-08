'use client';

import Link from 'next/link';
import { useEffect, useState } from 'react';
import { useRouter, usePathname } from 'next/navigation';
import { clearAuth, getToken, getUser, AuthUser } from '@/lib/auth';

const NAV = [
  { href: '/dashboard', label: 'Overview' },
  { href: '/dashboard/users', label: 'Users' },
  { href: '/dashboard/hierarchy', label: 'Hierarchy' },
  { href: '/dashboard/clients', label: 'Clients' },
  { href: '/dashboard/tour-plans', label: 'Tour plans' },
  { href: '/dashboard/visits', label: 'Visits' },
  { href: '/dashboard/expenses', label: 'Expenses' },
  { href: '/dashboard/expense-policies', label: 'Expense rates' },
  { href: '/dashboard/samples', label: 'Samples' },
  { href: '/dashboard/edetail', label: 'E-detailing' },
  { href: '/dashboard/rcpa', label: 'RCPA' },
];

export default function DashboardLayout({ children }: { children: React.ReactNode }) {
  const router = useRouter();
  const pathname = usePathname();
  const [user, setUser] = useState<AuthUser | null>(null);

  useEffect(() => {
    if (!getToken()) {
      router.replace('/login');
      return;
    }
    setUser(getUser());
  }, [router]);

  if (!user) return null;

  function logout() {
    clearAuth();
    router.replace('/login');
  }

  return (
    <div className="min-h-screen flex">
      <aside className="w-60 bg-white border-r border-slate-200 flex flex-col">
        <div className="px-5 py-4 border-b border-slate-200">
          <div className="text-brand font-semibold">Baseras</div>
          <div className="text-xs text-slate-500">FieldPharma Admin</div>
        </div>
        <nav className="flex-1 p-3 space-y-1 overflow-auto">
          {NAV.map((item) => {
            const active = pathname === item.href || pathname.startsWith(item.href + '/');
            return (
              <Link
                key={item.href}
                href={item.href}
                className={`block rounded-md px-3 py-2 text-sm ${
                  active ? 'bg-brand/10 text-brand font-medium' : 'text-slate-700 hover:bg-slate-100'
                }`}
              >
                {item.label}
              </Link>
            );
          })}
        </nav>
        <div className="border-t border-slate-200 p-4 text-sm">
          <div className="font-medium">{user.name}</div>
          <div className="text-xs text-slate-500">{user.role}</div>
          <button onClick={logout} className="mt-2 text-xs text-red-600 hover:underline">
            Sign out
          </button>
        </div>
      </aside>
      <main className="flex-1 p-8 overflow-auto">{children}</main>
    </div>
  );
}
