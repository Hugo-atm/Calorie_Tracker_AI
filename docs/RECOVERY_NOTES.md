# Source recovery and verification notes

This repository is based on recovered files from the original Android Studio project archive and three alternate Kotlin text snapshots.

### Selected Kotlin version

The published `MainActivity.kt` is based on `package com.example.calorietracker.txt`, because it contains an OpenRouter API URL and a model-attempt loop that tries historical fallback models. Other recovered variants enabled `STRICT_QWEN_ONLY` (which disables the alternate-model loop), and the original ZIP snapshot included apparent code errors. These versions were not merged indiscriminately.

One debug print that exposed part of a user-supplied API key was removed from the selected published source.

### Build and data limitations

- No successful Android build or live end-to-end test is claimed.
- The Android Gradle Plugin, Kotlin, Compose and serialization dependency declarations reflect historical source and may need updates.
- Original GIF and icon assets, the Gradle wrapper binary, and other local project files are not included in the GitHub text-only source recovery. Restore them from the user's original local backup before building.
- Qwen and other model identifiers were used in 2025 and may be unavailable or changed today.
- The app stores API keys in ordinary `SharedPreferences`; this is not secure enough for a released app.
- The food database is a small, mostly English-language demonstration. Indonesian localization was planned rather than delivered.
- The related pitch deck is a proposal, not proof of product sales, investments or launch.

This code is preserved as an academic and professional portfolio artifact, rather than a production-ready Android application.
