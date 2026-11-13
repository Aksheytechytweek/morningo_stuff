# Karnan Kudumbam — implementation and deployment status

Updated 2026-11-12. This is source code, **not a compiled/tested APK**.

## Implemented in source

- Kotlin Jetpack Compose household dashboard, expenses, groceries, price notebook, basket, analysis and PDF sharing.
- Room persistence layer and one-time import of existing `madipakkam_grocery.db` SQLite records; legacy database retained for recovery.
- Cloud screen for optional Google/Firebase sign-in, HTTPS backend URL, PIN, upload/download expense sync, report subscription and Rewalto public free-sample check.
- Basket tab includes a manual Check Live Prices loader and Rewalto result cards; requests are user-triggered only.
- Firebase ID-token-protected Express endpoints; Firestore backend; owner-scoped household data.
- Single-app basket rules are enforced: every required item must have a matching pack and known in-stock, PIN-specific observation from the same platform; no split orders. Final checkout fees are not yet available from Rewalto.
- Daily 20:00 IST scheduler workflow, Resend PDF email backend, duplicate-send guard.
- Ten Node.js unit tests pass (`cd backend && npm test` or `node --test`).

## External setup REQUIRED (not complete)

1. **Android build:** Install Android Studio / Android SDK 35 and build. This environment has no Android SDK or Gradle executable; no APK has been compiled or device-tested.
2. **Firebase:** Create a Firebase project (Spark is sufficient for authentication and modest Firestore usage), enable Google sign-in, register Android package `in.madipakkam.grocery`, add `app/google-services.json`, configure Google Services Gradle plugin (see below), and deploy backend with Firebase Admin application-default credentials. Without this, sign-in/sync are disabled.
3. **Email:** Verify a sending domain with Resend, supply `RESEND_API_KEY`, `REPORT_FROM_EMAIL`, and `DAILY_JOB_SECRET` to the backend. Deploy backend, configure GitHub Actions secrets `REPORT_JOB_URL` and `DAILY_JOB_SECRET`, and enable email subscription by signing in as the verified recipient `aksheysun@gmail.com`. Scheduled GitHub Actions may be delayed; use managed cloud scheduler for precise delivery. No email was sent.
4. **Rewalto price integration:** Backend adapter, free-credit preflight, per-search free-balance recheck, manual-only API route and Android Check Live Prices loader/results are implemented. Still needs a Rewalto free account/API key, backend deployment and real-response validation. No paid fallback exists. Provider results are timestamped public-page observations, not guaranteed live checkout prices; BigBasket national records are excluded from PIN-specific eligibility. Delivery fees/final checkout totals are not available.
5. **Cloud sync:** Initial manual upload/download is implemented; production-grade continuous two-way sync, conflict resolution and multi-device de-duplication remain to be hardened. Use one signed-in household account for this build.
6. **Testing:** Run Android build and on-device UI tests, auth integration tests, email sandbox tests and real provider acceptance tests before production use.

## Firebase Android setup

Add to root `build.gradle.kts` plugins:

```kotlin
id("com.google.gms.google-services") version "4.4.2" apply false
```

Add to `app/build.gradle.kts` plugins:

```kotlin
id("com.google.gms.google-services")
```

Put the project-specific `google-services.json` in `app/` (do not commit secrets or service-account JSON). Configure SHA-1/SHA-256 signing fingerprints for Google login. Set deployed HTTPS backend URL from Cloud tab.

## Security

- No Resend, Rewalto or Firebase Admin secrets in APK.
- Firestore client reads/writes denied; authenticated backend mediates access.
- Recipient must match the signed-in Firebase verified email before enabling automatic delivery.
- Backend scheduler secret is required for daily report dispatch.
- No shopping orders are placed by the application.

## Known issues / next engineering work

- UI currently calls compatibility Room facade from Compose; move to ViewModel + Flow and background repositories for production-grade responsiveness.
- Legacy import runs once; review date/time handling and large datasets.
- Download sync has a simple per-device imported-ID set; production sync should use stable entity IDs and server revisions for safe multi-device merges.
- Report delivery idempotency after uncertain network failures requires provider-level idempotency/reconciliation.
- Firebase/Resend/provider credentials and deployment are controlled by account owner, not by this source package.


### Rewalto free-only policy
- Free allowance advertised by Rewalto: 1,000 API credits/month, no card.
- Worst-case cap: one listing-search request per basket item, 10 records maximum per request (maximum one credit each). Basket capped at 20 items.
- Server refuses if free balance is insufficient or cannot be parsed, and rechecks before each search. Paid usage is never an allowed fallback.
- Live provider integration has not been tested with a real API key; configure `REWALTO_API_KEY` on the backend only.
