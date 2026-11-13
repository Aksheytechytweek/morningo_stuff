# Karnan Kudumbam - integration contracts

## 1. Single-store basket comparison

The app must not optimise each line item independently. It must calculate one candidate basket per provider and only rank providers that can fulfil every required item.

A production provider response should include:
- provider ID and serviceable location / PIN
- provider SKU, canonical product ID, product name, brand and pack size
- quantity and unit price
- in-stock / out-of-stock / unknown status
- price checked timestamp and source confidence
- delivery fee, handling fee, platform fee, taxes, discounts and minimum-order condition
- final payable amount when an authorised checkout quote is available

Eligibility invariant:
`eligible(provider) = every required basket line has an exact or user-approved equivalent in stock at that provider`.

Ranking invariant:
`rank eligible providers by final payable amount; never combine line items from different providers`.

If no provider can fulfil the full basket, show the missing products for each provider and ask the user whether to remove or defer an item. Never silently split the order.

Matching must consider brand, variant, pack size and unit quantity. A 500 g pack must not be treated as equivalent to a 1 kg pack without normalising unit price and confirming the user's requested quantity.

## 2. Price-provider adapter

Define a backend interface similar to:

- `searchCatalog(location, query)`
- `getAvailability(location, providerSku)`
- `quoteBasket(location, providerId, lines)`

Only use official APIs or providers with explicit authorisation. Do not scrape private app endpoints or bypass bot/access controls. Mark every result with its freshness and confidence. If a provider is unavailable, show unavailable rather than stale-as-live.

## 3. Daily email report

Requested preferences:
- Recipient: `aksheysun@gmail.com`
- Schedule: every day at 20:00 `Asia/Kolkata`
- Format: detailed email with attached PDF

Backend workflow:
1. Scheduler triggers once per local calendar day.
2. Aggregate expenses for that date and the current month.
3. Generate a PDF statement from the same immutable report snapshot.
4. Send through a transactional email provider using backend-only credentials.
5. Use an idempotency key such as `daily-report:{userId}:{yyyy-MM-dd}` to prevent duplicates.
6. Record queued/sent/failed status and provider message ID.
7. Retry transient failures with bounded backoff; never duplicate a successful send.

Do not send if the user has not enabled reports and confirmed the recipient. Store API keys in backend secret storage. The Android client must never contain email-provider credentials.

## 4. Current implementation boundary

The current app contains local expenses, basket records, manual price observations, locally saved report preferences and on-device PDF generation for manual sharing. It does not yet connect to external quick-commerce catalogues or send scheduled email. This distinction must remain visible in the UI until each integration has been implemented and tested.

## 5. Backend scaffold added
The `backend/` directory now contains an Express service with Firebase ID-token verification, user-scoped expense sync, verified-email report subscription, PDF generation, Resend delivery, delivery idempotency claims and a price-provider adapter. `BackendApi.kt` contains the Android HTTPS client transport. The server-side basket ranking logic has Node unit tests proving incomplete baskets cannot be recommended. The app still requires Firebase sign-in and UI wiring before these endpoints can be used.

## 6. Activation blockers
- Firebase project ID, Authentication and Firestore must be created and configured by the owner.
- Backend must be deployed to an HTTPS host with Firebase Admin Application Default Credentials.
- A Resend account, verified sender domain, API key and `REPORT_FROM_EMAIL` are required. Add secrets to the backend host, not the Android app.
- GitHub repository secrets `REPORT_JOB_URL` and `DAILY_JOB_SECRET` are required for the included daily scheduler workflow. GitHub scheduled jobs can run late; this is not a guaranteed exact-to-the-minute scheduler.
- Rewalto integration is implemented in the backend and app UI as a manual-only latest-observation lookup. It requires a Rewalto free account/API key configured on the backend. The backend checks free credits and refuses searches if the worst-case free-credit balance is insufficient or cannot be parsed. No paid fallback is implemented. Results are dated public-page observations, not guaranteed real-time checkout quotes; BigBasket data are national rather than PIN-specific and are excluded from local basket eligibility. Rewalto's Flipkart Minutes coverage is currently described as dark stores/delivery-time data rather than a guaranteed product-price feed. Delivery/handling fees and checkout totals remain unavailable.
- Android UI wiring, Room migration, Firebase authentication/cloud sync, address settings, integration tests and signed APK/device testing remain to be completed.


## 7. Rewalto free-only API policy
- `REWALTO_API_KEY` is a backend-only secret.
- The app calls `/v1/rewalto/basket/compare` only after the user taps Check Live Prices. No background polling.
- The server reads `/v1/credits/balance`, requires a parsed free-credit balance sufficient for the worst case, and rechecks free balance before each item search.
- Each product search is limited to 10 listings, at most one credit per item. Empty results cost zero; same-page repeats within seven days are free according to Rewalto's published docs.
- No payment method, paid plan, paid credits, automatic top-up, or paid fallback is configured. If balance is insufficient or unknown, the server blocks the operation.
- The current adapter and UI are implemented but not verified against a real Rewalto account/API key. Validate actual balance and listing response fields before production.
