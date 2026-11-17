# Karnan Kudumbam API — Cloudflare Workers

This Worker uses the Fetch runtime directly and verifies Firebase ID tokens. It does not bundle Firebase Admin service-account credentials.

## Routes
- GET /health — public health check.
- GET /v1/rewalto/sample — authenticated public version/catalogue check; zero price-search credits.
- GET /v1/rewalto/balance — authenticated free-credit balance check.
- POST /v1/rewalto/basket/compare — manual-only price observations, free-credit preflight and per-search rechecks; no paid fallback.
- POST /v1/households — create the signed-in user's household and owner membership.
- POST /v1/households/invitations — owner-only email-bound invitation creation.
- POST /v1/households/join — verified-email invitation acceptance.
- POST /v1/expenses/sync — authenticated household expense upload.
- GET /v1/expenses/sync?householdId=... — authenticated household expense download.
- PUT /v1/report-subscription — stores a verified-email report preference only. It does not send scheduled email.
- GET /health — deployment smoke check.

Android requests send Firebase ID tokens as Authorization: Bearer <token>.

## Cloudflare deployment
The repository includes `wrangler.toml`. The manual GitHub workflow `.github/workflows/karnan-kudumbam-deploy-worker.yml` deploys this Worker when run from Actions.

Configure repository secrets/variables:
- Secret `CLOUDFLARE_API_TOKEN`: token with Workers Scripts edit permission for the target account.
- Variable `CLOUDFLARE_ACCOUNT_ID`: the Cloudflare account ID.
- Worker variable `FIREBASE_PROJECT_ID`: set to `karnan-kudumbam` in `wrangler.toml`.
- Worker secret `REWALTO_API_KEY`: optional; add only after confirming the Rewalto account and free-credit allowance.

Never commit API keys, service-account JSON, signing keys or `.dev.vars`.

## Firebase deployment
The repository includes `firebase.json` and `firestore.rules`. The manual GitHub workflow `.github/workflows/karnan-kudumbam-deploy-firestore.yml` publishes the rules.

Configure the repository secret `FIREBASE_SERVICE_ACCOUNT_JSON` with a service-account JSON that has permission to deploy Firestore rules to project `karnan-kudumbam`. Do not upload that JSON to the repository or chat. Enable Email/Password Authentication and Firestore in Firebase Console.

After publishing, test household creation, invitations and expense sync using two distinct verified Firebase accounts. A CI test cannot substitute for this live test.

## Rewalto cost and data guard
Price checks run only after the user taps Check Live Prices. The Worker verifies the free-credit balance before searching and rechecks before each product search. Basket size is capped at 20; each search requests at most 10 results. If free credits are insufficient or cannot be parsed, the operation stops. No paid fallback is implemented.

Retailer comparison requires exact product/pack matching and a PIN-specific, fresh stock observation. BigBasket is no longer categorically excluded when Rewalto actually returns a matching PIN. National or missing-PIN observations cannot complete a PIN-specific basket. Results are dated public-page observations, not retailer checkout quotes; delivery, handling and final checkout fees are not included.

Rewalto's public contract describes bearer API-key authentication, `GET /v1/credits/balance`, and `GET /v1/data/listings/search`. Validate the actual connected account's response shape and permissions before enabling live calls. A public sample does not prove account access or consume search credits.

## Daily PDF email status
Not active yet. `PUT /v1/report-subscription` only stores a verified-email preference. The current Worker returns HTTP 503 for `/internal/jobs/daily-report`; the scheduled GitHub workflow must not be treated as a working delivery system. Do not enable a schedule until a secured server-side report job, durable scheduling, PDF generation, a verified sender and delivery tests are implemented.

## Local validation
Run `npm install && npm test`, then `npm run dev`. Before production, test Firebase token verification, published Firestore rules, two-account household sharing, expense sync, Rewalto free-credit parsing and Android sign-in against the live project.
