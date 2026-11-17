# Karnan Kudumbam — implementation and deployment status

Updated 2026-11-17. Git source and CI configuration are updated; deployment and device testing are still pending.

## Source implementation
- Android Jetpack Compose app with local Room storage, grocery planning, expenses, basket, analysis and on-device PDF sharing.
- Optional Firebase Google sign-in UI and HTTPS API client.
- Cloudflare Worker API using the Fetch runtime; no Express listener, Docker runtime or Firebase Admin service-account key.
- Firebase ID-token verification using Google's Secure Token signing keys.
- Owner-scoped Firestore REST expense upload/download.
- Verified-email report preference storage. Scheduled email/PDF delivery is intentionally not active.
- Manual Rewalto catalogue/price observation endpoints with free-credit checks and no paid fallback.
- Firestore rules scoped to the authenticated user's own household data.
- Root-level GitHub Actions workflows for backend tests and Android debug APK builds.

## Required owner setup
1. Cloudflare Worker: root directory `karnan-kudumbam/MadipakkamGroceryManager/backend`; build command `npm install`; deploy command `npm run deploy`.
2. Add `FIREBASE_PROJECT_ID` as a Worker variable. Add `REWALTO_API_KEY` as a Worker secret only if enabling manual price checks.
3. Configure Firebase Authentication (Google provider), Firestore, Android package `in.madipakkam.grocery`, and a project-specific `app/google-services.json`. Do not commit the JSON file if it contains project configuration you want private; never commit service-account credentials.
4. Deploy `firestore.rules` to the Firebase project.
5. Run GitHub Actions and inspect backend test and APK build results. Install the debug APK and test on a real Android device.

## Explicitly not ready
- No Cloudflare deployment has been performed by this commit.
- No Firebase project ID, Google services configuration, Rewalto key or other secrets have been supplied/configured.
- Scheduled PDF email delivery is not enabled; the API only stores the preference and reports pending scheduler setup.
- Rewalto has not been validated against the owner's actual account response. It is manual-only, free-credit guarded, and never uses a paid fallback.
- Multi-member household sharing is not enabled; the first release is one Firebase UID per household.
- No signed release APK/AAB or device acceptance test has been completed.

## Cost and security guardrails
- No paid Cloudflare plan is assumed; provider free quotas and terms can change.
- No real `.env`, Firebase service-account JSON, Resend key, Rewalto key or scheduler secret in Git or APK.
- No shopping orders are placed.
- Price observations are not guaranteed live checkout quotes; users must verify stock, PIN availability and final fees in the retailer app.
