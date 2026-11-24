# Karnan Kudumbam — Agent Instructions

## Project identity
- Repository: Aksheytechytweek/morningo_stuff
- Active project branch: karnan-kudumbam-complete-integration
- Android project: karnan-kudumbam/MadipakkamGroceryManager
- Application ID: in.madipakkam.grocery
- Current app version: 1.1.0 (versionCode 2)
- Firebase project ID: karnan-kudumbam
- Household defaults: Madipakkam, Chennai 600091; 3 adults; ₹15,000 monthly food/grocery budget.

## Safe Git workflow
- Work only on karnan-kudumbam-complete-integration unless the user explicitly names another branch.
- Never push, merge, reset, rebase, or commit directly to main.
- Check git status, branch and latest commit before changing files.
- Preserve user changes. Do not overwrite unrelated work.
- Use focused commits and run relevant checks before reporting success.

## Architecture
- Android Jetpack Compose app: karnan-kudumbam/MadipakkamGroceryManager/app
- Cloudflare Worker: karnan-kudumbam/MadipakkamGroceryManager/backend/src/worker.js
- Rewalto parsing and retailer eligibility: backend/src/rewalto.js
- Rewalto tests: backend/test/rewalto.test.js
- Android API client: app/src/main/java/in/madipakkam/grocery/BackendApi.kt
- Cloud settings UI: app/src/main/java/in/madipakkam/grocery/CloudScreen.kt
- Screens and basket UI: app/src/main/java/in/madipakkam/grocery/MainActivity.kt
- CI: .github/workflows/karnan-android-build.yml, .github/workflows/karnan-kudumbam-backend.yml, .github/workflows/karnan-kudumbam-deploy-worker.yml

## Product invariants
- Support a 3-adult South Indian home-cooked household in Madipakkam PIN 600091.
- ₹15,000 is the total monthly food/grocery ceiling. Label estimates clearly; never present planning allocations as actual spend.
- Smart Basket is single-store only: never recommend split orders.
- A retailer is complete only if every unchecked line has exact product/pack match, an in-stock observation, timestamp no older than 48 hours, and exact requested PIN match.
- Rewalto data is public-page observation, not a retailer checkout quote. State when stock, PIN availability, delivery, handling, platform fees or final checkout total are not verified.
- Price checks are manual-only. Never run hidden/scheduled price searches.
- Free-Rewalto-credit-only is mandatory. Check balance before search and before every product search. Unknown/insufficient balance means stop; no paid fallback.
- Never put API keys, service-account JSON, Cloudflare tokens, Resend keys, signing keys, GitHub PATs or other secrets in source, committed config, logs or user-visible output.

## Deployment
- Worker name: karnan-kudumbam-api (backend/wrangler.toml).
- Android reads backend_url and postal_code from local karnan_cloud preferences.
- No deployed Worker URL is assumed. Verify an actual deployment and use the URL Cloudflare returns.
- Deploy workflow needs GitHub secret CLOUDFLARE_API_TOKEN and variable CLOUDFLARE_ACCOUNT_ID.
- Worker needs FIREBASE_PROJECT_ID and REWALTO_API_KEY. Scheduled email additionally needs FIREBASE_SERVICE_ACCOUNT_JSON, RESEND_API_KEY, REPORT_FROM_EMAIL and DAILY_JOB_SECRET.
- Firebase Auth/Firestore configuration and real-account checks are required; CI success alone is not proof of production readiness.

## Checks
Backend: from karnan-kudumbam/MadipakkamGroceryManager/backend run npm install, npm test, and npx wrangler deploy --dry-run.
Android: from karnan-kudumbam/MadipakkamGroceryManager run gradle --no-daemon assembleDebug.
CI uses Java 21 and Android SDK 35; module emits Java/Kotlin 17 bytecode. APK path: app/build/outputs/apk/debug/app-debug.apk. Verify a successful CI run and artifact before claiming a build passed.

## Agent behaviour
- Read this file and .codex/PROJECT_CONTEXT.md before work.
- Use the focused subagents in .codex/agents for Android, backend/pricing and QA review.
- Verify current Cloudflare, OpenAI and GitHub docs through configured docs MCP servers.
- Inspect source before editing; add regression tests for pricing parser and eligibility changes.
- Never claim deployment, live price availability, email delivery or device testing without actual evidence.
- Ask before cloud resource changes, deployment, email sending, purchases, paid usage or account permission changes.
