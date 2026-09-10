# Lotto 6/42 Model Tracker — Android V1.0.0

A local-first Android app for **prospectively** generating and auditing Philippine Lotto 6/42 model portfolios. It does not claim to predict a fair lottery; all distinct 6-number combinations remain equally likely. The model is a disciplined selection/coverage framework and is evaluated against an equal-size random-control portfolio.

## V1.0 features

- Ask **number of recommended lines first** (1–100; quick choices 5/10/15/20), with estimated cost at ₱20/line.
- Model V1.0: 40% last-30 z-score + 25% last-20 + 20% last-10 + 15% long-run frequency z-score.
- HOT / NEUTRAL / COLD classifications; OVERDUE is flagged separately and **does not receive a probability bonus**.
- Portfolio optimizer controls overlap and coverage across however many lines were requested.
- Equal-size deterministic **random control portfolio** per draw for prospective model-vs-chance comparison.
- Mark a line **Bought & Locked** with timestamp. Only purchased locked lines can trigger Major Win Alert.
- Save a verified result manually; automatically score every generated/control ticket as 0–6 matches.
- Performance dashboard with match distribution and model-vs-random average matches.
- Local Room/SQLite database.
- `.l642` backup with SHA-256 integrity check; restore creates a local safety snapshot before replacing data.
- Android Storage Access Framework: **Back Up Now** can save to Google Drive/OneDrive/device providers.
- Select a persistent document-provider folder and optionally auto-backup after every completed draw.
- Dedicated `major_win_alert` notification channel with bundled custom sound + vibration for 5/6 and 6/6 Bought & Locked matches.
- Package ID fixed as `com.gsbtechnologies.lotto642modeltracker`; versionCode starts at 1. Future releases must retain the same ID/signing key and use non-destructive Room migrations for in-place updating.

## Seed data

V1.0 contains the latest 31 completed draws from **2026-07-02 through 2026-09-10**, plus the long-run frequency counts through the Sep 10 draw. The Sep 10 result seeded is `07,12,23,27,29,33`, based on the verified LottoMatik screenshot reviewed during development. Earlier recent draws and the 2,677-draw baseline were transcribed from the public Lottolyzer 6/42 history/frequency tables, then the Sep 10 numbers were added to make the baseline 2,678 draws.

## Build

The repository includes a GitHub Actions workflow that installs Gradle 8.13, runs unit tests, builds a debug APK, and uploads it as a testing artifact. **Do not use CI debug APKs as the permanent installed release**: each build environment can use a different debug signing key. For true in-place upgrades, the first distributed APK and every later APK must be signed by the same permanent release key. Configure that stable release key before the first production/private-distribution install.

Local build requires JDK 17 and Android SDK 36:

```bash
gradle testDebugUnitTest
gradle assembleDebug
```

## Important V1.0 limitation

Automatic retrieval of official PCSO results is intentionally **not hard-coded to an unstable scraped endpoint**. Results can be entered and marked verified in the app. A future verified-result provider can implement an official/stable endpoint without changing the model or audit schema. This prevents a delayed/cached third-party page from silently triggering a false Major Win Alert.

## Upgrade policy

Never use `fallbackToDestructiveMigration()` on the production database. Every schema change must ship with a tested migration. Keep the application ID and release signing identity unchanged. Major model changes increment `MODEL_VERSION` and never rewrite historical model runs.
