# Karnan Kudumbam API — Cloudflare Workers

This Worker verifies Firebase ID tokens and accesses Firestore. Scheduled email delivery uses a separate Firebase service-account secret; no service-account credential is bundled in the app.

## Routes
- GET /health — public deployment smoke check.
- GET /v1/rewalto/sample — authenticated public version/catalogue check; zero price-search credits.
- GET /v1/rewalto/balance — authenticated free-credit balance check.
- POST /v1/rewalto/basket/compare — manual-only price observations, free-credit preflight and per-search rechecks; no paid fallback.
- POST /v1/households — create the signed-in user's household and owner membership.
- POST /v1/households/invitations — owner-only email-bound invitation creation.
- POST /v1/households/join — verified-email invitation acceptance.
- POST /v1/expenses/sync — authenticated household expense upload.
- GET /v1/expenses/sync?householdId=... — authenticated household expense download.
- PUT /v1/report-subscription — stores a verified-email report preference and selected household ID.
- POST /internal/jobs/daily-report — secret-protected manual report run.
- Worker Cron — checks report subscriptions every minute in Asia/Kolkata and sends a PDF at the selected report time.

Android requests send Firebase ID tokens as Authorization: Bearer <token>.

## Cloudflare deployment
The repository includes `wrangler.toml`. Run the manual GitHub workflow `.github/workflows/karnan-kudumbam-deploy-worker.yml` to deploy.

Configure GitHub repository secrets/variables:
- Secret `CLOUDFLARE_API_TOKEN`: token with Workers Scripts edit permission.
- Variable `CLOUDFLARE_ACCOUNT_ID`: the Cloudflare account ID.

Configure Worker variables/secrets:
- `FIREBASE_PROJECT_ID=karnan-kudumbam` is set in `wrangler.toml`.
- Secret `REWALTO_API_KEY`: optional; add only after confirming the Rewalto account and free-credit allowance.
- Secret `FIREBASE_SERVICE_ACCOUNT_JSON`: Firebase service-account JSON with Firestore data read/write permissions, used only by the report job.
- Secret `RESEND_API_KEY`: Resend API key.
- Variable `REPORT_FROM_EMAIL`: sender address verified in Resend.
- Secret `DAILY_JOB_SECRET`: long random secret protecting the manual report endpoint.

Never commit API keys, service-account JSON, signing keys or `.dev.vars`.

## Firebase deployment
The repository includes `firebase.json` and `firestore.rules`. Run the manual workflow `.github/workflows/karnan-kudumbam-deploy-firestore.yml` to publish the rules. It requires the GitHub secret `FIREBASE_SERVICE_ACCOUNT_JSON` with permission to deploy rules to project `karnan-kudumbam`.

Enable Email/Password Authentication and Firestore in Firebase Console. After publishing, test household creation, invitations and expense sync using two distinct verified Firebase accounts. CI cannot substitute for this live test.

## Rewalto cost and data guard
Price checks run only after the user taps Check Live Prices. The Worker verifies the free-credit balance before searching and rechecks before every product search. Basket size is capped at 20; each search requests at most 10 results. If free credits are insufficient or cannot be parsed, the operation stops. No paid fallback is implemented.

Rewalto's public contract documents bearer API-key authentication, `GET /v1/credits/balance` and `GET /v1/data/listings/search`. Reads are credit-based; the integration must validate the actual connected account's balance response and permissions before live use. See the [Rewalto API guide](https://rewalto.com/developers/docs) and [data contract](https://rewalto.com/developers/docs/data).

Retailer comparison requires exact product/pack evidence, an in-stock observation no older than 48 hours and a PIN-specific observation. Missing timestamps, stale readings and national/missing-PIN observations are treated as unknown and cannot complete the basket. BigBasket is accepted when its actual record carries the requested PIN. Results are dated public-page observations, not checkout quotes; delivery, handling and final checkout fees are not included.

## Daily PDF email
The Worker includes verified-email subscription storage, household expense retrieval using a Firebase service account, PDF generation, Resend attachment delivery and a Firestore delivery ledger to avoid repeat sends for the same user/date/time. The Worker Cron checks every minute and matches the selected Asia/Kolkata report time. It exits quietly until the Firebase service account, Resend key and verified sender are configured. The manual route additionally requires `DAILY_JOB_SECRET`.

The attached PDF currently summarizes recorded household expenses. It does not yet include a generated daily grocery shopping list or live retailer checkout totals.

## Release and acceptance
A debug APK is produced by the repository's Android workflow. A separate signed AAB workflow requires the release keystore and passwords to be configured as GitHub secrets. Install and test on the Moto Edge 60 Pro after signing.

Before production acceptance, test Firebase token verification, published Firestore rules, two-account household sharing, expense sync, a real report email, Rewalto free-credit parsing and PIN-level stock/price data.
