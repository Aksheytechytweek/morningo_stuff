# Karnan Kudumbam API — Cloudflare Workers

This Worker uses the Fetch runtime directly. It does not start Express, use Docker, or bundle Firebase Admin service-account credentials.

## Routes
- GET /health — public health check.
- GET /v1/rewalto/sample — authenticated public catalogue/version test; zero price-search credits.
- GET /v1/rewalto/balance — authenticated free-credit balance check.
- POST /v1/rewalto/basket/compare — manual-only price observations, free-credit preflight and per-search rechecks; no paid fallback.
- POST /v1/expenses/sync — authenticated upload to the signed-in user's own household.
- GET /v1/expenses/sync — authenticated download of that household's expenses.
- PUT /v1/report-subscription — stores a verified-email preference only. It does not send scheduled email.

Android requests continue to send Firebase ID tokens as Authorization: Bearer <token>.

## Cloudflare setup
1. Worker name: karnan-kudumbam-api.
2. Git root directory: karnan-kudumbam/MadipakkamGroceryManager/backend.
3. Build command: npm install.
4. Deploy command: npm run deploy.
5. Set FIREBASE_PROJECT_ID as a plain-text Worker variable.
6. Optionally set REWALTO_API_KEY as a Worker secret after creating a free Rewalto account.
7. Do not set PORT, Firebase service-account JSON, Resend credentials or a real .env file.

For local development, create an untracked .dev.vars file with FIREBASE_PROJECT_ID and optional REWALTO_API_KEY, then run npm install && npm run dev.

## Firebase
Enable Authentication and Firestore. Set the exact Firebase project ID. Deploy the repository's firestore.rules. The Worker verifies Firebase ID tokens against Google's published Secure Token signing keys and accesses Firestore REST with the caller's ID token. The first release is one Firebase UID per household; multi-member sharing is not enabled.

## Rewalto cost guard
Price checks run only after the user taps Check Live Prices. The Worker verifies the free-credit balance before searching and rechecks before every product search. Basket size is capped at 20; each search is limited to 10 records. If free credits are insufficient or cannot be parsed, the operation stops. No paid fallback is implemented. Results are stored public-page observations, not checkout quotes; verify timestamps, PIN specificity, stock and price in the retailer app. Checkout fees are not included.

## Email status
Automated PDF email delivery is intentionally disabled in this migration. The report endpoint only saves the user's verified-email preference and returns deliveryStatus=pending-scheduler-setup. Do not enable the legacy Express email workflow or tell users email is active until a secure scheduled Worker flow is implemented and tested.

## Local validation
Run npm test, then npm run dev. Before production, test Firebase token verification, Firestore rules, sync, Rewalto free-credit parsing and Android sign-in against a real Firebase project.
