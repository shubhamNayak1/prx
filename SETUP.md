# Local setup

Need: **Node.js 20+**, **PostgreSQL 14+**, **Android Studio** (Iguana or newer). About 10–15 minutes total.

## 1. Database

```bash
brew install postgresql@16
brew services start postgresql@16
createdb fieldpharma
```

## 2. Backend

```bash
cd backend
cp .env.example .env   # edit DATABASE_URL if needed
npm install
npx prisma migrate dev --name init
npx prisma db seed
npm run dev
```

Backend at **http://localhost:4000**. Verify: `curl http://localhost:4000/health`.

Static uploads (selfies, bills) are served from `/uploads/*` and persisted under `backend/uploads/`.

## 3. Admin panel

```bash
cd admin
cp .env.example .env.local
npm install
npm run dev
```

Open **http://localhost:3000**. Login as:

| Email | Password | Role |
|---|---|---|
| `admin@baseras.test` | `admin123` | Admin |
| `rsm.north@baseras.test` | `manager123` | RSM |
| `asm.delhi@baseras.test` | `manager123` | ASM |

What you can do here:
- **Overview** — 360° dashboard: today's attendance, plan attainment %, visits trend (last 30 days), pending expenses, brand share from RCPA
- **Users** — create admins/managers/MRs, assign hierarchy, deactivate
- **Clients** — manage doctors/chemists/stockists/hospitals
- **Tour plans** — review and approve/reject MR-submitted plans
- **Visits** — see all DCR submissions with duration
- **Expenses** — pending review queue + approve/reject; click "View" on a bill to see the uploaded photo
- **Expense rates** — set TA (₹/km) and DA (₹/day) per grade. MRs see their grade's rates pre-filled
- **Samples** — define products, issue stock to MRs (the MR then sees their balance + distributes)
- **E-detailing** — create decks, add slides (paste image URLs from your CDN). Decks auto-download to MR phones for offline playback
- **RCPA** — see all competitor audits with brand-share comparison chart

## 4. Android app

1. Open Android Studio → File → Open → `android/`
2. Wait for Gradle sync (~500 MB first time)
3. Run on **emulator** (Pixel 7 / API 34 recommended). The app talks to `http://10.0.2.2:4000` (emulator-loopback).
4. Login as `mr.amit@baseras.test` / `mr123`

### Try the flows

**Attendance with selfie:**
- Home → Attendance → tap "Take attendance selfie" → grant camera → snap → tap **Punch In**
- Selfie uploads to `/api/uploads`, URL is stored on the attendance record
- If you toggle airplane mode before punching, the selfie + punch are queued — it'll all sync once you re-enable network (or wait 15 min for the periodic worker)

**Visit / DCR:**
- Home → Clients → first time, app fetches from server and caches; pulls down on subsequent loads
- Tap **+** to add a new client (doctor/chemist/etc.)
- Home → Visits → "Start a visit — pick a client" → choose → check-in (geo-tagged)
- Type products discussed + notes → tap **Save**
- Tap **Check out** → visit completes, queues for sync
- Verify on admin: log in as admin → Visits → see the DCR

**Expense claim:**
- Home → Expenses → tap **+**
- Pick **Travel (TA)** → enter from/to/distance — amount auto-calculates from your grade's rate
- Or pick **Daily (DA)** → DA flat rate auto-fills
- Or pick **Actual bill** → enter category + take a bill photo with the camera + amount → submit
- Verify on admin: Expenses → pending list → Approve or Reject

**Sample distribution:**
- Admin: Samples → add a product (e.g. "Brand X 200mg") → issue 50 strips to `mr.amit`
- MR app: Home → Samples & gifts → see balance "50 strips remaining" → tap **Distribute** → enter qty (e.g. 5) → save
- Admin: Samples → see issuance row; balance auto-decrements

**E-detailing:**
- Admin: E-detailing → "New deck" with name + product → click "Manage slides" → add slides (use placeholder URLs like `https://placehold.co/1080x1920?text=Slide+1` for testing)
- MR app: Home → E-detailing → tap deck → swipe through slides
- Each slide's view duration counts up in seconds; on close, the totals are POSTed to the server (and queued if offline)
- Backend writes the totals to the active visit's `edetailViewedSlides` JSON field if a `visitId` is passed (Phase 5.1: integrate from VisitFlow)

**RCPA:**
- MR app: Home → RCPA → tap **+** → pick a chemist (filtered from cached clients) → enter our brand + qty + competitor brand + qty → save
- Admin: RCPA → see entries + comparative bar chart by brand
- Admin: Overview → top brands appear in the 360° dashboard

### Physical device

Edit `android/app/build.gradle.kts`:
```kotlin
buildConfigField("String", "API_URL", "\"http://192.168.1.42:4000\"")
```
Use your dev machine's LAN IP. Phone must be on the same Wi-Fi.

## 5. What's next — production hardening

The functional roadmap is complete. Before going live with real users:

- **Switch backend to HTTPS** and remove `usesCleartextTraffic` from Android manifest
- **Replace local uploads dir with S3** (or compatible). The endpoint shape stays the same; just swap the storage backend in [`backend/src/routes/uploads.ts`](backend/src/routes/uploads.ts)
- **Replace `fallbackToDestructiveMigration()`** with proper Room `@AutoMigration`s — current setup wipes user data on schema bump
- **Add ProGuard/R8 rules** for release builds (kotlinx.serialization keep rules already in place)
- **Encrypt the Room DB** with SQLCipher if storing sensitive client info on-device
- **Add token refresh** — currently relies on 7-day JWT + manual re-login on expiry
- **Set up Sentry / Crashlytics** for crash reporting on the Android side
- **Backend monitoring** — pg_stat, slow queries, request logging

---

## Troubleshooting

**Prisma `P1001`** → Postgres not running.

**Admin login fails** → re-run `npx prisma db seed`.

**Android: emulator can't reach backend** → backend must bind to all interfaces (Express does by default). URL is `http://10.0.2.2:4000` for emulator. For LAN devices use machine IP.

**Android: cleartext error** → manifest has `usesCleartextTraffic="true"` for dev; switch backend to HTTPS for production.

**Android: location is null** → emulator → Extended Controls → Location → set coordinate → Send.

**Android: selfie doesn't open** → camera permission was denied; `Settings → Apps → FieldPharma → Permissions → Camera`.

**Android: "Pending sync" never clears** → check the SyncWorker constraint. WorkManager runs every 15 min when network is available; you can force a one-shot run via Logcat or by killing/relaunching the app while online.

**CORS errors in admin** → `CORS_ORIGIN` in backend `.env` must equal admin URL.

**Reset everything** → `cd backend && npx prisma migrate reset` (drops data + reseeds).
