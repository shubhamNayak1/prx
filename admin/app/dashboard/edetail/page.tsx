'use client';

import { useEffect, useState } from 'react';
import { api } from '@/lib/api';
import { getToken } from '@/lib/auth';

type Slide = { id: string; order: number; title: string | null; imageUrl: string };
type Deck = { id: string; name: string; product: string | null; slides: Slide[] };

export default function EdetailPage() {
  const [decks, setDecks] = useState<Deck[]>([]);
  const [loading, setLoading] = useState(true);
  const [newDeck, setNewDeck] = useState({ name: '', product: '' });
  const [savingDeck, setSavingDeck] = useState(false);
  const [activeDeckId, setActiveDeckId] = useState<string | null>(null);
  const [newSlide, setNewSlide] = useState({ title: '', imageUrl: '', order: 1 });
  const [err, setErr] = useState<string | null>(null);

  async function reload() {
    setLoading(true);
    const r = await api<{ decks: Deck[] }>('/api/edetail/decks', { token: getToken() });
    setDecks(r.decks);
    setLoading(false);
  }

  useEffect(() => {
    reload();
  }, []);

  async function createDeck() {
    setSavingDeck(true);
    setErr(null);
    try {
      await api('/api/edetail/decks', {
        method: 'POST', token: getToken(),
        body: JSON.stringify({
          name: newDeck.name,
          product: newDeck.product || undefined,
        }),
      });
      setNewDeck({ name: '', product: '' });
      reload();
    } catch (e: any) { setErr(e.message); }
    finally { setSavingDeck(false); }
  }

  async function addSlide() {
    if (!activeDeckId || !newSlide.imageUrl) return;
    setErr(null);
    try {
      await api(`/api/edetail/decks/${activeDeckId}/slides`, {
        method: 'POST', token: getToken(),
        body: JSON.stringify({
          order: newSlide.order,
          title: newSlide.title || undefined,
          imageUrl: newSlide.imageUrl,
        }),
      });
      setNewSlide({ title: '', imageUrl: '', order: newSlide.order + 1 });
      reload();
    } catch (e: any) { setErr(e.message); }
  }

  async function deleteSlide(id: string) {
    if (!confirm('Delete this slide?')) return;
    await api(`/api/edetail/slides/${id}`, { method: 'DELETE', token: getToken() });
    reload();
  }

  return (
    <div className="space-y-8">
      <h1 className="text-2xl font-semibold">E-detailing decks</h1>

      <section>
        <h2 className="text-lg font-semibold mb-3">All decks</h2>
        {loading ? (
          <div className="text-slate-500">Loading…</div>
        ) : decks.length === 0 ? (
          <div className="bg-white border border-slate-200 rounded-lg p-6 text-slate-500 text-sm">No decks yet</div>
        ) : (
          <div className="space-y-3">
            {decks.map((d) => (
              <div key={d.id} className="bg-white border border-slate-200 rounded-lg p-5">
                <div className="flex items-center justify-between">
                  <div>
                    <div className="font-medium">{d.name}</div>
                    <div className="text-xs text-slate-500">{d.product || '—'} • {d.slides.length} slide(s)</div>
                  </div>
                  <button
                    onClick={() => setActiveDeckId(activeDeckId === d.id ? null : d.id)}
                    className="text-xs text-brand hover:underline"
                  >
                    {activeDeckId === d.id ? 'Hide slides' : 'Manage slides'}
                  </button>
                </div>
                {activeDeckId === d.id && (
                  <div className="mt-4 space-y-3">
                    <div className="grid grid-cols-2 lg:grid-cols-4 gap-3">
                      {d.slides.map((s) => (
                        <div key={s.id} className="border border-slate-200 rounded p-2">
                          <img src={s.imageUrl} alt={s.title ?? ''} className="w-full h-32 object-cover rounded mb-1" />
                          <div className="text-xs">{s.order}. {s.title || '—'}</div>
                          <button onClick={() => deleteSlide(s.id)} className="text-xs text-red-600 hover:underline">
                            Delete
                          </button>
                        </div>
                      ))}
                    </div>
                    <div className="grid grid-cols-4 gap-2 max-w-3xl">
                      <input type="number" placeholder="Order"
                        value={newSlide.order}
                        onChange={(e) => setNewSlide({ ...newSlide, order: Number(e.target.value) })}
                        className="rounded-md border border-slate-300 px-3 py-2 text-sm" />
                      <input placeholder="Title"
                        value={newSlide.title}
                        onChange={(e) => setNewSlide({ ...newSlide, title: e.target.value })}
                        className="rounded-md border border-slate-300 px-3 py-2 text-sm" />
                      <input placeholder="Image URL"
                        value={newSlide.imageUrl}
                        onChange={(e) => setNewSlide({ ...newSlide, imageUrl: e.target.value })}
                        className="col-span-2 rounded-md border border-slate-300 px-3 py-2 text-sm" />
                    </div>
                    <button
                      onClick={addSlide}
                      disabled={!newSlide.imageUrl}
                      className="rounded-md bg-brand text-white px-3 py-1.5 text-sm hover:bg-brand-dark disabled:opacity-50"
                    >
                      Add slide
                    </button>
                  </div>
                )}
              </div>
            ))}
          </div>
        )}
      </section>

      <section>
        <h2 className="text-lg font-semibold mb-3">New deck</h2>
        <div className="bg-white border border-slate-200 rounded-lg p-5 max-w-xl">
          <div className="grid grid-cols-2 gap-3">
            <input placeholder="Deck name (e.g. Cardiology Q1)"
              value={newDeck.name}
              onChange={(e) => setNewDeck({ ...newDeck, name: e.target.value })}
              className="rounded-md border border-slate-300 px-3 py-2 text-sm" />
            <input placeholder="Product (optional)"
              value={newDeck.product}
              onChange={(e) => setNewDeck({ ...newDeck, product: e.target.value })}
              className="rounded-md border border-slate-300 px-3 py-2 text-sm" />
          </div>
          {err && <div className="mt-2 text-sm text-red-600">{err}</div>}
          <button
            onClick={createDeck}
            disabled={savingDeck || !newDeck.name}
            className="mt-3 rounded-md bg-brand text-white px-4 py-2 text-sm hover:bg-brand-dark disabled:opacity-50"
          >
            {savingDeck ? 'Saving…' : 'Create deck'}
          </button>
          <p className="text-xs text-slate-500 mt-2">
            Tip: paste image URLs from your CDN/S3. For dev, you can use placeholder URLs like
            <code className="text-slate-700"> https://placehold.co/1080x1920?text=Slide+1</code>.
          </p>
        </div>
      </section>
    </div>
  );
}
