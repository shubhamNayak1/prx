'use client';

import { useEffect, useState } from 'react';
import { api } from '@/lib/api';
import { getToken } from '@/lib/auth';

type Product = { id: string; name: string; unitType: string; isGift: boolean };
type User = { id: string; name: string; role: string };
type Issue = {
  id: string;
  quantity: number;
  issuedAt: string;
  product: Product;
  user: { id: string; name: string };
};

export default function SamplesPage() {
  const [products, setProducts] = useState<Product[]>([]);
  const [issues, setIssues] = useState<Issue[]>([]);
  const [users, setUsers] = useState<User[]>([]);
  const [loading, setLoading] = useState(true);

  // New product form
  const [newProd, setNewProd] = useState({ name: '', unitType: 'strip', isGift: false });
  const [savingProd, setSavingProd] = useState(false);

  // New issue form
  const [issue, setIssue] = useState({ productId: '', userId: '', quantity: 0 });
  const [savingIssue, setSavingIssue] = useState(false);
  const [err, setErr] = useState<string | null>(null);

  async function reload() {
    setLoading(true);
    const [p, i, u] = await Promise.all([
      api<{ products: Product[] }>('/api/samples/products', { token: getToken() }),
      api<{ issues: Issue[] }>('/api/samples/issues', { token: getToken() }),
      api<{ users: User[] }>('/api/users', { token: getToken() }),
    ]);
    setProducts(p.products);
    setIssues(i.issues);
    setUsers(u.users.filter((x) => x.role === 'MR'));
    setLoading(false);
  }

  useEffect(() => {
    reload();
  }, []);

  async function createProduct() {
    setSavingProd(true);
    setErr(null);
    try {
      await api('/api/samples/products', {
        method: 'POST', token: getToken(), body: JSON.stringify(newProd),
      });
      setNewProd({ name: '', unitType: 'strip', isGift: false });
      reload();
    } catch (e: any) {
      setErr(e.message);
    } finally {
      setSavingProd(false);
    }
  }

  async function createIssue() {
    setSavingIssue(true);
    setErr(null);
    try {
      await api('/api/samples/issues', {
        method: 'POST', token: getToken(), body: JSON.stringify({
          ...issue,
          quantity: Number(issue.quantity),
        }),
      });
      setIssue({ productId: '', userId: '', quantity: 0 });
      reload();
    } catch (e: any) {
      setErr(e.message);
    } finally {
      setSavingIssue(false);
    }
  }

  return (
    <div className="space-y-8">
      <h1 className="text-2xl font-semibold">Samples & gifts</h1>

      <section>
        <h2 className="text-lg font-semibold mb-3">Products</h2>
        <div className="bg-white border border-slate-200 rounded-lg overflow-hidden mb-3">
          <table className="w-full text-sm">
            <thead className="bg-slate-50 text-slate-600">
              <tr>
                <th className="text-left px-4 py-3">Name</th>
                <th className="text-left px-4 py-3">Unit</th>
                <th className="text-left px-4 py-3">Type</th>
              </tr>
            </thead>
            <tbody>
              {loading ? (
                <tr><td colSpan={3} className="px-4 py-6 text-slate-500">Loading…</td></tr>
              ) : products.length === 0 ? (
                <tr><td colSpan={3} className="px-4 py-6 text-slate-500">No products yet</td></tr>
              ) : products.map((p) => (
                <tr key={p.id} className="border-t border-slate-100">
                  <td className="px-4 py-3 font-medium">{p.name}</td>
                  <td className="px-4 py-3">{p.unitType}</td>
                  <td className="px-4 py-3">{p.isGift ? 'Gift' : 'Sample'}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
        <div className="bg-white border border-slate-200 rounded-lg p-4 max-w-xl">
          <div className="text-sm font-medium mb-2">Add product</div>
          <div className="grid grid-cols-3 gap-2">
            <input placeholder="Name" value={newProd.name}
              onChange={(e) => setNewProd({ ...newProd, name: e.target.value })}
              className="rounded-md border border-slate-300 px-3 py-2 text-sm" />
            <select value={newProd.unitType}
              onChange={(e) => setNewProd({ ...newProd, unitType: e.target.value })}
              className="rounded-md border border-slate-300 px-3 py-2 text-sm">
              <option value="strip">strip</option>
              <option value="bottle">bottle</option>
              <option value="packet">packet</option>
              <option value="piece">piece</option>
            </select>
            <label className="flex items-center text-sm">
              <input type="checkbox" checked={newProd.isGift}
                onChange={(e) => setNewProd({ ...newProd, isGift: e.target.checked })}
                className="mr-2" />
              Is a gift
            </label>
          </div>
          <button onClick={createProduct} disabled={savingProd || !newProd.name}
            className="mt-3 rounded-md bg-brand text-white px-4 py-2 text-sm hover:bg-brand-dark disabled:opacity-50">
            {savingProd ? 'Saving…' : 'Add'}
          </button>
        </div>
      </section>

      <section>
        <h2 className="text-lg font-semibold mb-3">Issue stock to MR</h2>
        <div className="bg-white border border-slate-200 rounded-lg p-4 max-w-2xl mb-4">
          <div className="grid grid-cols-3 gap-2">
            <select value={issue.productId}
              onChange={(e) => setIssue({ ...issue, productId: e.target.value })}
              className="rounded-md border border-slate-300 px-3 py-2 text-sm">
              <option value="">Pick product</option>
              {products.map((p) => <option key={p.id} value={p.id}>{p.name}</option>)}
            </select>
            <select value={issue.userId}
              onChange={(e) => setIssue({ ...issue, userId: e.target.value })}
              className="rounded-md border border-slate-300 px-3 py-2 text-sm">
              <option value="">Pick MR</option>
              {users.map((u) => <option key={u.id} value={u.id}>{u.name}</option>)}
            </select>
            <input type="number" placeholder="Quantity" value={issue.quantity || ''}
              onChange={(e) => setIssue({ ...issue, quantity: Number(e.target.value) })}
              className="rounded-md border border-slate-300 px-3 py-2 text-sm" />
          </div>
          {err && <div className="mt-2 text-sm text-red-600">{err}</div>}
          <button onClick={createIssue}
            disabled={savingIssue || !issue.productId || !issue.userId || issue.quantity <= 0}
            className="mt-3 rounded-md bg-brand text-white px-4 py-2 text-sm hover:bg-brand-dark disabled:opacity-50">
            {savingIssue ? 'Saving…' : 'Issue stock'}
          </button>
        </div>

        <div className="bg-white border border-slate-200 rounded-lg overflow-hidden">
          <table className="w-full text-sm">
            <thead className="bg-slate-50 text-slate-600">
              <tr>
                <th className="text-left px-4 py-3">Date</th>
                <th className="text-left px-4 py-3">MR</th>
                <th className="text-left px-4 py-3">Product</th>
                <th className="text-left px-4 py-3">Quantity</th>
              </tr>
            </thead>
            <tbody>
              {issues.length === 0 ? (
                <tr><td colSpan={4} className="px-4 py-6 text-slate-500">No issuances yet</td></tr>
              ) : issues.map((i) => (
                <tr key={i.id} className="border-t border-slate-100">
                  <td className="px-4 py-3">{i.issuedAt.slice(0, 10)}</td>
                  <td className="px-4 py-3">{i.user.name}</td>
                  <td className="px-4 py-3">{i.product.name}</td>
                  <td className="px-4 py-3">{i.quantity} {i.product.unitType}(s)</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </section>
    </div>
  );
}
