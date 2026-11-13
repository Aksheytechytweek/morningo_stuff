> **Current status:** See [DEVELOPMENT_STATUS.md](DEVELOPMENT_STATUS.md) for the exact implemented-vs-unconfigured breakdown. No APK or live price/email service is claimed.

# கர்ணன் குடும்பம் (Karnan Kudumbam)

Native Android household finance and grocery-planning app for a 3-adult household in Madipakkam, Chennai.

## Product defaults
- App name: கர்ணன் குடும்பம் (Karnan Kudumbam)
- Language direction: Tamil + English branding; current screens are English-first
- Household size: 3 adults
- Monthly household budget: ₹15,000
- Delivery area configured as Madipakkam, Chennai 600091 (editable/location-aware integration is a later step)
- Offline-first local SQLite storage
- Daily grocery reminder: 7:00 AM local device time
- Requested daily email report: aksheysun@gmail.com at 8:00 PM IST

## Implemented in this source version
- Rebranded Android application and dashboard
- Monthly budget dashboard and expense entry (category, description, payment method)
- Shopping basket with add/remove/check-off items
- Single-app basket comparison logic across Zepto, Blinkit, Swiggy Instamart, Flipkart Minutes and BigBasket
- A platform is eligible only when every unchecked basket item has a recorded matching price; the app never recommends splitting a basket
- Manual price observations with source and date; no fabricated live prices
- Expense category analysis
- Local report-recipient and delivery-time preferences
- On-device PDF expense statement generation and email/share-sheet handoff
- Daily 7 AM local grocery reminder using WorkManager
- Existing SQLite database upgrade from version 1 to version 2, adding the basket table

## Important integration status
### Rewalto free-credit price integration
The backend now contains a Rewalto adapter and a manual-only `Check Live Prices` flow. It reads the current free-credit balance before searching, estimates the worst-case request cost (one credit per basket line, at most 10 records per line), and refuses the request if the free balance cannot cover it. It re-checks the free balance before each product search. No paid fallback, top-up, subscription or payment endpoint is called. Keep the Rewalto account on the free plan and never add paid credits or a paid plan.

Set `REWALTO_API_KEY` only in the backend host's secret manager. The Android app never receives this key. The free Rewalto API allowance is advertised as 1,000 credits/month, no card; listing searches cost 1 credit per 10 delivered records (rounded up), empty searches cost nothing, and the same page is free to reread within 7 days. The integration fails closed if the balance response cannot be parsed. Rewalto's data are readings from public pages, not retailer-owned APIs or guaranteed real-time checkout quotes. Every result must retain its timestamp. BigBasket readings are national rather than PIN-specific and are excluded from PIN-specific complete-basket eligibility. Rewalto's current Flipkart Minutes coverage is focused on dark stores/delivery-time data, not a guaranteed item-price feed; show no product quote where none is returned. Checkout fees, final payable amounts and ordering are not provided by this source.

The Smart Basket button is manual only: no background or scheduled price polling. A platform can be recommended as a provisional lowest product subtotal only when every basket line has a matching product/pack, known in-stock state and PIN-specific reading from that same platform. The app never splits a basket. Always confirm price, stock, delivery fee and final payable amount inside the retailer app before ordering.

### Automatic email and PDF
The app currently stores the requested recipient/time locally and can generate a PDF for manual sharing through the Android share sheet. It does **not** send scheduled emails or automatically attach a PDF yet. Secure automatic delivery requires a backend scheduler and a transactional email provider. API keys must be stored only as backend secrets, never in the Android APK. Before activation, implement daily report aggregation, scheduled PDF generation, retry/idempotency, delivery logs and user consent. The requested recipient is aksheysun@gmail.com and preferred time is 20:00 Asia/Kolkata.

### Cloud sync
No cloud sync is connected in this source version. Local records stay on the device. Cloud backup, authentication and multi-device family sharing are later milestones; do not add production secrets to this repository.

## Build APK
1. Install Android Studio (Ladybug or newer) and Android SDK Platform 35.
2. Open this folder in Android Studio.
3. Allow Gradle sync to finish.
4. Select **Build > Build Bundle(s) / APK(s) > Build APK(s)**.
5. Debug APK output: `app/build/outputs/apk/debug/app-debug.apk`.

The development environment used for this update has Java 21 but no Android SDK, Gradle installation or adb. The source has therefore **not been compiled or device-tested here**. The included GitHub Actions workflow can build a debug APK after the project is pushed to a repository and the workflow is run.

## Next development milestones
1. Replace the legacy SQLite helper with Room entities/DAOs and add repository/ViewModel layers.
2. Configure the Rewalto free API key on the backend, validate actual response fields and PIN coverage, and test a small basket before relying on price recommendations. Final checkout-cost comparison still requires checkout fee data from the retailer or an authorised source.
3. Add editable household address and precise serviceability selection.
4. Implement secure backend email scheduling, daily PDF statement and delivery logs.
5. Add optional Firebase authentication/cloud sync after selecting the cloud project and security rules.
6. Add Tamil translations, accessibility checks, automated tests and signed release build.

## Privacy and security
- Current expense, basket and price data are stored locally in the app database.
- Email preferences are stored locally; no report is transmitted.
- No WhatsApp integration or message sending is implemented.
- Never commit API tokens, email provider keys, Firebase service-account files or personal credentials.

## New backend integration source (not deployed)
A provider-neutral Node.js backend scaffold is included under `backend/`:
- Firebase ID-token verification and user-scoped expense sync endpoint.
- Verified-recipient daily-report subscription endpoint.
- Server-side one-store basket ranking, requiring a complete in-stock basket and an authorised checkout quote before recommending a store.
- PDF generation and Resend email delivery with Firestore delivery records and a retry-safe sending claim.
- A GitHub Actions schedule configured for 20:00 Asia/Kolkata to trigger the secured report job (scheduled GitHub runs can be delayed).
- Android `BackendApi.kt` client transport for basket comparison, expense sync and report settings.

The backend is not deployed. It needs a Firebase project/authentication, an HTTPS backend host, a verified Resend sender domain, GitHub Actions secrets, and a Rewalto free API key configured as a backend secret. The Android UI now contains the manual price-check button and calls the Rewalto backend route after Google sign-in. No price request runs until the backend is deployed and configured. No paid Rewalto fallback is allowed.

Backend unit tests for the single-store rule can be run with `cd backend && npm test` after installing dependencies. The isolated decision tests were run successfully in the development environment.
