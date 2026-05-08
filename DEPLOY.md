# Deploy: Neon (DB) + Render (backend) + sideloaded APK

Total time: **~30 minutes** if you already have GitHub + an Android Studio install.

## What you'll end up with

- **Postgres** on Neon (free tier — 0.5 GB)
- **Backend API** at `https://fieldpharma-api.onrender.com` (free tier)
- **APK file** you can download and install on any Android phone
- (Optional) **Admin panel** on Vercel

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
3. Pick the `fieldpharma` repo. Render reads [`render.yaml`](render.yaml) automatically and shows a "fieldpharma-api" service ready to deploy
4. Click **Apply** → it asks you to fill in `DATABASE_URL` — paste the Neon URL from Step 2
5. `JWT_SECRET` is auto-generated. Leave `CORS_ORIGIN` as `*` for now
6. Click **Create New Resources**

The first build takes ~3-5 min:
- `npm install` pulls all deps
- `prisma generate` creates the client
- `npm run build` runs tsc
- `prisma migrate deploy` applies migrations to Neon

When it's green: get the URL from the top of the service page (e.g. `https://fieldpharma-api.onrender.com`).

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

1. **Edit** `android/app/build.gradle.kts`. Find this line:
   ```kotlin
   buildConfigField("String", "API_URL", "\"https://CHANGE-ME.onrender.com\"")
   ```
   Replace `CHANGE-ME.onrender.com` with your actual Render hostname. Keep the quotes and backslashes.

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

## Optional — Deploy the admin panel to Vercel

You'll want this to demo to clients without running anything locally.

1. **https://vercel.com** → sign in with GitHub → **Add New → Project** → import `fieldpharma`
2. **Root Directory:** click "Edit" → set to `admin`
3. **Environment Variables:** add `NEXT_PUBLIC_API_URL` = `https://fieldpharma-api.onrender.com`
4. **Deploy**

You get a URL like `https://fieldpharma-admin.vercel.app`. Login with `admin@baseras.test` / `admin123`.

After this, **tighten CORS** on Render: change `CORS_ORIGIN` env var from `*` to your Vercel URL. Redeploy will pick it up automatically.

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
