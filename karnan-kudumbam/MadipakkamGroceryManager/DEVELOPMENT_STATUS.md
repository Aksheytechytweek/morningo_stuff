# Karnan Kudumbam — implementation and deployment status

Updated 2026-11-17. Source, CI and deployment guidance are maintained on the integration branch. External service deployment and device acceptance remain owner-side tasks.

## Source implementation
- Android Jetpack Compose app with local Room storage, grocery planning, expenses, basket, analysis and on-device PDF sharing.
- Firebase email/password sign-in, email verification and HTTPS API client.
- Cloudflare Worker API using the Fetch runtime and Firebase ID-token verification.
- Household-scoped Firestore expense sync, owner-created email-bound invitation codes, verified-email joining, and owner/member checks.
- Verified-email report preference storage. Actual scheduled email/PDF delivery is not active until the secured report endpoint and mail sender are configured.
- Manual Rewalto catalogue/price observation endpoints with fail-closed free-credit checks and no paid fallback.
- Firestore rules scoped to household owners and invited members.
- Android debug build workflow watches the integration branch and app source changes. Daily email workflow skips safely until its secrets are configured.

## Owner setup still required
1. **Firebase:** configure Email/Password authentication, Firestore and Android package `in.madipakkam.grocery`; place the project-specific `app/google-services.json` in the app folder. Never commit service-account credentials.
2. **Firestore rules:** publish this branch's `firestore.rules` in Firebase Console before testing household features.
3. **Cloudflare Worker:** set the project root to `karnan-kudumbam/MadipakkamGroceryManager/backend`; install with `npm install`, deploy with `npm run deploy`, and configure `FIREBASE_PROJECT_ID`. Add `REWALTO_API_KEY` only as a Worker secret if enabling price checks.
4. **Daily report delivery:** deploy a secured report-job endpoint with persistent subscription storage and a verified mail sender. Only then add GitHub Actions repository secrets `REPORT_JOB_URL` and `DAILY_JOB_SECRET`. The scheduled workflow currently skips instead of failing when either secret is absent. The present Worker deliberately returns 503 for the report job; it must not be treated as a working mail service.
5. **Rewalto:** configure the actual account key as a Worker secret, verify its documented free-credit balance shape and catalogue response, and test with free credits only. The endpoint blocks when free balance cannot be verified.
6. **Acceptance:** run backend tests and Android build; test sign-in, email verification, household invitation with two separate verified accounts, expense sync, PIN-based basket observations and report delivery on real services.
7. **Release:** configure signing securely outside Git, build a signed AAB/APK, then install and test on the Moto Edge 60 Pro.

## Not yet verified / not production-ready
- Latest Worker deployment and Firestore rule publication.
- Actual two-account household test against the live Firebase project.
- Daily PDF email delivery: scheduler workflow exists, but the Worker endpoint, durable job storage and email provider are not active.
- Rewalto account response, actual free-credit parsing and product data quality.
- Retailer stock/pack matching and location-specific availability. Results are observations, not guaranteed checkout quotes; taxes, delivery, handling fees and final payable amounts are not supplied.
- Signed release build and device acceptance.

## Cost and security guardrails
- No paid Cloudflare plan or paid Rewalto usage is assumed.
- Never put API keys, service-account JSON, signing keys or scheduler secrets in Git, APK assets or chat.
- No shopping orders are placed. Users must verify stock, PIN availability and final fees in the retailer app.
