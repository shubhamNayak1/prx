# Deploy: Neon (DB) + Render (backend + crons) + sideloaded APK

Total time: **~45 minutes** on first deploy. Updates after that are a `git push`.

**Architecture target**: Android app (field + admin + manager) + cloud backend. The web portal under `admin/` is kept for marketing, public signup, and billing only — all operational work is in Android.

## What you'll end up with

- **Postgres** on Neon (free tier — 0.5 GB)
- **Backend API** at `https://fieldpharma-api.onrender.com` (free tier)
- **Two cron jobs** on Render (trial-lock, rebill)
- **APK file** you can download and install on any Android phone
- (Optional) **Marketing + billing site** on Vercel (`admin/`)
- (Optional) **WhatsApp OTP** via MSG91 + **Payments** via Razorpay

> **Heads up — Render free tier caveats**
> - Spins down after 15 min of inactivity. First request after sleep takes ~30s
> - Filesystem is ephemeral. **Selfie/bill photos uploaded to `/uploads/*` are wiped on each redeploy.** For real production use, swap [`backend/src/routes/uploads.ts`](backend/src/routes/uploads.ts) over to S3 (~30 LOC change) or pay for Render persistent disk

---

## Step 1 — Push code to GitHub

If the repo isn't on GitHub yet:

```bash
cd /Users/myown/Desktop/client/prx
git init
git add .
git commit -m "Baseras FieldPharma — initial scaffold"

# Create the repo on github.com (private is fine), then:
git remote add origin git@github.com:YOUR-USER/fieldpharma.git
git branch -M main
git push -u origin main
```

---

## Step 2 — Create Neon database

1. Go to **https://console.neon.tech**, sign up
2. **New Project** → name it `fieldpharma`, region close to where you'll run Render (e.g. `aws-us-east-1`)
3. After creation, the dashboard shows a **connection string** — copy it. It looks like:
   ```
   postgresql://neondb_owner:NB_xxxxx@ep-cool-cloud-12345.us-east-1.aws.neon.tech/neondb?sslmode=require
   ```
4. Save it somewhere — you'll paste it into Render in a moment

---

## Step 3 — Deploy backend on Render

1. Go to **https://dashboard.render.com**, sign in with GitHub
2. **New +** → **Blueprint**
3. Pick the `fieldpharma` repo. Render reads [`render.yaml`](render.yaml) and shows:
   - One web service: `fieldpharma-api`
   - Two cron jobs: `fieldpharma-trial-lock`, `fieldpharma-rebill`
4. Click **Apply**. Render asks for the `sync: false` env vars:
   - **`DATABASE_URL`** — paste the Neon URL (required)
   - **`APP_URL`** — your admin/marketing host once deployed (e.g. `https://fieldpharma-admin.vercel.app`). Can be left blank at first.
   - **Razorpay / MSG91 keys** — leave blank for now; the backend has dev stubs for both (payments return a stub URL, OTPs print to the server log).
5. `JWT_SECRET` and `CRON_KEY` are auto-generated. `CORS_ORIGIN` stays `*` until the admin host is known.
6. Click **Create New Resources**

The first build takes ~3-5 min:
- `npm install` pulls all deps
- `prisma generate` creates the client
- `npm run build` runs tsc
- `prisma migrate deploy` applies migrations to Neon

When it's green: get the URL from the top of the service page (e.g. `https://fieldpharma-api.onrender.com`).

The crons run on Render's own schedule (06:00 and 06:15 IST daily). You don't need to trigger them; they POST to `/api/billing/cron/*` with the shared `CRON_KEY` header automatically.

Smoke test:
```bash
curl https://fieldpharma-api.onrender.com/health
# {"ok":true,"service":"fieldpharma-api"}
```

---

## Step 4 — Seed the production DB

Migrations created the empty tables. To get the demo admin user + sample data, run the seed once.

**Easiest — from your machine, against the Neon DB:**

```bash
cd /Users/myown/Desktop/client/prx/backend
DATABASE_URL='paste-your-neon-url-here' npx prisma db seed
```

(`prisma db seed` runs `prisma/seed.ts` defined in `package.json`.)

Verify by logging in to admin (you can run admin locally pointing at the deployed backend, see optional step below — or just hit the API directly):

```bash
curl -X POST https://fieldpharma-api.onrender.com/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"admin@baseras.test","password":"admin123"}'
```

You should get back `{token: "...", user: {...}}`.

---

## Step 5 — Build the APK

1. **Point the release build at your Render URL.** Two options:

   **a. Edit `android/local.properties`** (easiest, gitignored, works for Android Studio and CLI):
   ```properties
   fieldpharma.release.apiUrl=https://your-service.onrender.com
   fieldpharma.debug.apiUrl=http://192.168.1.42:4000   # optional: your LAN IP for device testing
   ```

   **b. Export env vars** (handy for CI):
   ```bash
   export FIELDPHARMA_RELEASE_API_URL=https://your-service.onrender.com
   ```

   If you set neither, the release build uses the default `https://fieldpharma-api.onrender.com` — fine if that's your actual service name.

2. **Make sure debug keystore exists.** This is what signs the demo APK. Most Android Studio installs already have it at `~/.android/debug.keystore`. To check:
   ```bash
   ls ~/.android/debug.keystore && echo "OK"
   ```
   If it's missing, run any debug build once in Android Studio — it auto-creates the keystore.

3. **Build the release APK** — pick whichever feels easier:

   **From Android Studio:** Menu → **Build → Generate Signed App Bundle / APK** → APK → use the existing "demo" signing config the project defines → `release` build variant → Finish.

   *Or* simpler: **Build → Build Bundle(s) / APK(s) → Build APK(s)**, but first switch the build variant in **Build → Select Build Variant** to `release`.

   **From CLI:**
   ```bash
   cd /Users/myown/Desktop/client/prx/android
   ./gradlew assembleRelease
   ```

4. The APK lands at:
   ```
   android/app/build/outputs/apk/release/app-release.apk
   ```
   (~15-25 MB)

5. **Get it onto a phone**, any of:
   - Upload to Google Drive → open the link on the phone → download → tap to install
   - Connect phone via USB with developer mode + USB debugging on, then `adb install app-release.apk`
   - Email the APK to yourself, open on phone

6. **First install on the phone:** Android will ask "allow install from this source?" — say yes for the app you opened it from (Drive, Gmail, Files…). Then the install prompt appears.

7. **Open the app** → login with `mr.amit@baseras.test` / `mr123`. The app talks to your Render backend over HTTPS.

> If the first launch hangs ~30s before showing an error: that's Render's free tier waking up from cold-start sleep. Quit and reopen — second attempt is fast.

---

## Optional — Deploy the marketing / billing site to Vercel

Keep this because: public signup, payment pages (Razorpay bounces users back to a URL), and invoice downloads need a web surface. The Android app is the operational tool, but prospects reach you via a website.

1. **https://vercel.com** → sign in with GitHub → **Add New → Project** → import `fieldpharma`
2. **Root Directory:** click "Edit" → set to `admin`
3. **Environment Variables:** add `NEXT_PUBLIC_API_URL` = `https://fieldpharma-api.onrender.com`
4. **Deploy**

You get a URL like `https://fieldpharma-admin.vercel.app`.

After this, in the Render dashboard:
- Change `CORS_ORIGIN` from `*` to your Vercel URL — Android doesn't send Origin headers so this doesn't affect it.
- Set `APP_URL` to the Vercel URL — Razorpay payment-link callbacks will route back here.

---

## Optional — Enable WhatsApp OTP (MSG91)

Without this, OTPs print to the Render log (visible in **Logs** tab) during dev. For real users:

1. Sign up at **https://control.msg91.com**, verify your business.
2. **WhatsApp** → **Setup WhatsApp Business API** → connect a phone number. Takes ~24h for Facebook/Meta approval.
3. **WhatsApp** → **Templates** → create a template:
   - Name: `fieldpharma_otp`
   - Language: English
   - Category: Authentication
   - Body: `Your FieldPharma verification code is {{1}}. Valid for 10 minutes. Do not share.`
   - Submit for approval (~1h usually).
4. Once approved, set these env vars on Render:
   ```
   MSG91_WA_AUTH_KEY=<account auth key>
   MSG91_WA_INTEGRATED_NUMBER=919876543210    # digits only, your approved number
   MSG91_WA_TEMPLATE_NAME=fieldpharma_otp
   MSG91_WA_LANGUAGE_CODE=en
   ```
5. (Optional) Set up SMS fallback for users without WhatsApp:
   ```
   MSG91_AUTH_KEY=...
   MSG91_SENDER_ID=FLDPHM          # 6-char DLT-approved
   MSG91_OTP_TEMPLATE_ID=...       # DLT template with ##OTP## variable
   ```

Redeploy picks these up automatically.

---

## Optional — Enable Razorpay payments

Without this, the admin billing page's "Pay" button opens a stub URL. For real payments:

1. Sign up at **https://dashboard.razorpay.com**, verify KYC.
2. **Settings → API Keys** → generate Live keys (or Test keys first — recommended).
3. **Settings → Webhooks** → add a webhook:
   - URL: `https://fieldpharma-api.onrender.com/api/billing/webhook`
   - Secret: any long random string (save this)
   - Active events: `payment_link.paid`, `payment.captured`
4. Set env vars on Render:
   ```
   RAZORPAY_KEY_ID=rzp_live_xxx
   RAZORPAY_KEY_SECRET=xxx
   RAZORPAY_WEBHOOK_SECRET=xxx     # the webhook secret from step 3
   ```
5. Redeploy. From the admin page, click Upgrade → you get a real Razorpay payment link.

---

## Migration plan — moving admin/manager to Android

The web portal (`admin/`) today has 11 operational screens (users, clients, tour-plans, visits, expenses, samples, rcpa, edetail, hierarchy, expense-policies, dashboard overview). To go Android-only, these need Android equivalents:

| Priority | Admin screen | Android target | Notes |
|---|---|---|---|
| P0 | Dashboard overview | New `AdminHomeScreen` | Shows company KPIs instead of rep tiles when `role === ADMIN` |
| P0 | Users list + new | New `UsersScreen` + `NewUserScreen` | OTP-based invite to new MR phone number |
| P0 | Tour-plan approvals | New `ApprovalsScreen` with Tour-plan tab | Approve/reject with reason |
| P0 | Clients list + new-client approvals | Mirror existing `ClientsScreen` + add approval queue | Needs `Client.status` schema change (noted earlier) |
| P0 | Billing | New `BillingScreen` | Status + Upgrade button → Razorpay in Chrome Custom Tab |
| P1 | Expense approvals | `ApprovalsScreen` Expenses tab | |
| P1 | Hierarchy | New `HierarchyScreen` | Read-only tree; invite flow adds nodes |
| P1 | Expense policies (TA/DA rates) | New `RatesScreen` | |
| P2 | Samples issuance management | Admin tab in existing `SamplesScreen` | |
| P2 | E-detailing deck upload | New `DecksScreen` admin mode | Share-sheet from Files app for PDF/images |
| P2 | RCPA aggregate view | New `RcpaReportsScreen` | |

What stays on web:
- Public landing page (`/`), sign-up form (`/login` → Create account)
- Billing/invoice downloads (so a CFO can grab a PDF without the Android app)
- Terms, Privacy, docs

Sequencing: do all P0 before dropping any web screens. P1 and P2 can follow once mobile adoption is confirmed.

---

## Updating after a code change

```bash
git add .
git commit -m "describe the change"
git push
```

- **Render** auto-rebuilds backend on every push to `main` (~2-3 min)
- **Vercel** auto-rebuilds admin on every push (~1-2 min)
- **APK** does NOT auto-rebuild — re-run Step 5 and redistribute

---

## Common issues

| Symptom | Fix |
|---|---|
| Render build: `Cannot find module '@prisma/client'` | The `postinstall` script must have run. Check Render build log for `prisma generate` output. If missing, ensure `package.json` has `"postinstall": "prisma generate"` |
| Render build: `tsc: command not found` | `npm install --include=dev` flag missing in build command — check `render.yaml` |
| Render runtime: `P1001 Can't reach database server` | `DATABASE_URL` env var not set, or Neon project paused (free tier auto-pauses; Neon resumes on first connect — first request may be slow) |
| `npx prisma db seed` errors with `Cannot find module 'tsx'` | Run `npm install` in `backend/` first |
| APK build: `keystore not found` | Run a debug build once (e.g. `./gradlew assembleDebug`) to auto-create `~/.android/debug.keystore` |
| APK installs but login fails with network error | API_URL not updated in `build.gradle.kts`, or backend is sleeping. Check `https://your-app.onrender.com/health` in a browser |
| App says "Cleartext HTTP not permitted" | Your Render URL must use `https://`, not `http://` — check the build.gradle.kts line |
| Admin can't login (CORS) | `CORS_ORIGIN` on Render must include your admin host. Set to `*` for testing |
| Signup → "Could not send OTP" | WhatsApp template not approved yet. OTP still prints to Render Logs → use that code to continue. Or set the SMS fallback env vars. |
| Razorpay webhook 401 (bad signature) | `RAZORPAY_WEBHOOK_SECRET` on Render doesn't match the secret entered in Razorpay dashboard → Webhooks |
| Billing page says "SUBSCRIPTION_LOCKED" after trial | Expected — trial-lock cron fired. In Render → Logs for `fieldpharma-trial-lock` to confirm. Pay via Razorpay (or set subscription.status back to ACTIVE in Neon SQL for dev). |
| Cron shows red "No activity" after deploy | Render crons only fire on their schedule, not immediately. For a smoke test, hit the endpoint manually: `curl -X POST -H "x-cron-key: $CRON_KEY" https://your-service.onrender.com/api/billing/cron/trial-lock` |

---

## Production hardening checklist (when you're ready)

These are explicit shortcuts taken for the demo deploy. Address before real customers use it:

- [ ] Switch Render uploads from local FS to S3-compatible (selfies/bills currently lost on redeploy)
- [ ] Generate a real Android signing keystore + use Play App Signing (current APK uses the universal debug keystore — fine for sideload, not Play Store)
- [ ] Replace `fallbackToDestructiveMigration()` in `AppDatabase.kt` with proper Room migrations (currently wipes user data on schema bump)
- [ ] Set up token refresh (currently 7-day JWT + manual re-login)
- [ ] Lock `CORS_ORIGIN` to specific admin host
- [ ] Encrypt Room DB with SQLCipher (currently plain SQLite)
- [ ] Add Sentry / Crashlytics for crash reports
- [ ] Add Render persistent disk or move uploads to S3
- [ ] Bump Neon to a paid tier with point-in-time recovery
