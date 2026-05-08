# Baseras FieldPharma — Android

MR mobile app — **Kotlin + Jetpack Compose + Room + WorkManager + Retrofit**.

## What works

| Feature | Status |
|---|---|
| Login (MR-only) | ✅ |
| Encrypted token storage | ✅ |
| 401 → auto sign-out | ✅ |
| Geo-tagged punch in / out | ✅ |
| Selfie capture during punch-in | ✅ Phase 2.1 |
| Selfie offline upload (queued) | ✅ |
| Clients list — offline-cached, search, create | ✅ Phase 3 |
| Tour plans (read-only view) | ✅ Phase 3 |
| Visit flow — check-in / notes / check-out / DCR | ✅ Phase 3 |
| Expense claim (TA auto-calc, DA, ACTUAL with bill photo) | ✅ Phase 4 |
| Samples balance + distribution | ✅ Phase 4 |
| E-detailing — offline deck cache + slide viewer with view-time tracking | ✅ Phase 5 |
| RCPA — competitor audit at chemists | ✅ Phase 5 |
| WorkManager periodic sync (15 min) | ✅ |
| Pending-sync indicator | ✅ |
| Tour plan creation from app | ❌ Phase 3.1 |
| E-detailing tied to active visit (vs standalone) | ❌ Phase 5.1 |

## Architecture

```
com.baseras.fieldpharma/
├── FieldPharmaApp.kt        Application — manual DI
├── MainActivity.kt
├── auth/                    EncryptedSharedPreferences + auth event flow
├── camera/                  PhotoCapture (FileProvider + TakePicture, segregated by subdir)
├── data/
│   ├── remote/              Retrofit Api + ApiClient + UploadHelper
│   ├── local/               Room v3 — attendance, pending_sync, client_cache, visits, edetail_decks, edetail_slides
│   └── repo/                Auth, Attendance, Client, TourPlan, Visit, Expense, Sample, Edetail, Rcpa
├── location/                FusedLocationProvider wrapper
├── sync/                    SyncWorker — drains all queues (attendance / visits / expenses / samples / e-detail / RCPA)
├── nav/AppNav.kt            Compose nav graph + session-expired handling
└── ui/
    ├── theme/               Material 3 brand theme
    ├── login/               LoginScreen
    ├── home/                HomeScreen with feature tiles
    ├── attendance/          Punch-in/out with optional selfie
    ├── clients/             List + search + create
    ├── tour/                Upcoming plans (read-only)
    ├── visit/               PickClient → VisitFlow (check-in/notes/check-out)
    ├── expense/             Expenses list + new (TA/DA/ACTUAL with bill photo)
    ├── sample/              Stock balance + distribute dialog
    ├── edetail/             Decks list + HorizontalPager slide viewer with per-slide timer
    └── rcpa/                RCPA list + new-entry form (chemist picker)
```

## Offline-first contract

**Attendance:**
- Punch in saves locally with `punchInPhotoPath` (if selfie taken)
- Tries upload → punch immediately
- On failure: queues in `pending_sync` with `localPhotoPath`
- `SyncWorker` uploads photo first, then submits punch with returned URL

**Visits:**
- Check-in creates a `VisitEntity` with state=`IN_PROGRESS`
- Notes save locally on each tap (no network needed)
- Check-out marks state=`COMPLETED`, tries to POST
- On failure: state stays `COMPLETED`, sync worker retries until `SYNCED` (with serverId)

**Clients:**
- All clients pulled to Room cache on each refresh
- List/search read from cache (always-instant UI)
- Create requires network (no offline-create yet)

**Expenses:**
- TA pre-fills the per-km rate from `/api/expense-policies/me` based on user's grade
- Submit goes through foreground first; on failure queues `EXPENSE` in `pending_sync` with `localPhotoPath` for the bill
- SyncWorker uploads photo first, then submits

**Samples:**
- Balance fetched from server on screen load
- Distribute is foreground-attempt with `SAMPLE_DIST` queue fallback

**E-detailing:**
- Decks + slides cached in Room v3; slide images cached by Coil's disk cache (default 250MB)
- Sync worker calls `refresh()` on each run to keep the local copy current
- View tracking: `LaunchedEffect` ticks 1Hz on the currently visible page; on close, totals POST to `/api/edetail/views` (queued as `EDETAIL_VIEW` if offline)

**RCPA:**
- Form filtered to chemists from the cached client list
- Submit attempts foreground; queues as `RCPA` on failure

## Versions

- AGP 8.7.2, Kotlin 2.0.21, Compose BOM 2024.10
- minSdk 26, targetSdk 35

## Notes for production

- Set `usesCleartextTraffic="false"` in manifest, switch backend to HTTPS
- Replace `fallbackToDestructiveMigration()` with proper Room migrations once you have real users
- Replace local file storage with S3 (or compatible) — see `backend/src/routes/uploads.ts`
- Add ProGuard/R8 keep rules for kotlinx.serialization (already in `proguard-rules.pro`)
- Consider SQLCipher for encrypted Room DB (currently plain SQLite)
