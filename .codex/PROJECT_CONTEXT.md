# Karnan Kudumbam — Codex Project Handoff

## Repository and branch
- GitHub: https://github.com/Aksheytechytweek/morningo_stuff
- Work branch: karnan-kudumbam-complete-integration
- Do not use main for project changes.
- Android project: karnan-kudumbam/MadipakkamGroceryManager

## Product
Karnan Kudumbam is an Android household grocery and expense manager for a 3-adult home in Madipakkam, Chennai (PIN 600091). The monthly household food/grocery ceiling is ₹15,000. Meals are mostly home-cooked South Indian food. Priorities: offline-first use, shared household, expense tracking, practical grocery basket, price observations, reports and careful budget control.

## Stack and current modules
### Android
- Kotlin + Jetpack Compose + Material 3.
- Application ID / namespace: in.madipakkam.grocery.
- Gradle module: karnan-kudumbam/MadipakkamGroceryManager/app.
- Gradle versionName 1.1.0, versionCode 2.
- Firebase Authentication, Google Sign-In, Room/local persistence.
- CI uses Java 21, Android platform 35 and Build Tools 35.0.0. Module emits Java/Kotlin 17 bytecode.
- Main files: MainActivity.kt, AuthGate.kt, BackendApi.kt, CloudScreen.kt, GroceryDb.kt, DailyPlanner.kt, ReportPdf.kt.

### Backend
- Cloudflare Worker: karnan-kudumbam/MadipakkamGroceryManager/backend.
- Entry point: src/worker.js; Wrangler worker name: karnan-kudumbam-api.
- Rewalto parsing and provider eligibility: src/rewalto.js.
- Backend tests use Node built-in test runner (npm test).
- Firebase project ID is configured as karnan-kudumbam in wrangler.toml.
- Android sends Firebase ID tokens as Bearer auth to the Worker.

## Product requirements
- Household of 3 adults; PIN 600091; ₹15,000 total monthly food/grocery budget.
- Lists should reflect local South Indian cooking, sensible quantities, minimize spoilage and select seasonal/low-cost items.
- Retailers: Zepto, Blinkit, Swiggy Instamart, Flipkart Minutes and BigBasket.
- Never split one day's basket across retailers. Recommend a provider only when it covers every unchecked basket line.
- Manual-only price refresh. No hidden or scheduled price searches.
- Rewalto free-credit-only. No paid fallback.
- Price observations are not checkout quotes. Distinguish catalogue observations, PIN availability, estimated local market prices and confirmed checkout totals.
- Complete eligibility requires exact product and pack, explicit in-stock state, <=48h timestamp and exact requested PIN.
- Unknown, stale, undated or non-local observations cannot complete a basket.
- Never imply an order is placed by the app.

## Live price flow
1. User taps Check Live Prices in Basket.
2. App gets a Firebase ID token and reads local backend_url and postal_code from karnan_cloud preferences.
3. BackendApi.compareRewaltoBasket calls POST /v1/rewalto/basket/compare.
4. Worker verifies Firebase token, validates PIN and basket, checks Rewalto free balance, fails closed if balance is unknown/insufficient, re-checks before each product search, then calls GET /v1/data/listings/search.
5. rewalto.js maps listing fields, stock freshness, exact pack/product and PIN, then builds retailer summaries.
6. UI renders observations and completion. Checkout fees/final payable are excluded unless a true checkout quote source exists.

### Rewalto parser care
- Support current fields such as priceMinor, packSizeRaw, inStock and observedAt, retaining tested legacy aliases only when needed.
- Rewalto minor currency units are paise.
- Normalize retailer identifiers cautiously.
- BigBasket is not inherently excluded; it can qualify only when its actual observation matches requested PIN, freshness, product/pack and in-stock rules.
- Free balance parser must fail closed. Never infer free balance from total balance.

## Backend routes
- GET /health
- GET /v1/rewalto/sample — no-credit public version/catalogue sample.
- GET /v1/rewalto/balance — authenticated free-credit check.
- POST /v1/rewalto/basket/compare — authenticated manual free-credit-only observations.
- POST /v1/households
- POST /v1/households/invitations
- POST /v1/households/join
- POST /v1/expenses/sync
- GET /v1/expenses/sync?householdId=...
- PUT /v1/report-subscription
- POST /internal/jobs/daily-report — secret protected.

## Deployment/configuration status
Android requires a real HTTPS Worker URL saved via Cloud → HTTPS backend URL. Do not invent it. After deployment, retrieve the URL from Cloudflare output and configure the app.
Deployment workflow: .github/workflows/karnan-kudumbam-deploy-worker.yml. Required GitHub secret CLOUDFLARE_API_TOKEN (Workers Scripts edit) and variable CLOUDFLARE_ACCOUNT_ID.
Worker runtime: FIREBASE_PROJECT_ID and REWALTO_API_KEY. Rewalto key must have usage:read and data:read scopes.
Scheduled report service additionally uses FIREBASE_SERVICE_ACCOUNT_JSON, RESEND_API_KEY, REPORT_FROM_EMAIL and DAILY_JOB_SECRET.
Never commit credentials. Firebase Email/Password Auth and Firestore rules need configuration and real-account tests.

## Build and tests
Backend from karnan-kudumbam/MadipakkamGroceryManager/backend:
- npm install
- npm test
- npx wrangler deploy --dry-run

Android from karnan-kudumbam/MadipakkamGroceryManager:
- gradle --no-daemon assembleDebug

APK: app/build/outputs/apk/debug/app-debug.apk. Verify CI workflow and uploaded artifact before reporting a build success. Physical-device testing is separate.

## First Codex task
"Read AGENTS.md and .codex/PROJECT_CONTEXT.md. Inspect the active integration branch and summarize the exact remaining live-price deployment blockers. Do not deploy or change cloud resources until you've verified tool connectivity and asked approval for resource writes."
