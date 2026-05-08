# Baseras FieldPharma

Pharma medical-representative (MR) field-force management platform by [Baseras Tech LLP](https://baserastech.com/).

## Modules

| Path | Purpose |
|---|---|
| `backend/` | Node.js + Express + Prisma + PostgreSQL API |
| `admin/` | Next.js admin web panel — users, hierarchy, clients, tour-plan approvals, visits |
| `android/` | Kotlin + Jetpack Compose mobile app for MRs |

## What works today

- Multi-tenant data model (Company → Users → hierarchy → MRs)
- Admin web: user CRUD, hierarchy tree, clients CRUD, tour-plan approval queue, visits/DCR list, expense approval, expense-rate policies (TA/DA per grade), sample products + stock issuance, e-detail deck manager, RCPA list with brand-share chart, **360° dashboard with attainment, spend, and trend charts**
- Android MR app: login, geo-tagged punch in/out **with selfie**, offline-first queue, periodic sync, clients (offline cached + create), tour plans view, visits with check-in / notes / check-out, expenses (TA auto-calc from grade rates / DA / ACTUAL with bill photo), samples (balance + distribute), **e-detailing (offline-cached slide decks with per-slide view-time tracking)**, **RCPA (chemist competitor audit)**
- File uploads (selfies, bills) with multer-backed `/api/uploads`
- 401 → auto sign-out

## Quick start

- **Run locally**: see [SETUP.md](SETUP.md) — Postgres + Node + Android Studio, ~10 min
- **Deploy to Neon + Render + APK**: see [DEPLOY.md](DEPLOY.md) — production-ish setup, ~30 min

```bash
# Backend
cd backend && cp .env.example .env && npm install
npx prisma migrate dev && npx prisma db seed && npm run dev

# Admin (separate terminal)
cd admin && cp .env.example .env.local && npm install && npm run dev

# Android
open -a "Android Studio" android/
```

Default admin: `admin@baseras.test` / `admin123`. MR: `mr.amit@baseras.test` / `mr123`.

## Roadmap

- [x] Phase 1 — Backend auth + Admin panel + User/hierarchy CRUD
- [x] Phase 2 — Android login + offline-first attendance + sync worker
- [x] Phase 2.1 — Selfie capture, file uploads endpoint, 401 handling
- [x] Phase 3 — Clients CRUD + tour plans + visits/DCR (offline-first)
- [x] Phase 4 — Expenses (TA/DA/actuals with bill photos) + sample/gift inventory
- [x] Phase 5 — E-detailing (offline cached decks, view-time tracking) + RCPA + 360° manager dashboards
- [ ] Production hardening — proper Room migrations, HTTPS, S3-compatible uploads, ProGuard, monitoring, encrypted DB
