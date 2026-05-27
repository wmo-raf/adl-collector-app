# ADLCollector

Android app for field observers to collect and sync weather/environmental observations to
a multi-tenant Django backend. Observers select a tenant (organization), authenticate via OAuth2,
browse their assigned stations, submit manual observations, and have them synced in the background.

## Tech Stack

| Layer           | Library                             |
|-----------------|-------------------------------------|
| UI              | Jetpack Compose + Material3         |
| DI              | Hilt 2.59 (KSP codegen)             |
| Navigation      | Navigation Compose (sealed `Route`) |
| Local DB        | Room 2.8                            |
| Networking      | Retrofit 3 + OkHttp 5 + Moshi       |
| Auth            | OpenID AppAuth (PKCE/OAuth2)        |
| Tenant config   | Firebase Firestore                  |
| Token storage   | DataStore Preferences               |
| Background sync | WorkManager + `@HiltWorker`         |
| Build           | AGP 9.2, Kotlin 2.3, Java 17        |

All library versions live in `gradle/libs.versions.toml`.

## Key Directories

```
app/src/main/java/com/climtech/adlcollector/
├── app/               # Application wiring: AdlApp, MainActivity, NavGraph, Routes, MainScreen
├── core/
│   ├── auth/          # AuthManager (token refresh), OAuthManager, TenantLocalStore, TenantManager
│   ├── data/
│   │   ├── db/        # Room: AppDatabase, entities (Station, StationDetail, Observation), DAOs
│   │   └── network/   # NetworkModule, AuthInterceptor, ApiGuardInterceptor, TokenAuthenticator
│   ├── di/            # CoreModule (singleton providers)
│   ├── model/         # TenantConfig (URL builder, OAuth endpoints)
│   ├── net/           # NetworkException sealed class
│   ├── ui/            # Shared Compose components (ErrorScreen, LoadingScreen) + Theme
│   └── util/          # Result<T>, Logger, retryNetwork, RetrofitExt (asResult())
└── feature/
    ├── login/         # Tenant selection + OAuth flow
    ├── stations/      # Station list & detail (data/, presentation/, ui/)
    └── observations/  # Submit, list, detail, background upload (+ domain/, sync/)
```

## Build & Test Commands

```bash
# Debug build
./gradlew assembleDebug

# Release build
./gradlew assembleRelease

# Unit tests
./gradlew test

# Instrumented tests (requires connected device/emulator)
./gradlew connectedAndroidTest

# Clean
./gradlew clean
```

Build outputs: `app/build/outputs/apk/`

## Important Cross-cutting Files

- `core/util/Result.kt` — custom `sealed interface Result<T>` (`Ok`/`Err`) used everywhere
- `core/net/NetworkException.kt` — sealed error hierarchy for all network failures
- `core/util/RetrofitExt.kt` — `Response<T>.asResult()` extension
- `core/util/Retry.kt` — `retryNetwork { }` with exponential backoff + jitter
- `core/model/TenantConfig.kt` — builds all tenant-scoped API URLs via `.api()`/`.endpoint()`
- `app/Routes.kt` — sealed `Route` class; use `.build()` for navigation

## Additional Documentation

- [`.claude/docs/architectural_patterns.md`](.claude/docs/architectural_patterns.md) — Offline-first
repository pattern, UiState/StateFlow/HiltViewModel convention, per-tenant Retrofit construction,
 OkHttp interceptor chain, DB key scheme, WorkManager sync, and more.
