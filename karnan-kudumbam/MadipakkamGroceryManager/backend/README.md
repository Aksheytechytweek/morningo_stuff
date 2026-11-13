# Karnan Kudumbam backend (integration scaffold)

This backend is a secure integration layer; it is not active until deployed and configured.

## Capabilities in source

- Firebase ID-token verification for authenticated app requests.
- Expense sync endpoint, scoped to the signed-in user's household ID.
- Report-subscription endpoint that only enables reports for the verified signed-in email address.
- Rewalto free-credit adapter for manual PIN-based listing searches, with free-balance preflight and per-search re-checks; no paid fallback.
- Server-side single-store summary; only same-platform, exact-name/pack, known in-stock, PIN-specific records can complete a local basket.
- Daily report job endpoint that reads Firestore expenses, generates a PDF, emails it through Resend and records delivery status to prevent repeat sends.
- Health endpoint and security headers.

## Required external setup (not supplied in source)

1. Create a Firebase project and enable Authentication + Firestore. The Android app must sign in and sync expenses before the server can report on them.
2. Deploy this backend to a trusted HTTPS host with Firebase Admin Application Default Credentials.
3. Create a Resend account, verify a sending domain, and set `RESEND_API_KEY` and `REPORT_FROM_EMAIL` as server secrets.
4. Set a long random `DAILY_JOB_SECRET` as a server secret.
5. Add GitHub repository secrets `REPORT_JOB_URL` (the HTTPS URL ending in `/internal/jobs/daily-report`) and `DAILY_JOB_SECRET`. The included workflow triggers at 20:00 IST (GitHub may delay scheduled runs).
6. Create a Rewalto free account (no card), obtain its API key, and set `REWALTO_API_KEY` in the backend host secret manager. Never add paid credits or subscribe. The `/v1/rewalto/balance` endpoint exposes only the free balance. `/v1/rewalto/basket/compare` preflights the worst-case free-credit cost and re-checks free credits before each search; if the balance cannot be verified or is insufficient, it stops without making charged product searches. Product search is manual only, initiated by the app's Check Live Prices button. The integration caps each item search at 10 results (maximum one credit per search).

## Price provider response contract

The configured endpoint receives:

```json
{
  "location": { "postalCode": "600091", "city": "Chennai", "state": "Tamil Nadu", "country": "IN" },
  "lines": [{ "id": "line-1", "name": "Tomato", "quantity": 1, "unit": "500 g" }],
  "providers": ["zepto", "blinkit", "swiggy_instamart", "flipkart_minutes", "bigbasket"]
}
```

It must return `quotes`, one quote per app, each with `providerId`, `providerName`, `serviceable`, `checkoutQuoteAvailable`, `checkedAt`, `quoteExpiresAt`, `items` (each item has `requestLineId`, `available`, `lineTotal`) and checkout values `deliveryFee`, `handlingFee`, `platformFee`, `smallCartFee`, `tax`, `discount`. The backend ranks only complete, serviceable baskets with a checkout quote. If any item is missing, that app is ineligible. No line-item splitting is possible in the ranking function.

## Report data contract

Firestore collections:

- `households/{uid}/expenses/{expenseId}`: `date` (`YYYY-MM-DD`), `amount`, `category`, `note`, `payment`.
- `reportSubscriptions/{uid}`: `email`, `enabled`, `monthlyBudget`, `reportTime`, `timeZone`.
- `reportDeliveries/{uid_YYYY-MM-DD}`: send status and provider message ID.

The client must call `PUT /v1/report-subscription` with a Firebase ID token and a verified email equal to the requested recipient. It must call `POST /v1/expenses/sync` with the same token to sync expense data.

## Security notes

- Never put Firebase service-account JSON, Resend keys, Rewalto API keys or the job secret in the Android app or source control.
- Rewalto search results are dated public-page observations, not guaranteed current checkout prices. Preserve `checkedAt` and PIN context; never present a stale or national-only record as a PIN-specific live price. BigBasket national observations are excluded from PIN-specific basket eligibility. Fees/final checkout totals are not available from this integration.
- Firestore Admin SDK bypasses Firestore client rules; API authorization is therefore enforced in the server routes.
- The current sync endpoint intentionally scopes each household to the authenticated UID. Multi-member family sharing needs an explicit household-membership model and security review before enabling it.
- The daily report endpoint is intended for a trusted scheduler only. Protect it with the random job secret and HTTPS.
- This scaffold has not been deployed or penetration-tested. It does not send email until all secrets, verified sender domain, user authentication and cloud data are configured.
