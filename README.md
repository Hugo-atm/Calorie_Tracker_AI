# Calorie Tracker AI — Android Prototype

**Kotlin · Jetpack Compose · OpenRouter API · Android**

Recovered source from an independent, **pre-launch calorie-tracking Android app** developed in 2025 for the Indonesian market. The project explored conversational food logging and a gamified user interface. It was discontinued before release; this repository documents programming work, not a published product or an evaluated nutrition system.

## Features represented in the recovered source

- **Food logging:** a conversational interface for entering food descriptions and receiving estimated calories.
- **OpenRouter API:** an HTTP integration using historical Qwen and other free-model identifiers. The selected recovered Kotlin source attempts an alternate model if the primary model fails. **These 2025 model IDs are not verified as available today.**
- **Local fallback:** a small offline keyword-based food lookup (mostly English-language entries).
- **Android data persistence:** food entries and chat records held in `SharedPreferences`.
- **Gamified UI:** a bunny character, eating/idle animations and health-style progress indicators.

A more comprehensive **Indonesian localized food database was planned**, not completed.

## Repository structure

- [`app/src/main/java/com/example/calorietracker/MainActivity.kt`](app/src/main/java/com/example/calorietracker/MainActivity.kt) — recovered Kotlin/Compose source, including API requests, food logging and UI logic
- [`app/build.gradle.kts`](app/build.gradle.kts), [`gradle/libs.versions.toml`](gradle/libs.versions.toml) — recovered dependency declarations with the Kotlin serialization plugin version aligned
- [`app/src/main/AndroidManifest.xml`](app/src/main/AndroidManifest.xml) — Android application manifest
- [`docs/RECOVERY_NOTES.md`](docs/RECOVERY_NOTES.md) — version selection and limitations

**Archive completeness:** GitHub currently contains source/configuration text only. The historical Android project used GIF animations, Android launcher assets and a Gradle wrapper; these binary assets are retained in a separate local recovery backup and are **not included in this checkout**. The repository, as published, is **not a standalone, verified build**. To reconstruct the app, copy the missing assets into the corresponding Android resource paths from the original project archive.

## How it was intended to work

```text
Jetpack Compose UI
  ├─ Food input / chat
  │    ├─ OpenRouter LLM request (historical model IDs)
  │    └─ Local keyword lookup (fallback)
  ├─ Food entry and calorie tracking
  ├─ SharedPreferences storage
  └─ Bunny animations / gamified progress
```

## Environment and limitations

The recovered project used Android Studio, Kotlin, Jetpack Compose, Coil for GIFs, and Kotlin serialization. No successful clean-machine compilation, emulator test or working live OpenRouter API call has been independently confirmed here. You must provide your own API key, verify current model availability, restore missing assets, and potentially correct historic compiler issues.

**Security note:** The historical app stored its API key using unencrypted `SharedPreferences`, which should **not** be used for production secret storage. The curated Kotlin source removes a debugging line that printed part of an API key. The API key is not included.

**Accuracy note:** Food and calorie outputs are exploratory estimates, not medically validated nutritional advice. No consumer release, user traction or revenue is claimed.

## Business concept and investor pitch

The app was developed alongside a proposed Indonesian-market business model. See the [historical investor-pitch overview](docs/BUSINESS_PITCH_OVERVIEW.md) for the problem statement, proposed features, roadmap, market analysis, and commercialization approach.

The pitch included aspirational features (such as photo-based calorie estimation, expanded Indonesian food data and a fine-tuned model) and projections through 2027. **These were plans and forecasts, not completed features, customers or realized revenue.** The original PowerPoint is retained separately and is not currently included in this GitHub checkout.

## Background

Independent venture, **June–October 2025**. The related investor pitch describes proposals, projections and commercialization plans, not realized outcomes.

*Portfolio archive of recovered Kotlin/Jetpack Compose code and independent product experimentation.*
