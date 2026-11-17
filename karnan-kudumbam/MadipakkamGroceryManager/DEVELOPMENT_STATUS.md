# Karnan Kudumbam — implementation and deployment status

Updated 2026-11-17. Work is on `karnan-kudumbam-complete-integration`; main has not been merged or changed.

## Integrated in source
- Android Jetpack Compose app with local storage, grocery planning, expenses, basket, analytics and PDF sharing.
- Firebase Email/Password sign-in, email verification and ID-token API authentication.
- Cloudflare Worker routes for household creation, owner invitations, verified-email joining and household-scoped expense sync.
- Firestore rules for owner/member access, invitation validation, expense access and verified-email report preferences.
- Rewalto server-side integration with bearer key, free-credit preflight and recheck before every product search; no paid fallback.
- Retailer matching requires exact product/pack evidence, in-stock state and PIN-specific observation. BigBasket is accepted when its actual record carries the requested PIN; national/missing-PIN observations cannot complete the basket.
- Scheduled report pipeline: verified-email subscription, household expense retrieval using a server-side Firebase service account, PDF generation, Resend attachment delivery, Firestore delivery ledger and secret-protected manual trigger.
- Cloudflare Worker Cron checks subscriptions every minute and matches the requested Asia/Kolkata report time.
- Repository-level CI workflows plus manual Firebase rules and Cloudflare Worker deployment workflows.

## External configuration required before production
1. **GitHub repository secrets/variables**
   - `FIREBASE_SERVICE_ACCOUNT_JSON` for the Firestore rules deployment workflow.
   - `CLOUDFLARE_API_TOKEN` and repository variable `CLOUDFLARE_ACCOUNT_ID` for Worker deployment.
   - `REPORT_JOB_URL` and `DAILY_JOB_SECRET` only for the optional manual report workflow; set the same random secret as a Cloudflare Worker secret.
2. **Cloudflare Worker secrets/variables**
   - `FIREBASE_SERVICE_ACCOUNT_JSON`: service account with Firestore data read/write permissions, used only by scheduled report generation.
   - `RESEND_API_KEY`: Resend API key.
   - `REPORT_FROM_EMAIL`: sender address verified in Resend.
   - `DAILY_JOB_SECRET`: long random secret for the manual job route.
   - `REWALTO_API_KEY`: optional Rewalto organisation API key. Do not enable price checks until the actual account balance shape and free allowance have been verified.
   - `FIREBASE_PROJECT_ID=karnan-kudumbam` is set in Wrangler configuration.
3. **Firebase**
   - Enable Email/Password Authentication and Firestore.
   - Publish this branch's `firestore.rules` using the manual GitHub deployment workflow.
   - Keep the Android `google-services.json` project config in the app folder and out of service-account handling.\n6. **Android signing**\n   - Create a release keystore locally. Add `ANDROID_KEYSTORE_BASE64`, `ANDROID_KEY_ALIAS`, `ANDROID_KEY_PASSWORD` and `ANDROID_STORE_PASSWORD` as GitHub Actions secrets. The signed AAB workflow fails closed if any secret is missing. Never commit the keystore.
4. **Rewalto**
   - Validate the live `/v1/credits/balance` response and API permissions using the actual account.
   - The public API contract documents bearer authentication, listing search and credit-based reads. Results remain dated public-page observations, not checkout quotes.
5. **Release/device**
   - Configure Android signing secrets outside Git, build a signed APK/AAB and install it on the Moto Edge 60 Pro.
   - Test sign-in, email verification, two-account invitation/join, shared expense sync, report email and live Rewalto checks.

## Deployment workflows
- Android debug build: `.github/workflows/karnan-android-build.yml`\n- Signed Android release (manual): `.github/workflows/karnan-kudumbam-release.yml`
- Backend tests: `.github/workflows/karnan-kudumbam-backend.yml`
- Firebase rules deployment (manual): `.github/workflows/karnan-kudumbam-deploy-firestore.yml`
- Cloudflare Worker deployment (manual): `.github/workflows/karnan-kudumbam-deploy-worker.yml`
- Daily report manual trigger (manual): `.github/workflows/karnan-kudumbam-daily-email.yml`

## Not yet proven live
- Worker has not been deployed from this branch to the user's Cloudflare account.
- Firestore rules have not been published from this branch.
- Household invitations have not been tested with two real Firebase accounts.
- Resend delivery has not been verified; it requires a verified sender and the secrets above.
- Rewalto key, free-credit balance shape, product results and PIN-specific availability have not been tested against the user's account.
- No signed release has been built or installed on the Moto device.
- CI success does not constitute production acceptance.

## Cost and security guardrails
- No paid Rewalto credit fallback is implemented.
- No paid Cloudflare plan or email provider subscription is assumed.
- Never commit service-account JSON, API keys, scheduler secrets or Android signing keys.
- The app does not place retailer orders; final stock, delivery fees and checkout totals must be confirmed in the retailer app.
